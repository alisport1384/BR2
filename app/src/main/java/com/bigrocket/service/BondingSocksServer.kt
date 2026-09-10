package com.bigrocket.service

import android.net.Network
import android.net.VpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Local SOCKS5 server that [HevTunnel] (hev-socks5-tunnel, a real userspace TCP/IP stack)
 * forwards every TUN packet to as plain SOCKS5, in place of BigRocket's own hand-rolled
 * IP-packet parser/relay ([TunPacketRouter]/[TcpRelayEngine]/[UdpRelayEngine]).
 *
 * hev already terminates the device's TCP connections properly (retransmission, ordering,
 * congestion control - the actual point of switching to it); this class only needs to do
 * what BigRocket's bonding has always done - pick Wi-Fi vs Cellular per new connection by
 * weight, protect() the socket, and relay real bytes - once per SOCKS5 request rather than
 * once per raw IP flow. That's also why this doesn't reuse TcpRelayEngine/UdpRelayEngine:
 * their code is built around parsing raw IP/TCP/UDP headers and hand-building reply packets
 * (RST, etc.), none of which applies here - hev owns that layer now.
 *
 * Kept as a self-contained alternate path (TunPacketRouter is untouched) specifically so
 * this can be A/B compared and trivially reverted - see BigRocketVpnService.USE_HEV_TUNNEL.
 */
class BondingSocksServer(
    private val vpnService: VpnService,
    private val path3Router: Path3Router
) {

    companion object {
        /** 127.0.0.1-only; picked to avoid AetherUpstream's own 1819 and any other local port. */
        const val PORT = 12347
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val UDP_IDLE_TIMEOUT_MS = 60_000L
        private const val UDP_RECEIVE_TIMEOUT_MS = 1000
    }

    @Volatile private var wifiWeight = 50
    @Volatile private var cellularWeight = 50
    @Volatile private var upstreamMode = UpstreamMode.NONE

    private val relayIdCounter = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    /** Every open relay (TCP or UDP), tagged with which physical Network it's using, so a
     * soft-failure eviction (see [notifySoftFailure]) can close exactly the ones pinned to a
     * path that just went bad - mirrors NetworkSessionTracker's role for the old router. */
    private val activeRelays = ConcurrentHashMap<Int, ActiveRelay>()

    private class ActiveRelay(@Volatile var network: Network?, val close: () -> Unit)

    fun start() {
        if (serverSocket != null) return
        val server = ServerSocket(PORT, 128, InetAddress.getByName("127.0.0.1"))
        serverSocket = server
        acceptJob = scope.launch {
            while (isActive) {
                val client = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                scope.launch { handleClient(client) }
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        activeRelays.values.toList().forEach { runCatching { it.close() } }
        activeRelays.clear()
    }

    fun updateNetworks(wifi: Network?, cellular: Network?) {
        path3Router.updateNetworks(wifi, cellular)
    }

    fun updateWeights(wifiW: Int, cellularW: Int) {
        wifiWeight = wifiW
        cellularWeight = cellularW
        path3Router.updateWeights(wifiW, cellularW)
    }

    fun setUpstreamMode(mode: UpstreamMode) {
        upstreamMode = mode
    }

    /** Mirrors TunPacketRouter.notifySoftFailure: evict every relay pinned to [deadNetwork]
     * immediately instead of leaving it to fail silently - see the extended reasoning on
     * TunPacketRouter.notifySoftFailure itself, which applies identically here. */
    fun notifySoftFailure(deadNetwork: Network) {
        activeRelays.entries.toList().forEach { (id, relay) ->
            if (relay.network == deadNetwork) {
                runCatching { relay.close() }
                activeRelays.remove(id)
            }
        }
    }

    /** Deterministic best-path pick. This class has exactly one instance
     *  (BigRocketVpnService.bondingUpstream), used exclusively as Aether's own upstreamProxy -
     *  every connection/association it ever handles (TCP CONNECT for a GOOL/TCP tunnel, UDP
     *  ASSOCIATE for a WireGuard/MASQUE tunnel) is one long-lived flow for the whole VPN
     *  session, not one of many short-lived ones. A weighted-random sample is only meaningful
     *  when it is drawn many times so the outcome converges to the configured ratio; drawn
     *  exactly once, it just as often lands on the low-weight path outright and pins the
     *  entire session there. This never uses chance:
     *  unequal weights mean the app's own engine (DynamicWeightCalculator) has an authoritative
     *  answer already (a user-set score difference, or a measured quality difference), so the
     *  higher-weight network wins outright; an exact tie is resolved by that same engine's own
     *  identity tie-break rule (recent measured latency - see preferredIdentityPath), not by a
     *  fresh coin flip here. An exact tie only happens when the user has given both paths the
     *  same score (unequal scores always produce unequal weights - see
     *  DynamicWeightCalculator.preferredWeights), so it is rare and, either way, genuinely
     *  arbitrary: Wi-Fi is picked, fixed and not random. (DynamicWeightCalculator's own
     *  identity/IP tie-break is deliberately not reused here - it mutates a separate sticky
     *  identity-owner state meant for a different feature, and calling it here would silently
     *  decide/consume that state as a side effect of an unrelated bonding pin.) */
    private fun pickBestNetwork(): Network? = path3Router.selectNetwork()

    // --- SOCKS5 server handshake ------------------------------------------------------

    private suspend fun handleClient(client: Socket) {
        val relayId = relayIdCounter.getAndIncrement()
        try {
            client.tcpNoDelay = true
            client.soTimeout = CONNECT_TIMEOUT_MS
            val input = client.getInputStream()
            val output = client.getOutputStream()

            val greeting = ByteArray(2)
            if (!readFully(input, greeting)) return closeQuietly(client)
            if (greeting[0].toInt() != 0x05) return closeQuietly(client)
            val methodCount = greeting[1].toInt() and 0xFF
            if (methodCount > 0 && !readFully(input, ByteArray(methodCount))) return closeQuietly(client)
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            val head = ByteArray(4)
            if (!readFully(input, head)) return closeQuietly(client)
            val cmd = head[1].toInt() and 0xFF
            val destination = readAddress(input, head[3].toInt() and 0xFF) ?: return closeQuietly(client)

            client.soTimeout = 0
            when (cmd) {
                0x01 -> handleConnect(relayId, client, input, output, destination.first, destination.second)
                0x03 -> handleUdpAssociate(relayId, client, output)
                else -> {
                    output.write(socksReply(0x07))
                    output.flush()
                    closeQuietly(client)
                }
            }
        } catch (_: Exception) {
            activeRelays.remove(relayId)
            closeQuietly(client)
        }
    }

    private fun readAddress(input: InputStream, atyp: Int): Pair<String, Int>? {
        val host = when (atyp) {
            0x01 -> {
                val addr = ByteArray(4)
                if (!readFully(input, addr)) return null
                InetAddress.getByAddress(addr).hostAddress
            }
            0x03 -> {
                val lenByte = ByteArray(1)
                if (!readFully(input, lenByte)) return null
                val len = lenByte[0].toInt() and 0xFF
                val domain = ByteArray(len)
                if (len > 0 && !readFully(input, domain)) return null
                String(domain, Charsets.US_ASCII)
            }
            0x04 -> {
                val addr = ByteArray(16)
                if (!readFully(input, addr)) return null
                InetAddress.getByAddress(addr).hostAddress
            }
            else -> return null
        }
        val portBytes = ByteArray(2)
        if (!readFully(input, portBytes)) return null
        val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
        return host to port
    }

    // --- TCP CONNECT -------------------------------------------------------------------

    private suspend fun handleConnect(
        relayId: Int,
        client: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        destHost: String,
        destPort: Int,
    ) {
        val mode = upstreamMode
        val remote: Socket
        val network: Network?
        try {
            if (mode == UpstreamMode.AETHER) {
                network = null
                remote = AetherUpstream.openTcp(vpnService, destHost, destPort)
            } else {
                // pickBestNetwork(), not pickNetwork(): this branch only ever carries Aether's
                // own outbound connections (this server instance is Aether's dedicated
                // upstreamProxy - see BigRocketVpnService/EmbeddedAetherRuntime), and a
                // GOOL/TCP tunnel is one long-lived connection for the whole session, same as
                // the UDP-associate case above - a single weighted-random sample would just as
                // often pin the whole session to the low-weight path. See pickBestNetwork's doc.
                val picked = pickBestNetwork() ?: throw IOException("No usable network")
                AppLogger.log(
                    "Path3",
                    "TCP CONNECT pin chosen=${path3Router.describeNetwork(picked)} wifiWeight=$wifiWeight cellularWeight=$cellularWeight dest=$destHost:$destPort",
                )
                network = picked
                // Protecting a socket only prevents VPN recursion; it does NOT select the
                // physical uplink. The selected Network must create/bind the socket, otherwise
                // Android is free to use the default network (typically Wi-Fi), defeating
                // BigRocket's path selection.
                val socket = networkSocket(picked)
                if (!vpnService.protect(socket)) throw IOException("Unable to protect TCP socket from VPN")
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(destHost, destPort), CONNECT_TIMEOUT_MS)
                remote = socket
            }
        } catch (_: Exception) {
            runCatching { clientOut.write(socksReply(0x01)); clientOut.flush() }
            closeQuietly(client)
            return
        }

        activeRelays[relayId] = ActiveRelay(network) {
            runCatching { client.close() }
            runCatching { remote.close() }
        }

        try {
            clientOut.write(socksReply(0x00))
            clientOut.flush()
        } catch (_: Exception) {
            activeRelays.remove(relayId)
            runCatching { remote.close() }
            closeQuietly(client)
            return
        }

        val remoteIn = remote.getInputStream()
        val remoteOut = remote.getOutputStream()

        val upload = scope.launch { pipe(clientIn, remoteOut) }
        val download = scope.launch { pipe(remoteIn, clientOut) }
        upload.join()
        download.join()

        activeRelays.remove(relayId)
        runCatching { remote.close() }
        closeQuietly(client)
    }

    private fun networkSocket(network: Network): Socket =
        network.socketFactory.createSocket()

    private fun pipe(from: InputStream, to: OutputStream) {
        val buffer = ByteArray(16 * 1024)
        try {
            while (true) {
                val n = from.read(buffer)
                if (n < 0) break
                to.write(buffer, 0, n)
                to.flush()
                TrafficStats.recordBytes(n)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { to.flush() }
        }
    }

    // --- UDP ASSOCIATE -------------------------------------------------------------------

    private suspend fun handleUdpAssociate(relayId: Int, client: Socket, clientOut: OutputStream) {
        val localUdp = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val mode = upstreamMode
        val aetherAssociation = if (mode == UpstreamMode.AETHER) {
            try {
                AetherUpstream.openUdp(vpnService)
            } catch (_: Exception) {
                null
            }
        } else null

        val reply = socksReply(0x00, InetAddress.getByName("127.0.0.1"), localUdp.localPort)
        try {
            clientOut.write(reply)
            clientOut.flush()
        } catch (_: Exception) {
            runCatching { localUdp.close() }
            runCatching { aetherAssociation?.close() }
            closeQuietly(client)
            return
        }

        // Core 1.7.0 probes several WireGuard endpoints concurrently. Keep one physical
        // UDP socket per destination for the lifetime of this SOCKS association. This gives
        // every endpoint a stable source port while still allowing the Android Network binding
        // to be explicit. A single shared socket is legal SOCKS5, but it makes concurrent
        // WireGuard endpoint probing share one kernel UDP flow and makes return-path attribution
        // unnecessarily fragile on Android multi-network routing.
        data class PhysicalUdpRelay(
            val network: Network,
            val socket: DatagramSocket,
            val receiver: Job,
        )

        val relays = ConcurrentHashMap<String, PhysicalUdpRelay>()
        val controlWatcher: Job
        var currentClientAddr: InetSocketAddress = InetSocketAddress("127.0.0.1", 0)

        fun createRelay(network: Network, destination: InetSocketAddress): PhysicalUdpRelay? {
            val socket = runCatching {
                val s = DatagramSocket(null)
                if (!vpnService.protect(s)) {
                    s.close()
                    throw IOException("Unable to protect UDP socket from VPN")
                }
                network.bindSocket(s)
                s.bind(InetSocketAddress(0))
                s.connect(destination)
                s.soTimeout = UDP_RECEIVE_TIMEOUT_MS
                s
            }.onFailure { error ->
                AppLogger.logError(
                    "Path3",
                    "UDP relay bind failed for ${path3Router.describeNetwork(network)} dest=${destination.hostString}:${destination.port}",
                    error,
                )
            }.getOrNull() ?: return null

            val receiver = scope.launch {
                val respBuf = ByteArray(64 * 1024)
                try {
                    while (isActive && !socket.isClosed) {
                        val resp = DatagramPacket(respBuf, respBuf.size)
                        try {
                            socket.receive(resp)
                        } catch (_: SocketTimeoutException) {
                            continue
                        } catch (_: Exception) {
                            break
                        }
                        val source = resp.socketAddress as? InetSocketAddress ?: destination
                        val encoded = encodeSocksUdp(
                            source.address.hostAddress,
                            source.port,
                            resp.data.copyOf(resp.length),
                        )
                        // Aether's UDP socket is the source of the SOCKS association packet.
                        // Capture it once in the outer loop and send every response back there.
                        runCatching {
                            localUdp.send(DatagramPacket(encoded, encoded.size, currentClientAddr))
                        }
                        TrafficStats.recordBytes(resp.length)
                    }
                } catch (_: Exception) {
                }
            }
            return PhysicalUdpRelay(network, socket, receiver)
        }

        val activeRelayNetwork = AtomicReference<Network?>(null)

        activeRelays[relayId] = ActiveRelay(null) {
            runCatching { client.close() }
            runCatching { localUdp.close() }
            relays.values.toList().forEach {
                runCatching { it.receiver.cancel() }
                runCatching { it.socket.close() }
            }
            relays.clear()
        }

        controlWatcher = scope.launch {
            try {
                val buf = ByteArray(1)
                while (client.getInputStream().read(buf) >= 0) { /* control channel stays open */ }
            } catch (_: Exception) {
            } finally {
                runCatching { localUdp.close() }
            }
        }

        val buffer = ByteArray(64 * 1024)
        var lastActivity = System.currentTimeMillis()
        localUdp.soTimeout = UDP_RECEIVE_TIMEOUT_MS
        var firstPacket = true
        try {
            while (System.currentTimeMillis() - lastActivity < UDP_IDLE_TIMEOUT_MS) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    localUdp.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }
                lastActivity = System.currentTimeMillis()
                currentClientAddr = packet.socketAddress as? InetSocketAddress ?: currentClientAddr
                val decoded = decodeSocksUdp(packet.data, packet.length) ?: continue

                if (firstPacket) {
                    AppLogger.log(
                        "Path3",
                        "UDP ASSOCIATE active dest=${decoded.host}:${decoded.port} wifiWeight=$wifiWeight cellularWeight=$cellularWeight",
                    )
                    firstPacket = false
                }

                if (aetherAssociation != null) {
                    // Retained for the legacy AETHER-mode branch. BigRocket's embedded
                    // Aether path uses NONE here so that Path3 is the actual upstream boundary.
                    runCatching { aetherAssociation.send(decoded.host, decoded.port, decoded.payload) }
                    val received = runCatching { aetherAssociation.receive(buffer) }.getOrNull()
                    if (received != null) {
                        val encoded = encodeSocksUdp(decoded.host, decoded.port, received.payload)
                        runCatching { localUdp.send(DatagramPacket(encoded, encoded.size, currentClientAddr)) }
                    }
                    continue
                }

                val destination = runCatching {
                    InetSocketAddress(InetAddress.getByName(decoded.host), decoded.port)
                }.getOrNull() ?: continue
                val key = "${destination.address.hostAddress}:${destination.port}"

                val relay = relays[key] ?: run {
                    val network = activeRelayNetwork.get() ?: pickBestNetwork() ?: continue
                    activeRelayNetwork.compareAndSet(null, network)
                    val chosen = activeRelayNetwork.get() ?: network
                    val created = createRelay(chosen, destination) ?: continue
                    relays.putIfAbsent(key, created)?.also { existing ->
                        created.receiver.cancel()
                        created.socket.close()
                    } ?: created.also {
                        activeRelays[relayId]?.network = chosen
                        AppLogger.log(
                            "Path3",
                            "UDP endpoint relay created path=${path3Router.describeNetwork(chosen)} dest=$key",
                        )
                    }
                }

                runCatching {
                    relay.socket.send(DatagramPacket(decoded.payload, decoded.payload.size))
                }.onFailure {
                    relays.remove(key)?.let {
                        it.receiver.cancel()
                        it.socket.close()
                    }
                }
                TrafficStats.recordBytes(decoded.payload.size)
            }
        } finally {
            controlWatcher.cancel()
            activeRelays.remove(relayId)
            runCatching { localUdp.close() }
            runCatching { aetherAssociation?.close() }
            relays.values.toList().forEach {
                runCatching { it.receiver.cancel() }
                runCatching { it.socket.close() }
            }
            relays.clear()
            closeQuietly(client)
        }
    }

    private data class DecodedUdp(val host: String, val port: Int, val payload: ByteArray)

    private fun decodeSocksUdp(data: ByteArray, length: Int): DecodedUdp? {
        if (length < 4) return null
        if (data[0].toInt() != 0 || data[1].toInt() != 0) return null
        val atyp = data[3].toInt() and 0xFF
        var offset = 4
        val host: String
        when (atyp) {
            0x01 -> {
                if (offset + 4 > length) return null
                host = InetAddress.getByAddress(data.copyOfRange(offset, offset + 4)).hostAddress
                offset += 4
            }
            0x03 -> {
                if (offset >= length) return null
                val len = data[offset].toInt() and 0xFF
                offset += 1
                if (offset + len > length) return null
                host = String(data, offset, len, Charsets.US_ASCII)
                offset += len
            }
            0x04 -> {
                if (offset + 16 > length) return null
                host = InetAddress.getByAddress(data.copyOfRange(offset, offset + 16)).hostAddress
                offset += 16
            }
            else -> return null
        }
        if (offset + 2 > length) return null
        val port = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        offset += 2
        return DecodedUdp(host, port, data.copyOfRange(offset, length))
    }

    private fun encodeSocksUdp(host: String, port: Int, payload: ByteArray): ByteArray {
        val addr = InetAddress.getByName(host)
        val addrBytes = addr.address
        val out = java.io.ByteArrayOutputStream()
        out.write(0); out.write(0); out.write(0)
        out.write(if (addrBytes.size == 16) 0x04 else 0x01)
        out.write(addrBytes)
        out.write((port ushr 8) and 0xFF)
        out.write(port and 0xFF)
        out.write(payload)
        return out.toByteArray()
    }

    // --- helpers -------------------------------------------------------------------------

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = try {
                input.read(buffer, read, buffer.size - read)
            } catch (_: Exception) {
                return false
            }
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun socksReply(rep: Int, boundAddr: InetAddress = InetAddress.getByName("0.0.0.0"), boundPort: Int = 0): ByteArray {
        val addrBytes = boundAddr.address
        val out = java.io.ByteArrayOutputStream()
        out.write(0x05); out.write(rep); out.write(0x00)
        out.write(if (addrBytes.size == 16) 0x04 else 0x01)
        out.write(addrBytes)
        out.write((boundPort ushr 8) and 0xFF)
        out.write(boundPort and 0xFF)
        return out.toByteArray()
    }

    private fun closeQuietly(socket: Socket) {
        runCatching { socket.close() }
    }
}
