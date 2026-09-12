package com.bigrocket.service

import android.content.Context
import io.github.immaghzbad.aetherst.core.AetherProcessRunner
import io.github.immaghzbad.aetherst.shared.model.AetherConfig
import io.github.immaghzbad.aetherst.shared.model.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object EmbeddedAetherRuntime {
    const val SOCKS_HOST = "127.0.0.1"
    const val SOCKS_PORT = 1819

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runner: AetherProcessRunner? = null
    private var monitorJob: Job? = null

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _trafficReady = MutableStateFlow(false)
    val trafficReady: StateFlow<Boolean> = _trafficReady.asStateFlow()
    private val _status = MutableStateFlow(ConnectionStatus.STOPPED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    fun start(context: Context, config: AetherConfig) {
        if (_enabled.value) return
        _enabled.value = true
        _trafficReady.value = false
        val app = context.applicationContext
        val effective = config.copy(
            connectionMode = io.github.immaghzbad.aetherst.shared.model.ConnectionMode.PROXY_ONLY,
            socksHost = SOCKS_HOST,
            socksPort = SOCKS_PORT.toString(),
            upstreamProxyEnabled = true,
            upstreamProxy = "socks5://127.0.0.1:${BondingSocksServer.PORT}",
        )
        val newRunner = AetherProcessRunner(app)
        runner = newRunner
        monitorJob?.cancel()
        monitorJob = scope.launch {
            newRunner.connectionStatus.collectLatest { state ->
                _status.value = state
                _trafficReady.value = state == ConnectionStatus.SOCKS_READY || state == ConnectionStatus.RUNNING
                if (state == ConnectionStatus.ERROR || state == ConnectionStatus.FAILED || state == ConnectionStatus.STOPPED) {
                    _trafficReady.value = false
                    if (state != ConnectionStatus.STOPPED) _enabled.value = false
                }
            }
        }
        newRunner.start(effective, "$SOCKS_HOST:$SOCKS_PORT", inputProvider = { kotlinx.coroutines.delay(1000); "" })
    }

    fun stop(context: Context) {
        _enabled.value = false
        _trafficReady.value = false
        monitorJob?.cancel()
        monitorJob = null
        runner?.stop()
        runner = null
        _status.value = ConnectionStatus.STOPPED
    }

    fun isRunning(): Boolean = _status.value != ConnectionStatus.STOPPED && _status.value != ConnectionStatus.ERROR && _status.value != ConnectionStatus.FAILED
    fun isTrafficReady(): Boolean = _trafficReady.value
}
