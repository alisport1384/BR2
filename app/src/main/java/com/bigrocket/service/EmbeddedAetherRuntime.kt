package com.bigrocket.service

import android.content.Context
import io.github.immaghzbad.aetherst.core.AetherProcessRunner
import io.github.immaghzbad.aetherst.core.SocksGate
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.model.AetherConfig
import io.github.immaghzbad.aetherst.shared.model.ConnectionStatus
import io.github.immaghzbad.aetherst.shared.model.SocksReadiness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs the real AetherST 1.6.9 Android core as a downstream of BigRocket Path 3.
 *
 * BigRocket's SOCKS server at 127.0.0.1:12347 is the only physical-network boundary
 * exposed to Aether. Aether itself listens on 127.0.0.1:1819 for downstream traffic.
 */
object EmbeddedAetherRuntime {
    const val SOCKS_HOST = "127.0.0.1"
    const val SOCKS_PORT = 1819

    private const val PATH3_SOCKS_PORT = BondingSocksServer.PORT
    // Same conservative data-plane probe used by the AetherST Android controller.
    // An IP literal avoids introducing a second DNS dependency before the tunnel is proven.
    private const val PROBE_HOST = "1.1.1.1"
    private const val PROBE_PORT = 80
    private const val PROBE_CONNECT_TIMEOUT_MS = 2500
    private const val PROBE_INTERVAL_MS = 700L
    private const val PROBE_MAX_WAIT_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runner: AetherProcessRunner? = null
    private var monitorJob: Job? = null
    private var readinessJob: Job? = null
    private val generation = AtomicLong(0)

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _trafficReady = MutableStateFlow(false)
    val trafficReady: StateFlow<Boolean> = _trafficReady.asStateFlow()

    private val _status = MutableStateFlow(ConnectionStatus.STOPPED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    fun start(context: Context, config: AetherConfig) {
        synchronized(this) {
            if (_enabled.value && runner != null && _status.value !in setOf(ConnectionStatus.ERROR, ConnectionStatus.FAILED)) {
                return
            }

            stopLocked()

            val app = context.applicationContext
            val attempt = generation.incrementAndGet()
            _enabled.value = true
            _trafficReady.value = false
            _status.value = ConnectionStatus.STARTING
            SocksGate.setReady(SocksReadiness.NOT_READY)

            val effective = config.copy(
                connectionMode = io.github.immaghzbad.aetherst.shared.model.ConnectionMode.PROXY_ONLY,
                socksHost = SOCKS_HOST,
                socksPort = SOCKS_PORT.toString(),
                upstreamProxyEnabled = true,
                // Aether's entire external transport is intentionally sent through Path 3.
                upstreamProxy = "socks5://$SOCKS_HOST:$PATH3_SOCKS_PORT",
            )

            LogRepository.currentAppLogLevel = effective.appLogLevel
            LogRepository.currentCoreLogLevel = effective.coreLogLevel
            LogRepository.i(
                "Starting AetherST ${com.bigrocket.BuildConfig.CORE_VERSION} via BigRocket Path 3 " +
                    "(upstream=$SOCKS_HOST:$PATH3_SOCKS_PORT, socks=$SOCKS_HOST:$SOCKS_PORT)",
                "AetherSystem",
            )

            val newRunner = AetherProcessRunner(app)
            runner = newRunner

            monitorJob = scope.launch {
                newRunner.connectionStatus.collectLatest { state ->
                    if (generation.get() != attempt) return@collectLatest
                    _status.value = state
                    if (state == ConnectionStatus.STARTING ||
                        state == ConnectionStatus.VALIDATING ||
                        state == ConnectionStatus.RECONNECTING
                    ) {
                        _trafficReady.value = false
                        if (state != ConnectionStatus.RECONNECTING) {
                            SocksGate.setReady(SocksReadiness.NOT_READY)
                        }
                    }
                    if (state == ConnectionStatus.ERROR || state == ConnectionStatus.FAILED || state == ConnectionStatus.STOPPED) {
                        _trafficReady.value = false
                        SocksGate.setReady(SocksReadiness.NOT_READY)
                        if (state != ConnectionStatus.STOPPED) {
                            _enabled.value = false
                        }
                    }
                }
            }

            readinessJob = scope.launch {
                var waited = 0L
                var firstFailureLogged = false
                while (isActive && generation.get() == attempt && _enabled.value) {
                    val state = _status.value
                    if (state == ConnectionStatus.ERROR || state == ConnectionStatus.FAILED || state == ConnectionStatus.STOPPED) {
                        break
                    }

                    if (probeAetherTunnel()) {
                        SocksGate.setReady(SocksReadiness.PROBED_OK)
                        _trafficReady.value = true
                        _status.value = ConnectionStatus.RUNNING
                        LogRepository.i("AetherST SOCKS + outbound probe succeeded; Path 3 handoff enabled", "AetherSystem")
                        break
                    }

                    if (!firstFailureLogged && waited >= 4_000L) {
                        firstFailureLogged = true
                        LogRepository.w("Waiting for AetherST SOCKS/data-plane readiness...", "AetherSystem")
                    }
                    delay(PROBE_INTERVAL_MS)
                    waited += PROBE_INTERVAL_MS
                    if (waited >= PROBE_MAX_WAIT_MS) {
                        LogRepository.e("AetherST did not establish a verified outbound SOCKS path within ${PROBE_MAX_WAIT_MS / 1000}s", "AetherSystem")
                        break
                    }
                }
            }

            newRunner.start(
                effective,
                "$SOCKS_HOST:$SOCKS_PORT",
                onCodeRequired = {
                    LogRepository.w("AetherST requested interactive authentication input", "AetherSystem")
                },
                inputProvider = { delay(Long.MAX_VALUE); "" },
            )
        }
    }

    fun stop(context: Context) {
        synchronized(this) {
            stopLocked()
        }
    }

    private fun stopLocked() {
        generation.incrementAndGet()
        _enabled.value = false
        _trafficReady.value = false
        SocksGate.setReady(SocksReadiness.NOT_READY)
        readinessJob?.cancel()
        readinessJob = null
        monitorJob?.cancel()
        monitorJob = null
        runner?.stop()
        runner = null
        _status.value = ConnectionStatus.STOPPED
    }

    /**
     * A plain listener check is not enough. The probe performs a real SOCKS5 CONNECT
     * through Aether to a stable HTTPS endpoint, proving that the downstream core is
     * not merely listening but has a usable data plane.
     */
    private fun probeAetherTunnel(): Boolean {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(SOCKS_HOST, SOCKS_PORT), PROBE_CONNECT_TIMEOUT_MS)
            socket.soTimeout = PROBE_CONNECT_TIMEOUT_MS
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            output.write(byteArrayOf(0x05, 0x01, 0x00))
            output.flush()
            val greeting = ByteArray(2)
            readFully(input, greeting)
            if (greeting[0].toInt() != 5 || greeting[1].toInt() != 0) return false

            val hostBytes = PROBE_HOST.toByteArray(Charsets.US_ASCII)
            val request = ByteArray(7 + hostBytes.size)
            request[0] = 0x05
            request[1] = 0x01
            request[2] = 0x00
            request[3] = 0x03
            request[4] = hostBytes.size.toByte()
            hostBytes.copyInto(request, 5)
            request[5 + hostBytes.size] = ((PROBE_PORT ushr 8) and 0xFF).toByte()
            request[6 + hostBytes.size] = (PROBE_PORT and 0xFF).toByte()
            output.write(request)
            output.flush()

            val header = ByteArray(4)
            readFully(input, header)
            if (header[0].toInt() != 5 || (header[1].toInt() and 0xFF) != 0) return false
            skipSocksAddress(input, header[3].toInt() and 0xFF)
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun readFully(input: BufferedInputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw IOException("Unexpected EOF")
            offset += count
        }
    }

    private fun skipSocksAddress(input: BufferedInputStream, atyp: Int) {
        val count = when (atyp) {
            0x01 -> 4
            0x03 -> input.read().also { if (it < 0) throw IOException("EOF") }
            0x04 -> 16
            else -> throw IOException("Invalid SOCKS5 reply address type")
        }
        if (atyp == 0x03) {
            repeat(count) { if (input.read() < 0) throw IOException("EOF") }
        } else {
            repeat(count) { if (input.read() < 0) throw IOException("EOF") }
        }
        repeat(2) { if (input.read() < 0) throw IOException("EOF") }
    }

    fun isRunning(): Boolean = _enabled.value && _status.value !in setOf(
        ConnectionStatus.STOPPED,
        ConnectionStatus.ERROR,
        ConnectionStatus.FAILED,
    )

    fun isTrafficReady(): Boolean = _trafficReady.value
}
