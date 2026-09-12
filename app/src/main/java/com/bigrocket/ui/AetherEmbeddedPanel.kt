package com.bigrocket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.immaghzbad.aetherst.core.SocksGate
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.model.AetherConfig
import io.github.immaghzbad.aetherst.shared.model.AetherIpMode
import io.github.immaghzbad.aetherst.shared.model.AetherLogLevel
import io.github.immaghzbad.aetherst.shared.model.AetherNoise
import io.github.immaghzbad.aetherst.shared.model.AetherPerfProfile
import io.github.immaghzbad.aetherst.shared.model.AetherProtocol
import io.github.immaghzbad.aetherst.shared.model.AetherScanMode
import io.github.immaghzbad.aetherst.shared.model.ConnectionStatus
import io.github.immaghzbad.aetherst.shared.model.ConnectionMode
import io.github.immaghzbad.aetherst.shared.model.RoutingMode
import io.github.immaghzbad.aetherst.shared.model.RoutingRule
import io.github.immaghzbad.aetherst.shared.model.SocksReadiness
import com.bigrocket.BuildConfig
import com.bigrocket.service.EmbeddedAetherRuntime

private enum class AetherPanelTab { DASHBOARD, SETTINGS, LOGS, ABOUT }

@Composable
fun AetherEmbeddedPanel(
    modifier: Modifier = Modifier,
    config: AetherConfig,
    onConfigChange: (AetherConfig) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    var tab by remember { mutableStateOf(AetherPanelTab.DASHBOARD) }
    val status by EmbeddedAetherRuntime.status.collectAsState()
    val readiness by SocksGate.readiness.collectAsState()
    val logs by LogRepository.logs.collectAsState()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("AetherST", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "BigRocket → Path 3 → AetherST",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

            ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp) {
                PanelTab(AetherPanelTab.DASHBOARD, "داشبورد", Icons.Filled.Dashboard, tab) { tab = it }
                PanelTab(AetherPanelTab.SETTINGS, "تنظیمات", Icons.Filled.Settings, tab) { tab = it }
                PanelTab(AetherPanelTab.LOGS, "لاگ", Icons.Filled.ListAlt, tab) { tab = it }
                PanelTab(AetherPanelTab.ABOUT, "درباره", Icons.Filled.Info, tab) { tab = it }
            }

            when (tab) {
                AetherPanelTab.DASHBOARD -> DashboardTab(
                    config = config,
                    status = status,
                    readiness = readiness,
                    enabled = enabled,
                    onEnabledChange = onEnabledChange,
                    onConfigChange = onConfigChange,
                    onOpenSettings = { tab = AetherPanelTab.SETTINGS },
                )
                AetherPanelTab.SETTINGS -> SettingsTab(config, onConfigChange)
                AetherPanelTab.LOGS -> LogsTab(logs)
                AetherPanelTab.ABOUT -> AboutTab()
            }
        }
    }
}

@Composable
private fun PanelTab(
    value: AetherPanelTab,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: AetherPanelTab,
    onSelected: (AetherPanelTab) -> Unit,
) {
    Tab(
        selected = selected == value,
        onClick = { onSelected(value) },
        text = { Text(title) },
        icon = { Icon(icon, contentDescription = title) },
    )
}

@Composable
private fun DashboardTab(
    config: AetherConfig,
    status: ConnectionStatus,
    readiness: SocksReadiness,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onConfigChange: (AetherConfig) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.padding(14.dp)) {
        Text(statusText(status, readiness), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            if (EmbeddedAetherRuntime.isTrafficReady()) "Path 3 تحویل داده شد؛ SOCKS خروجی تأیید شده است"
            else "AetherST هنوز تأیید نشده؛ BigRocket روی مسیر مستقیم باقی می‌ماند",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Selector("پروتکل", config.protocol.displayName, AetherProtocol.entries.toList()) { onConfigChange(config.copy(protocol = it)) }
        Selector("اسکن Endpoint", scanLabel(config.scanMode), AetherScanMode.entries.toList()) { onConfigChange(config.copy(scanMode = it)) }
        Selector("IP", config.ipMode.displayName, AetherIpMode.entries.toList()) { onConfigChange(config.copy(ipMode = it)) }
        Selector("Noise", config.noise.displayName, AetherNoise.entries.toList()) { onConfigChange(config.copy(noise = it)) }

        OutlinedTextField(
            value = config.peer,
            onValueChange = { onConfigChange(config.copy(peer = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Peer (اختیاری، ip:port)") },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Quick reconnect")
            Switch(config.quickReconnect, onCheckedChange = { onConfigChange(config.copy(quickReconnect = it)) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("HTTP/2")
            Switch(config.h2Mode, onCheckedChange = { onConfigChange(config.copy(h2Mode = it)) })
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) { Text("تنظیمات کامل") }
            Button(onClick = { onEnabledChange(!enabled) }, modifier = Modifier.weight(1f)) {
                Text(if (enabled) "قطع AetherST" else "اتصال AetherST")
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { onConfigChange(config.copy(peer = "")) }) { Text("استفاده از اسکن خودکار Endpoint") }
    }
}

@Composable
private fun SettingsTab(config: AetherConfig, onConfigChange: (AetherConfig) -> Unit) {
    var routingOpen by remember { mutableStateOf(false) }
    var splitOpen by remember { mutableStateOf(false) }
    var routePattern by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .height(560.dp)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SectionTitle("Presets")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PresetButton("Turbo", config.presetId == "turbo") { onConfigChange(applyPreset(config, "turbo")) }
            PresetButton("Thorough", config.presetId == "thorough") { onConfigChange(applyPreset(config, "thorough")) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PresetButton("Stealth", config.presetId == "stealth") { onConfigChange(applyPreset(config, "stealth")) }
            PresetButton("Ironclad", config.presetId == "ironclad") { onConfigChange(applyPreset(config, "ironclad")) }
            PresetButton("Custom", config.presetId == "custom") { onConfigChange(config.copy(presetId = "custom")) }
        }

        SectionTitle("Core")
        Selector("Protocol", config.protocol.displayName, AetherProtocol.entries.toList()) { onConfigChange(config.copy(protocol = it)) }
        Selector("Endpoint Scan", scanLabel(config.scanMode), AetherScanMode.entries.toList()) { onConfigChange(config.copy(scanMode = it)) }
        Selector("IP Mode", config.ipMode.displayName, AetherIpMode.entries.toList()) { onConfigChange(config.copy(ipMode = it)) }
        Selector("Noise", config.noise.displayName, AetherNoise.entries.toList()) { onConfigChange(config.copy(noise = it)) }
        Selector("Performance", config.perfProfile.displayName, AetherPerfProfile.entries.toList()) { onConfigChange(config.copy(perfProfile = it)) }
        TextFieldSetting("Peer", config.peer) { onConfigChange(config.copy(peer = it)) }

        SectionTitle("MASQUE / Transport")
        SwitchRow("HTTP/2", config.h2Mode) { onConfigChange(config.copy(h2Mode = it)) }
        SwitchRow("ECH", config.echEnabled) { onConfigChange(config.copy(echEnabled = it)) }
        SwitchRow("HTTP/2 Fragment", config.h2Fragment) { onConfigChange(config.copy(h2Fragment = it)) }
        TextFieldSetting("Fragment Size", config.fragmentSize) { onConfigChange(config.copy(fragmentSize = it)) }
        TextFieldSetting("Fragment Delay", config.fragmentDelay) { onConfigChange(config.copy(fragmentDelay = it)) }
        TextFieldSetting("TLS Groups", config.tlsGroups) { onConfigChange(config.copy(tlsGroups = it)) }
        IntFieldSetting("MASQUE MTU", config.masqueMtu) { onConfigChange(config.copy(masqueMtu = it.coerceAtLeast(0))) }

        SectionTitle("WireGuard / GOOL")
        TextFieldSetting("WireGuard Peer", config.wgPeer) { onConfigChange(config.copy(wgPeer = it)) }
        if (config.protocol == AetherProtocol.GOOL) {
            TextFieldSetting("WIW Outer", config.wiwOuter) { onConfigChange(config.copy(wiwOuter = it)) }
            TextFieldSetting("WIW Inner", config.wiwInner) { onConfigChange(config.copy(wiwInner = it)) }
            SwitchRow("WIW Scan", config.wiwScan) { onConfigChange(config.copy(wiwScan = it)) }
        }
        SwitchRow("Keepalive", config.keepaliveEnabled) { onConfigChange(config.copy(keepaliveEnabled = it)) }
        IntFieldSetting("Keepalive Seconds", config.keepalive) { onConfigChange(config.copy(keepalive = it.coerceIn(0, 180))) }
        IntFieldSetting("Endpoint Cooldown", config.wgEndpointCooldownSecs) { onConfigChange(config.copy(wgEndpointCooldownSecs = it.coerceAtLeast(0))) }
        SwitchRow("No Profile Retry", config.noProfileRetry) { onConfigChange(config.copy(noProfileRetry = it)) }

        SectionTitle("Reconnect / Validation")
        SwitchRow("Quick Reconnect", config.quickReconnect) { onConfigChange(config.copy(quickReconnect = it)) }
        IntFieldSetting("Validate Seconds", config.validateSecs) { onConfigChange(config.copy(validateSecs = it.coerceIn(1, 120))) }
        IntFieldSetting("Reconnect Seconds", config.reconnectSecs) { onConfigChange(config.copy(reconnectSecs = it.coerceIn(1, 120))) }
        SwitchRow("Smart Reconnect", config.smartReconnect) { onConfigChange(config.copy(smartReconnect = it)) }
        IntFieldSetting("Retry Limit", config.reconnectRetryLimit) { onConfigChange(config.copy(reconnectRetryLimit = it.coerceIn(1, 100))) }
        SwitchRow("No Data Check", config.noDataCheck) { onConfigChange(config.copy(noDataCheck = it)) }
        SwitchRow("Re-provision", config.reprovision) { onConfigChange(config.copy(reprovision = it)) }

        SectionTitle("DNS / Routing")
        SwitchRow("Aether DNS", config.dnsEnabled) { onConfigChange(config.copy(dnsEnabled = it)) }
        TextFieldSetting("DNS Servers", config.dnsList) { onConfigChange(config.copy(dnsList = it)) }
        SwitchRow("Route Sniffing", config.routeSniffing) { onConfigChange(config.copy(routeSniffing = it)) }
        IntFieldSetting("Sniff Timeout ms", config.sniffingTimeoutMs) { onConfigChange(config.copy(sniffingTimeoutMs = it.coerceIn(10, 5000))) }
        OutlinedButton(onClick = { routingOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("قوانین Routing Rules") }
        OutlinedButton(onClick = { splitOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Split Tunneling") }

        SectionTitle("Connection / Local Interface")
        Text("Embedded mode: Proxy Only — BigRocket owns the VPN/TUN")
        Text("SOCKS: 127.0.0.1:1819  |  Path 3 Upstream: 127.0.0.1:${com.bigrocket.service.BondingSocksServer.PORT}")
        Selector("App Log Level", config.appLogLevel.displayName, AetherLogLevel.entries.toList()) { onConfigChange(config.copy(appLogLevel = it)) }
        Selector("Core Log Level", config.coreLogLevel.displayName, AetherLogLevel.entries.toList()) { onConfigChange(config.copy(coreLogLevel = it)) }
        IntFieldSetting("MTU", config.mtu) { onConfigChange(config.copy(mtu = it.coerceIn(576, 9000))) }
        SwitchRow("Tunnel All Apps", config.tunnelAllApps) { onConfigChange(config.copy(tunnelAllApps = it)) }

        SectionTitle("Security / Reliability")
        SwitchRow("Kill Switch", config.killSwitch) { onConfigChange(config.copy(killSwitch = it)) }
        SwitchRow("Strict Kill Switch", config.strictKillSwitch) { onConfigChange(config.copy(strictKillSwitch = it)) }
        SwitchRow("IPv6 Leak Protection", config.ipv6Leak) { onConfigChange(config.copy(ipv6Leak = it)) }
        SwitchRow("Share Hotspot", config.shareHotspot) { onConfigChange(config.copy(shareHotspot = it)) }

        SectionTitle("HEV Engine")
        Selector("HEV Log Level", config.hevLogLevel, listOf("off", "error", "warn", "info", "debug")) { onConfigChange(config.copy(hevLogLevel = it)) }
        IntFieldSetting("HEV Connect Timeout ms", config.hevConnectTimeoutMs) { onConfigChange(config.copy(hevConnectTimeoutMs = it.coerceIn(100, 120000))) }
        IntFieldSetting("HEV Read / Write Timeout ms", config.hevReadWriteTimeoutMs) { onConfigChange(config.copy(hevReadWriteTimeoutMs = it.coerceIn(1000, 300000))) }
        IntFieldSetting("HEV Max Sessions", config.hevMaxSessionCount) { onConfigChange(config.copy(hevMaxSessionCount = it.coerceAtLeast(0))) }
        IntFieldSetting("HEV MAPDNS Cache", config.hevMapdnsCacheSize) { onConfigChange(config.copy(hevMapdnsCacheSize = it.coerceAtLeast(0))) }
        Selector("HEV UDP Mode", config.hevUdpMode, listOf("udp", "tcp", "system")) { onConfigChange(config.copy(hevUdpMode = it)) }

        SectionTitle("Diagnostics / System")
        TextFieldSetting("Ping URL", config.pingUrl) { onConfigChange(config.copy(pingUrl = it)) }
        TextFieldSetting("Language", config.appLanguage) { onConfigChange(config.copy(appLanguage = it)) }
        TextFieldSetting("Connect Button Style", config.connectButtonStyle) { onConfigChange(config.copy(connectButtonStyle = it)) }
        IntFieldSetting("TCP RX Buffer", config.netstackTcpRx) { onConfigChange(config.copy(netstackTcpRx = it.coerceAtLeast(0))) }
        IntFieldSetting("TCP TX Buffer", config.netstackTcpTx) { onConfigChange(config.copy(netstackTcpTx = it.coerceAtLeast(0))) }

        if (config.protocol == AetherProtocol.ZERO_TRUST) {
            SectionTitle("Zero Trust")
            TextFieldSetting("Team Name", config.teamName) { onConfigChange(config.copy(teamName = it)) }
            TextFieldSetting("Access Email", config.accessEmail) { onConfigChange(config.copy(accessEmail = it)) }
            TextFieldSetting("Access Client ID", config.accessId) { onConfigChange(config.copy(accessId = it)) }
            SecretFieldSetting("Access Client Secret", config.accessSecret) { onConfigChange(config.copy(accessSecret = it)) }
            SecretFieldSetting("Access Token", config.accessToken) { onConfigChange(config.copy(accessToken = it)) }
            SwitchRow("Stay Signed In", config.ztStaySignedIn) { onConfigChange(config.copy(ztStaySignedIn = it)) }
            SwitchRow("Gateway", config.useGateway) { onConfigChange(config.copy(useGateway = it)) }
            config.zeroTrustError()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        SectionTitle("Logging")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { LogRepository.clear() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.ClearAll, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("پاک کردن لاگ")
            }
            OutlinedButton(onClick = { onConfigChange(config) }, modifier = Modifier.weight(1f)) {
                Text("ذخیره")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (routingOpen) {
            AlertDialog(
                onDismissRequest = { routingOpen = false },
                title = { Text("Routing Rules") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("قوانین BLOCK توسط Aether Core استفاده می‌شوند.")
                        Spacer(Modifier.height(8.dp))
                        TextFieldSetting("Domain / IP", routePattern) { routePattern = it }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            val p = routePattern.trim()
                            if (p.isNotEmpty()) {
                                onConfigChange(config.copy(routingRules = (config.routingRules + RoutingRule(p, RoutingMode.BLOCK)).distinctBy { it.pattern }))
                                routePattern = ""
                            }
                        }) { Text("افزودن BLOCK") }
                        Spacer(Modifier.height(8.dp))
                        config.routingRules.forEach { rule ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${rule.mode}: ${rule.pattern}", modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    onConfigChange(config.copy(routingRules = config.routingRules.filterNot { it == rule }))
                                }) { Text("حذف") }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { routingOpen = false }) { Text("بستن") } },
            )
        }

        if (splitOpen) {
            SplitTunnelingDialog(config, onConfigChange) { splitOpen = false }
        }
    }
}

@Composable
private fun SplitTunnelingDialog(config: AetherConfig, onConfigChange: (AetherConfig) -> Unit, onClose: () -> Unit) {
    var tunneled by remember { mutableStateOf(config.tunneledPackages.joinToString(",")) }
    var excluded by remember { mutableStateOf(config.excludedPackages.joinToString(",")) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Split Tunneling") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("پکیج‌ها را با کاما جدا کن.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(tunneled, { tunneled = it }, Modifier.fillMaxWidth(), label = { Text("Tunneled packages") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(excluded, { excluded = it }, Modifier.fillMaxWidth(), label = { Text("Excluded packages") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfigChange(
                    config.copy(
                        tunneledPackages = tunneled.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                        excludedPackages = excluded.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                    )
                )
                onClose()
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("انصراف") } },
    )
}

@Composable
private fun LogsTab(logs: List<io.github.immaghzbad.aetherst.shared.model.LogEntry>) {
    val text = logs.takeLast(180)
    Column(Modifier.padding(12.dp)) {
        Text("AetherST Logs", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.fillMaxWidth().height(500.dp).verticalScroll(rememberScrollState())
        ) {
            if (text.isEmpty()) {
                Text("لاگی ثبت نشده است.")
            } else {
                text.forEach { entry ->
                    Text(
                        "${entry.timestamp} [${entry.level}] [${entry.tag}] ${entry.message}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun AboutTab() {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AetherST", style = MaterialTheme.typography.headlineSmall)
        Text("نسخه: ${BuildConfig.CORE_VERSION}")
        Text("Aether Core: 1.9.0")
        Text("HEV Tun2Socks: 2.17.1")
        HorizontalDivider()
        Text("Embedded mode: AetherST downstream of BigRocket Path 3")
        Text("Local SOCKS: ${EmbeddedAetherRuntime.SOCKS_HOST}:${EmbeddedAetherRuntime.SOCKS_PORT}")
        Text("Path 3 upstream: ${EmbeddedAetherRuntime.SOCKS_HOST}:${com.bigrocket.service.BondingSocksServer.PORT}")
        Text("Connection mode: ${ConnectionMode.PROXY_ONLY}")
    }
}

@Composable
private fun PresetButton(title: String, active: Boolean, onClick: () -> Unit) {
    if (active) Button(onClick = onClick, modifier = Modifier.weight(1f)) { Text(title) }
    else OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(title) }
}

private fun applyPreset(config: AetherConfig, presetId: String): AetherConfig = when (presetId) {
    "turbo" -> config.copy(
        presetId = "turbo", protocol = AetherProtocol.MASQUE, noise = AetherNoise.GFW,
        scanMode = AetherScanMode.TURBO, echEnabled = false, httpProxyEnabled = false,
        h2Mode = true, h2Fragment = false, noDataCheck = false, tlsGroups = "",
        fragmentSize = "16-32", fragmentDelay = "2-10", mtu = 1320,
    )
    "thorough" -> config.copy(
        presetId = "thorough", protocol = AetherProtocol.MASQUE, noise = AetherNoise.GFW,
        scanMode = AetherScanMode.TURBO, echEnabled = false, httpProxyEnabled = false,
        h2Mode = true, h2Fragment = false, noDataCheck = false, tlsGroups = "",
        fragmentSize = "16-32", fragmentDelay = "2-10", mtu = 1320,
    )
    "stealth" -> config.copy(
        presetId = "stealth", protocol = AetherProtocol.GOOL, noise = AetherNoise.AGGRESSIVE,
        scanMode = AetherScanMode.STEALTH, echEnabled = false, httpProxyEnabled = true,
        h2Mode = true, h2Fragment = false, noDataCheck = false, tlsGroups = "",
        fragmentSize = "16-32", fragmentDelay = "2-10", mtu = 1330,
    )
    "ironclad" -> config.copy(
        presetId = "ironclad", protocol = AetherProtocol.WG, noise = AetherNoise.AGGRESSIVE,
        scanMode = AetherScanMode.STEALTH, echEnabled = false, httpProxyEnabled = true,
        h2Mode = true, h2Fragment = false, noDataCheck = false, tlsGroups = "",
        fragmentSize = "16-32", fragmentDelay = "2-10", mtu = 1330,
    )
    else -> config.copy(presetId = "custom")
}

@Composable
private fun SectionTitle(title: String) {
    Spacer(Modifier.height(6.dp))
    HorizontalDivider()
    Spacer(Modifier.height(4.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange)
    }
}

@Composable
private fun TextFieldSetting(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) })
}

@Composable
private fun SecretFieldSetting(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        onChange,
        Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
    )
}

@Composable
private fun IntFieldSetting(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.filter(Char::isDigit)
            text.toIntOrNull()?.let(onChange)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
    )
}

@Composable
private fun <T> Selector(label: String, value: String, options: List<T>, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = { expanded = true }) { Text(value) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

private fun optionLabel(option: Any): String = when (option) {
    is AetherProtocol -> option.displayName
    is AetherNoise -> option.displayName
    is AetherIpMode -> option.displayName
    is AetherScanMode -> option.rawValue
    is AetherPerfProfile -> option.displayName
    is AetherLogLevel -> option.displayName
    else -> option.toString()
}

private fun scanLabel(mode: AetherScanMode): String = when (mode) {
    AetherScanMode.TURBO -> "Turbo"
    AetherScanMode.BALANCED -> "Balanced"
    AetherScanMode.THOROUGH -> "Thorough"
    AetherScanMode.STEALTH -> "Stealth"
    AetherScanMode.IRONCLAD -> "Ironclad"
}

private fun statusText(status: ConnectionStatus, readiness: SocksReadiness): String = when {
    readiness == SocksReadiness.PROBED_OK -> "متصل و تأییدشده"
    status == ConnectionStatus.ERROR || status == ConnectionStatus.FAILED -> "خطا در اتصال"
    status == ConnectionStatus.RECONNECTING -> "در حال اتصال مجدد"
    status == ConnectionStatus.STARTING -> "در حال راه‌اندازی AetherST"
    status == ConnectionStatus.VALIDATING || status == ConnectionStatus.DATAPLANE_VALIDATED -> "در حال تأیید اتصال"
    status == ConnectionStatus.SOCKS_READY -> "SOCKS آماده؛ در حال تأیید خروجی"
    status == ConnectionStatus.RUNNING -> "در حال اجرا"
    else -> "آماده"
}

