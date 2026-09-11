package com.bigrocket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.immaghzbad.aetherst.shared.model.AetherConfig
import io.github.immaghzbad.aetherst.shared.model.AetherIpMode
import io.github.immaghzbad.aetherst.shared.model.AetherNoise
import io.github.immaghzbad.aetherst.shared.model.AetherProtocol
import io.github.immaghzbad.aetherst.shared.model.AetherScanMode
import io.github.immaghzbad.aetherst.shared.model.ConnectionStatus
import com.bigrocket.service.EmbeddedAetherRuntime

@Composable
fun AetherEmbeddedPanel(
    modifier: Modifier = Modifier,
    config: AetherConfig,
    onConfigChange: (AetherConfig) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val status = EmbeddedAetherRuntime.status.collectAsState().value
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("AetherST", style = MaterialTheme.typography.titleLarge)
                    Text(statusText(status), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(Modifier.height(12.dp))
            Selector("Protocol", config.protocol.displayName, AetherProtocol.entries.toList()) { onConfigChange(config.copy(protocol = it)) }
            Selector("Scan", config.scanMode.name, AetherScanMode.entries.toList()) { onConfigChange(config.copy(scanMode = it)) }
            Selector("IP", config.ipMode.displayName, AetherIpMode.entries.toList()) { onConfigChange(config.copy(ipMode = it)) }
            Selector("Noise", config.noise.displayName, AetherNoise.entries.toList()) { onConfigChange(config.copy(noise = it)) }
            OutlinedTextField(
                value = config.peer,
                onValueChange = { onConfigChange(config.copy(peer = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Peer (ip:port)") },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Quick reconnect")
                Switch(config.quickReconnect, onCheckedChange = { onConfigChange(config.copy(quickReconnect = it)) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("HTTP/2")
                Switch(config.h2Mode, onCheckedChange = { onConfigChange(config.copy(h2Mode = it)) })
            }
            TextButton(onClick = { onConfigChange(config.copy(peer = "")) }) { Text("Auto endpoint") }
        }
    }
}

@Composable
private fun <T> Selector(label: String, value: String, options: List<T>, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = { expanded = true }) { Text(value) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toString()) },
                    onClick = { expanded = false; onSelected(option) },
                )
            }
        }
    }
}

private fun statusText(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.STOPPED -> "آماده"
    ConnectionStatus.STARTING -> "در حال راه‌اندازی موتور"
    ConnectionStatus.VALIDATING, ConnectionStatus.DATAPLANE_VALIDATED -> "در حال بررسی اتصال"
    ConnectionStatus.SOCKS_READY, ConnectionStatus.TUN_ACTIVE, ConnectionStatus.RUNNING -> "متصل"
    ConnectionStatus.RECONNECTING -> "در حال اتصال مجدد"
    ConnectionStatus.STOPPING -> "در حال قطع اتصال"
    ConnectionStatus.ERROR, ConnectionStatus.FAILED -> "خطا"
}
