package com.bigrocket.service

import android.content.Context
import io.github.immaghzbad.aetherst.shared.model.*

/** Persistent settings for the embedded AetherST 1.6.9 UI/engine. */
object AetherSTConfigStore {
    private const val PREFS = "aetherst_config"
    private const val P = "cfg_"

    fun load(context: Context): AetherConfig {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val default = AetherConfig()
        return AetherConfig(
            presetId = p.getString(P + "presetId", default.presetId) ?: default.presetId,
            protocol = readEnum(p.getString(P + "protocol", null), default.protocol),
            noise = readEnum(p.getString(P + "noise", null), default.noise),
            scanMode = readEnum(p.getString(P + "scan", null), default.scanMode),
            ipMode = readEnum(p.getString(P + "ip", null), default.ipMode),
            echEnabled = p.getBoolean(P + "ech", default.echEnabled),
            httpProxyEnabled = p.getBoolean(P + "httpProxy", default.httpProxyEnabled),
            perfProfile = readEnum(p.getString(P + "perf", null), default.perfProfile),
            h2Mode = p.getBoolean(P + "h2", default.h2Mode),
            h2Fragment = p.getBoolean(P + "fragment", default.h2Fragment),
            fragmentSize = p.getString(P + "fragmentSize", default.fragmentSize) ?: default.fragmentSize,
            fragmentDelay = p.getString(P + "fragmentDelay", default.fragmentDelay) ?: default.fragmentDelay,
            noDataCheck = p.getBoolean(P + "noDataCheck", default.noDataCheck),
            quickReconnect = p.getBoolean(P + "quickReconnect", default.quickReconnect),
            socksHost = "127.0.0.1",
            socksPort = "1819",
            httpPort = p.getString(P + "httpPort", default.httpPort) ?: default.httpPort,
            appLogLevel = readEnum(p.getString(P + "appLogLevel", null), default.appLogLevel),
            coreLogLevel = readEnum(p.getString(P + "coreLogLevel", null), default.coreLogLevel),
            peer = p.getString(P + "peer", default.peer) ?: default.peer,
            wgPeer = p.getString(P + "wgPeer", default.wgPeer) ?: default.wgPeer,
            wiwOuter = p.getString(P + "wiwOuter", default.wiwOuter) ?: default.wiwOuter,
            wiwInner = p.getString(P + "wiwInner", default.wiwInner) ?: default.wiwInner,
            wiwScan = p.getBoolean(P + "wiwScan", default.wiwScan),
            masqueMtu = p.getInt(P + "masqueMtu", default.masqueMtu),
            netstackTcpRx = p.getInt(P + "netstackTcpRx", default.netstackTcpRx),
            netstackTcpTx = p.getInt(P + "netstackTcpTx", default.netstackTcpTx),
            keepaliveEnabled = p.getBoolean(P + "keepaliveEnabled", default.keepaliveEnabled),
            keepalive = p.getInt(P + "keepalive", default.keepalive),
            validateSecs = p.getInt(P + "validateSecs", default.validateSecs),
            reconnectSecs = p.getInt(P + "reconnectSecs", default.reconnectSecs),
            wgEndpointCooldownSecs = p.getInt(P + "wgEndpointCooldownSecs", default.wgEndpointCooldownSecs),
            noProfileRetry = p.getBoolean(P + "noProfileRetry", default.noProfileRetry),
            tlsGroups = p.getString(P + "tlsGroups", default.tlsGroups) ?: default.tlsGroups,
            mtu = p.getInt(P + "mtu", default.mtu),
            connectionMode = ConnectionMode.PROXY_ONLY,
            tunnelEngine = readEnum(p.getString(P + "tunnelEngine", null), default.tunnelEngine),
            excludedPackages = p.getStringSet(P + "excludedPackages", emptySet()) ?: emptySet(),
            blockedPackages = p.getStringSet(P + "blockedPackages", emptySet()) ?: emptySet(),
            tunneledPackages = p.getStringSet(P + "tunneledPackages", emptySet()) ?: emptySet(),
            routingRules = decodeRoutingRules(p.getStringSet(P + "routingRules", emptySet()) ?: emptySet()),
            teamName = p.getString(P + "teamName", default.teamName) ?: default.teamName,
            accessEmail = p.getString(P + "accessEmail", default.accessEmail) ?: default.accessEmail,
            accessId = p.getString(P + "accessId", default.accessId) ?: default.accessId,
            accessSecret = p.getString(P + "accessSecret", default.accessSecret) ?: default.accessSecret,
            accessToken = p.getString(P + "accessToken", default.accessToken) ?: default.accessToken,
            ztStaySignedIn = p.getBoolean(P + "ztStaySignedIn", default.ztStaySignedIn),
            ztTokenExpiry = p.getLong(P + "ztTokenExpiry", default.ztTokenExpiry),
            useGateway = p.getBoolean(P + "useGateway", default.useGateway),
            killSwitch = p.getBoolean(P + "killSwitch", default.killSwitch),
            ipv6Leak = p.getBoolean(P + "ipv6Leak", default.ipv6Leak),
            smartReconnect = p.getBoolean(P + "smartReconnect", default.smartReconnect),
            reconnectRetryLimit = p.getInt(P + "reconnectRetryLimit", default.reconnectRetryLimit),
            strictKillSwitch = p.getBoolean(P + "strictKillSwitch", default.strictKillSwitch),
            dnsEnabled = p.getBoolean(P + "dnsEnabled", default.dnsEnabled),
            dnsList = p.getString(P + "dnsList", default.dnsList) ?: default.dnsList,
            shareHotspot = p.getBoolean(P + "shareHotspot", default.shareHotspot),
            tunnelAllApps = p.getBoolean(P + "tunnelAllApps", default.tunnelAllApps),
            upstreamProxy = "socks5://${EmbeddedAetherRuntime.SOCKS_HOST}:${BondingSocksServer.PORT}",
            upstreamProxyEnabled = true,
            routeSniffing = p.getBoolean(P + "routeSniffing", default.routeSniffing),
            sniffingTimeoutMs = p.getInt(P + "sniffingTimeoutMs", default.sniffingTimeoutMs),
            reprovision = p.getBoolean(P + "reprovision", default.reprovision),
            hevLogLevel = p.getString(P + "hevLogLevel", default.hevLogLevel) ?: default.hevLogLevel,
            hevConnectTimeoutMs = p.getInt(P + "hevConnectTimeoutMs", default.hevConnectTimeoutMs),
            hevReadWriteTimeoutMs = p.getInt(P + "hevReadWriteTimeoutMs", default.hevReadWriteTimeoutMs),
            hevMaxSessionCount = p.getInt(P + "hevMaxSessionCount", default.hevMaxSessionCount),
            hevMapdnsCacheSize = p.getInt(P + "hevMapdnsCacheSize", default.hevMapdnsCacheSize),
            hevUdpMode = p.getString(P + "hevUdpMode", default.hevUdpMode) ?: default.hevUdpMode,
            cloakEnabled = p.getBoolean(P + "cloakEnabled", default.cloakEnabled),
            cloakSniList = p.getString(P + "cloakSniList", default.cloakSniList) ?: default.cloakSniList,
            cloakTtlList = p.getString(P + "cloakTtlList", default.cloakTtlList) ?: default.cloakTtlList,
            cloakJitterMin = p.getInt(P + "cloakJitterMin", default.cloakJitterMin),
            cloakJitterMax = p.getInt(P + "cloakJitterMax", default.cloakJitterMax),
            cloakFragment = p.getBoolean(P + "cloakFragment", default.cloakFragment),
            cloakAdaptive = p.getBoolean(P + "cloakAdaptive", default.cloakAdaptive),
            cloakFallbackPorts = p.getString(P + "cloakFallbackPorts", default.cloakFallbackPorts) ?: default.cloakFallbackPorts,
            cloakLogLevel = p.getString(P + "cloakLogLevel", default.cloakLogLevel) ?: default.cloakLogLevel,
            cloakRandomizeSniCase = p.getBoolean(P + "cloakRandomizeSniCase", default.cloakRandomizeSniCase),
            psiphonEnabled = false,
            psiphonChainOuter = p.getString(P + "psiphonChainOuter", default.psiphonChainOuter) ?: default.psiphonChainOuter,
            psiphonSocksPort = p.getString(P + "psiphonSocksPort", default.psiphonSocksPort) ?: default.psiphonSocksPort,
            psiphonEgressRegion = p.getString(P + "psiphonEgressRegion", default.psiphonEgressRegion) ?: default.psiphonEgressRegion,
            psiphonChainMode = readEnum(p.getString(P + "psiphonChainMode", null), default.psiphonChainMode),
            psiphonMasqueOrder = p.getString(P + "psiphonMasqueOrder", default.psiphonMasqueOrder) ?: default.psiphonMasqueOrder,
            psiphonViaAether = p.getBoolean(P + "psiphonViaAether", default.psiphonViaAether),
            pingUrl = p.getString(P + "pingUrl", default.pingUrl) ?: default.pingUrl,
            connectButtonStyle = p.getString(P + "connectButtonStyle", default.connectButtonStyle) ?: default.connectButtonStyle,
            appLanguage = p.getString(P + "appLanguage", default.appLanguage) ?: default.appLanguage,
        )
    }

    fun save(context: Context, c: AetherConfig) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        e.putString(P + "presetId", c.presetId)
            .putString(P + "protocol", c.protocol.name)
            .putString(P + "noise", c.noise.name)
            .putString(P + "scan", c.scanMode.name)
            .putString(P + "ip", c.ipMode.name)
            .putBoolean(P + "ech", c.echEnabled)
            .putBoolean(P + "httpProxy", c.httpProxyEnabled)
            .putString(P + "perf", c.perfProfile.name)
            .putBoolean(P + "h2", c.h2Mode)
            .putBoolean(P + "fragment", c.h2Fragment)
            .putString(P + "fragmentSize", c.fragmentSize)
            .putString(P + "fragmentDelay", c.fragmentDelay)
            .putBoolean(P + "noDataCheck", c.noDataCheck)
            .putBoolean(P + "quickReconnect", c.quickReconnect)
            .putString(P + "httpPort", c.httpPort)
            .putString(P + "appLogLevel", c.appLogLevel.name)
            .putString(P + "coreLogLevel", c.coreLogLevel.name)
            .putString(P + "peer", c.peer)
            .putString(P + "wgPeer", c.wgPeer)
            .putString(P + "wiwOuter", c.wiwOuter)
            .putString(P + "wiwInner", c.wiwInner)
            .putBoolean(P + "wiwScan", c.wiwScan)
            .putInt(P + "masqueMtu", c.masqueMtu)
            .putInt(P + "netstackTcpRx", c.netstackTcpRx)
            .putInt(P + "netstackTcpTx", c.netstackTcpTx)
            .putBoolean(P + "keepaliveEnabled", c.keepaliveEnabled)
            .putInt(P + "keepalive", c.keepalive)
            .putInt(P + "validateSecs", c.validateSecs)
            .putInt(P + "reconnectSecs", c.reconnectSecs)
            .putInt(P + "wgEndpointCooldownSecs", c.wgEndpointCooldownSecs)
            .putBoolean(P + "noProfileRetry", c.noProfileRetry)
            .putString(P + "tlsGroups", c.tlsGroups)
            .putInt(P + "mtu", c.mtu)
            .putString(P + "tunnelEngine", c.tunnelEngine.name)
            .putStringSet(P + "excludedPackages", c.excludedPackages)
            .putStringSet(P + "blockedPackages", c.blockedPackages)
            .putStringSet(P + "tunneledPackages", c.tunneledPackages)
            .putStringSet(P + "routingRules", encodeRoutingRules(c.routingRules))
            .putString(P + "teamName", c.teamName)
            .putString(P + "accessEmail", c.accessEmail)
            .putString(P + "accessId", c.accessId)
            .putString(P + "accessSecret", c.accessSecret)
            .putString(P + "accessToken", c.accessToken)
            .putBoolean(P + "ztStaySignedIn", c.ztStaySignedIn)
            .putLong(P + "ztTokenExpiry", c.ztTokenExpiry)
            .putBoolean(P + "useGateway", c.useGateway)
            .putBoolean(P + "killSwitch", c.killSwitch)
            .putBoolean(P + "ipv6Leak", c.ipv6Leak)
            .putBoolean(P + "smartReconnect", c.smartReconnect)
            .putInt(P + "reconnectRetryLimit", c.reconnectRetryLimit)
            .putBoolean(P + "strictKillSwitch", c.strictKillSwitch)
            .putBoolean(P + "dnsEnabled", c.dnsEnabled)
            .putString(P + "dnsList", c.dnsList)
            .putBoolean(P + "shareHotspot", c.shareHotspot)
            .putBoolean(P + "tunnelAllApps", c.tunnelAllApps)
            .putBoolean(P + "routeSniffing", c.routeSniffing)
            .putInt(P + "sniffingTimeoutMs", c.sniffingTimeoutMs)
            .putBoolean(P + "reprovision", c.reprovision)
            .putString(P + "hevLogLevel", c.hevLogLevel)
            .putInt(P + "hevConnectTimeoutMs", c.hevConnectTimeoutMs)
            .putInt(P + "hevReadWriteTimeoutMs", c.hevReadWriteTimeoutMs)
            .putInt(P + "hevMaxSessionCount", c.hevMaxSessionCount)
            .putInt(P + "hevMapdnsCacheSize", c.hevMapdnsCacheSize)
            .putString(P + "hevUdpMode", c.hevUdpMode)
            .putBoolean(P + "cloakEnabled", c.cloakEnabled)
            .putString(P + "cloakSniList", c.cloakSniList)
            .putString(P + "cloakTtlList", c.cloakTtlList)
            .putInt(P + "cloakJitterMin", c.cloakJitterMin)
            .putInt(P + "cloakJitterMax", c.cloakJitterMax)
            .putBoolean(P + "cloakFragment", c.cloakFragment)
            .putBoolean(P + "cloakAdaptive", c.cloakAdaptive)
            .putString(P + "cloakFallbackPorts", c.cloakFallbackPorts)
            .putString(P + "cloakLogLevel", c.cloakLogLevel)
            .putBoolean(P + "cloakRandomizeSniCase", c.cloakRandomizeSniCase)
            .putString(P + "psiphonChainOuter", c.psiphonChainOuter)
            .putString(P + "psiphonSocksPort", c.psiphonSocksPort)
            .putString(P + "psiphonEgressRegion", c.psiphonEgressRegion)
            .putString(P + "psiphonChainMode", c.psiphonChainMode.name)
            .putString(P + "psiphonMasqueOrder", c.psiphonMasqueOrder)
            .putBoolean(P + "psiphonViaAether", c.psiphonViaAether)
            .putString(P + "pingUrl", c.pingUrl)
            .putString(P + "connectButtonStyle", c.connectButtonStyle)
            .putString(P + "appLanguage", c.appLanguage)
            .apply()
    }

    private fun encodeRoutingRules(rules: List<RoutingRule>): Set<String> =
        rules.mapNotNull { rule ->
            val pattern = rule.pattern.trim()
            if (pattern.isEmpty()) null else "${rule.mode.name}|$pattern"
        }.toSet()

    private fun decodeRoutingRules(values: Set<String>): List<RoutingRule> =
        values.mapNotNull { value ->
            val separator = value.indexOf('|')
            if (separator <= 0 || separator >= value.lastIndex) return@mapNotNull null
            val mode = runCatching { RoutingMode.valueOf(value.substring(0, separator)) }.getOrNull() ?: return@mapNotNull null
            val pattern = value.substring(separator + 1).trim()
            if (pattern.isEmpty()) null else RoutingRule(pattern, mode)
        }.sortedBy { it.pattern }

    private inline fun <reified T : Enum<T>> readEnum(value: String?, fallback: T): T =
        runCatching { if (value == null) fallback else enumValueOf<T>(value) }.getOrDefault(fallback)
}
