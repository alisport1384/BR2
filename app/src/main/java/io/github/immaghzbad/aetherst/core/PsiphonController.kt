package io.github.immaghzbad.aetherst.core

import io.github.immaghzbad.aetherst.shared.model.AetherConfig

/** AetherST Psiphon integration is intentionally disabled in the BigRocket embedding. */
object PsiphonController {
    fun isSupported(config: AetherConfig): Boolean = false
    fun isConnected(): Boolean = false
}
