package com.bigrocket.service

import android.net.VpnService
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object AetherSTUpstream {
    private const val HOST = EmbeddedAetherRuntime.SOCKS_HOST
    private const val PORT = EmbeddedAetherRuntime.SOCKS_PORT
    private const val TIMEOUT_MS = 5000

    fun openTcp(vpnService: VpnService, destinationHost: String, destinationPort: Int): Socket {
        val socket = Socket()
        try {
            vpnService.protect(socket)
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.connect(InetSocketAddress(HOST, PORT), TIMEOUT_MS)
            socket.soTimeout = TIMEOUT_MS
            val out = socket.getOutputStream()
            val input = DataInputStream(socket.getInputStream())
            greeting(out, input)
            val hostBytes = destinationHost.toByteArray(Charsets.UTF_8)
            if (hostBytes.size > 255) throw IOException("Destination hostname is too long")
            out.write(ByteArrayOutputStream().apply {
                write(byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()))
                write(hostBytes)
                write((destinationPort ushr 8) and 0xFF)
                write(destinationPort and 0xFF)
            }.toByteArray())
            out.flush()
            val head = ByteArray(4)
            input.readFully(head)
            if (head[1].toInt() != 0) throw IOException("AetherST SOCKS5 CONNECT rejected: ${head[1].toInt() and 0xFF}")
            skipAddress(input, head[3].toInt() and 0xFF)
            socket.soTimeout = 0
            return socket
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    fun openUdp(vpnService: VpnService): UdpAssociation {
        val control = Socket()
        val udp = DatagramSocket()
        try {
            vpnService.protect(control)
            vpnService.protect(udp)
            control.connect(InetSocketAddress(HOST, PORT), TIMEOUT_MS)
            control.soTimeout = TIMEOUT_MS
            val out = control.getOutputStream()
            val input = DataInputStream(control.getInputStream())
            greeting(out, input)
            out.write(byteArrayOf(0x05, 0x03, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            out.flush()
            val head = ByteArray(4)
            input.readFully(head)
            if (head[1].toInt() != 0) throw IOException("AetherST SOCKS5 UDP ASSOCIATE rejected")
            val host = when (head[3].toInt() and 0xFF) {
                0x01 -> InetAddress.getByAddress(input.readBytesExact(4)).hostAddress
                0x03 -> String(input.readBytesExact(input.readUnsignedByte()), Charsets.US_ASCII)
                0x04 -> InetAddress.getByAddress(input.readBytesExact(16)).hostAddress
                else -> throw IOException("Invalid SOCKS5 UDP address type")
            }
            val port = input.readUnsignedShort()
            control.soTimeout = 0
            udp.soTimeout = 1000
            return UdpAssociation(control, udp, InetSocketAddress(host, port))
        } catch (e: Exception) {
            runCatching { control.close() }; runCatching { udp.close() }; throw e
        }
    }

    class UdpAssociation internal constructor(private val control: Socket, private val socket: DatagramSocket, private val relay: InetSocketAddress) : AutoCloseable {
        fun send(destinationHost: String, destinationPort: Int, payload: ByteArray) {
            val address = InetAddress.getByName(destinationHost)
            val packet = ByteArrayOutputStream().apply {
                write(byteArrayOf(0,0,0, if (address.address.size == 16) 4 else 1))
                write(address.address)
                write((destinationPort ushr 8) and 0xFF); write(destinationPort and 0xFF); write(payload)
            }.toByteArray()
            socket.send(DatagramPacket(packet, packet.size, relay))
        }
        fun receive(buffer: ByteArray): ReceivedUdp? = try {
            val packet = DatagramPacket(buffer, buffer.size); socket.receive(packet); parseReply(packet.data, packet.length)
        } catch (_: java.net.SocketTimeoutException) { null }
        override fun close() { runCatching { socket.close() }; runCatching { control.close() } }
    }
    data class ReceivedUdp(val payload: ByteArray)

    private fun greeting(out: java.io.OutputStream, input: DataInputStream) {
        out.write(byteArrayOf(0x05, 0x01, 0x00)); out.flush()
        val reply = ByteArray(2); input.readFully(reply)
        if (reply[0].toInt() != 5 || reply[1].toInt() != 0) throw IOException("AetherST SOCKS5 authentication negotiation failed")
    }
    private fun skipAddress(input: DataInputStream, atyp: Int) {
        when (atyp) { 1 -> input.skipBytes(4); 3 -> input.skipBytes(input.readUnsignedByte()); 4 -> input.skipBytes(16); else -> throw IOException("Invalid SOCKS5 address type") }
        input.readUnsignedShort()
    }
    private fun parseReply(data: ByteArray, length: Int): ReceivedUdp? {
        if (length < 10 || data[0].toInt() != 0 || data[1].toInt() != 0 || data[2].toInt() != 0) return null
        var i = 3
        val atyp = data[i++].toInt() and 0xFF
        i += when (atyp) { 1 -> 4; 3 -> if (i < length) 1 + (data[i].toInt() and 0xFF) else return null; 4 -> 16; else -> return null }
        if (i + 2 > length) return null
        i += 2
        return if (i <= length) ReceivedUdp(data.copyOfRange(i, length)) else null
    }
    private fun DataInputStream.readBytesExact(n: Int): ByteArray { val b = ByteArray(n); readFully(b); return b }
}
