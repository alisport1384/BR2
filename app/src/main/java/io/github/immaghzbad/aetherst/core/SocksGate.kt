package io.github.immaghzbad.aetherst.core

import io.github.immaghzbad.aetherst.shared.model.SocksReadiness
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SocksGate {
    private val _readiness = MutableStateFlow(SocksReadiness.NOT_READY)
    val readiness: StateFlow<SocksReadiness> = _readiness.asStateFlow()
    fun setReady(value: SocksReadiness) { _readiness.value = value }
}
