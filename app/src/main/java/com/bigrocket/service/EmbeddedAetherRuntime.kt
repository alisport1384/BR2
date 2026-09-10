package com.bigrocket.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.github.immaghzbad.aetherst.core.AetherController
import io.github.immaghzbad.aetherst.core.AetherProcess
import io.github.immaghzbad.aetherst.core.EngineMeta
import io.github.immaghzbad.aetherst.core.PortProbe
import io.github.immaghzbad.aetherst.core.NetProbe
import io.github.immaghzbad.aetherst.core.TunnelConfig
import io.github.immaghzbad.aetherst.data.ProfileStore
import io.github.immaghzbad.aetherst.model.ConnectionProfile
import io.github.immaghzbad.aetherst.model.ConnectionState

/** Runs Aether in embedded proxy mode so BigRocket remains the only Android VPN. */
object EmbeddedAetherRuntime {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var job: Job? = null
    private var process: AetherProcess? = null

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _trafficReady = MutableStateFlow(false)
    val trafficReady: StateFlow<Boolean> = _trafficReady.asStateFlow()

    fun start(context: Context, profile: ConnectionProfile) {
        if (job?.isActive == true) return
        _enabled.value = true
        _trafficReady.value = false
        val app = context.applicationContext
        job = scope.launch {
            runCatching {
                AetherController.setState(ConnectionState.Launching)
                EngineMeta.reset()
                // Aether must consume BigRocket's already-bonded transport. Its own
                // physical dials are therefore chained through the local SOCKS5
                // bonding boundary instead of going directly to Wi-Fi/Cellular.
                val embeddedProfile = profile.copy(
                    proxyMode = true,
                    upstreamProxy = "socks5://127.0.0.1:${BondingSocksServer.PORT}"
                )
                ProfileStore(app).save(embeddedProfile)
                val engine = AetherProcess(app.applicationInfo.nativeLibraryDir, app.filesDir)
                process = engine
                engine.start(embeddedProfile)
                AetherController.setState(ConnectionState.Connecting)
                // GOOL establishes two sequential WireGuard layers before the
                // native SOCKS5 listener is created. Use the protocol's full
                // startup budget here instead of failing while the inner tunnel
                // is still legitimately being established.
                val startupTimeoutMs = embeddedProfile.connectTimeoutMs().coerceAtLeast(10_000L)
                val open = PortProbe.awaitOpen(
                    TunnelConfig.SOCKS_HOST,
                    TunnelConfig.SOCKS_PORT,
                    startupTimeoutMs,
                    isEngineAlive = { engine.isAlive() },
                )
                if (!open) error("Aether SOCKS5 listener did not become ready")
                AetherController.setState(ConnectionState.Verifying)
                AetherController.setIpLoading(true)
                val ip = NetProbe.fetchIpInfoViaSocks(
                    TunnelConfig.SOCKS_HOST,
                    TunnelConfig.SOCKS_PORT,
                )
                AetherController.setIpInfo(ip?.let { io.github.immaghzbad.aetherst.core.IpEndpoint(it.ip, it.countryCode, true) })
                AetherController.setIpLoading(false)
                AetherController.setState(ConnectionState.Connected("${TunnelConfig.SOCKS_HOST}:${TunnelConfig.SOCKS_PORT}"))
                _trafficReady.value = true
            }.onFailure {
                AetherController.setState(ConnectionState.Error(it.message ?: "Aether connection failed"))
                _enabled.value = false
                _trafficReady.value = false
                stopInternal()
            }
        }
    }

    fun stop(context: Context) {
        _enabled.value = false
        _trafficReady.value = false
        job?.cancel()
        job = null
        stopInternal()
        AetherController.setState(ConnectionState.Idle)
        EngineMeta.reset()
    }

    private fun stopInternal() {
        runCatching { process?.stop() }
        process = null
    }

    fun isRunning(): Boolean = process?.isAlive() == true

    fun isTrafficReady(): Boolean = _trafficReady.value
}
