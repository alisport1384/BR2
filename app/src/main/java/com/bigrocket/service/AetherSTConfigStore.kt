package com.bigrocket.service

import android.content.Context
import io.github.immaghzbad.aetherst.shared.model.*

object AetherSTConfigStore {
    private const val PREFS = "aetherst_config"
    private const val P = "cfg_"

    fun load(context: Context): AetherConfig {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AetherConfig(
            protocol = readEnum(p.getString(P+"protocol", null), AetherProtocol.MASQUE),
            noise = readEnum(p.getString(P+"noise", null), AetherNoise.GFW),
            scanMode = readEnum(p.getString(P+"scan", null), AetherScanMode.TURBO),
            ipMode = readEnum(p.getString(P+"ip", null), AetherIpMode.AUTO),
            peer = p.getString(P+"peer", "") ?: "",
            wgPeer = p.getString(P+"wgPeer", "") ?: "",
            wiwOuter = p.getString(P+"wiwOuter", "") ?: "",
            wiwInner = p.getString(P+"wiwInner", "") ?: "",
            wiwScan = p.getBoolean(P+"wiwScan", true),
            quickReconnect = p.getBoolean(P+"quickReconnect", true),
            h2Mode = p.getBoolean(P+"h2", true),
            h2Fragment = p.getBoolean(P+"fragment", false),
            fragmentSize = p.getString(P+"fragmentSize", "16-32") ?: "16-32",
            fragmentDelay = p.getString(P+"fragmentDelay", "2-10") ?: "2-10",
            echEnabled = p.getBoolean(P+"ech", false),
            keepaliveEnabled = p.getBoolean(P+"keepaliveEnabled", true),
            keepalive = p.getInt(P+"keepalive", 5),
            validateSecs = p.getInt(P+"validateSecs", 10),
            reconnectSecs = p.getInt(P+"reconnectSecs", 2),
            noDataCheck = p.getBoolean(P+"noDataCheck", false),
            tlsGroups = p.getString(P+"tlsGroups", "") ?: "",
            dnsEnabled = p.getBoolean(P+"dnsEnabled", false),
            dnsList = p.getString(P+"dnsList", "1.1.1.1,2606:4700:4700::1111") ?: "1.1.1.1,2606:4700:4700::1111",
            coreLogLevel = readEnum(p.getString(P+"coreLogLevel", null), AetherLogLevel.INFO),
        )
    }

    fun save(context: Context, c: AetherConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(P+"protocol", c.protocol.name)
            .putString(P+"noise", c.noise.name)
            .putString(P+"scan", c.scanMode.name)
            .putString(P+"ip", c.ipMode.name)
            .putString(P+"peer", c.peer)
            .putString(P+"wgPeer", c.wgPeer)
            .putString(P+"wiwOuter", c.wiwOuter)
            .putString(P+"wiwInner", c.wiwInner)
            .putBoolean(P+"wiwScan", c.wiwScan)
            .putBoolean(P+"quickReconnect", c.quickReconnect)
            .putBoolean(P+"h2", c.h2Mode)
            .putBoolean(P+"fragment", c.h2Fragment)
            .putString(P+"fragmentSize", c.fragmentSize)
            .putString(P+"fragmentDelay", c.fragmentDelay)
            .putBoolean(P+"ech", c.echEnabled)
            .putBoolean(P+"keepaliveEnabled", c.keepaliveEnabled)
            .putInt(P+"keepalive", c.keepalive)
            .putInt(P+"validateSecs", c.validateSecs)
            .putInt(P+"reconnectSecs", c.reconnectSecs)
            .putBoolean(P+"noDataCheck", c.noDataCheck)
            .putString(P+"tlsGroups", c.tlsGroups)
            .putBoolean(P+"dnsEnabled", c.dnsEnabled)
            .putString(P+"dnsList", c.dnsList)
            .putString(P+"coreLogLevel", c.coreLogLevel.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> readEnum(value: String?, fallback: T): T =
        runCatching { if (value == null) fallback else enumValueOf<T>(value) }.getOrDefault(fallback)
}
