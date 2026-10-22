package com.edgeone.gestures.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edgeone.gestures.core.ActionRegistry
import com.edgeone.gestures.core.AppSettings
import com.edgeone.gestures.core.EdgeSide
import com.edgeone.gestures.core.GestureType
import com.edgeone.gestures.data.LogEntry
import com.edgeone.gestures.data.RuntimeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    settings: AppSettings,
    runtime: RuntimeState,
    log: List<LogEntry>,
    serviceEnabled: Boolean,
    onClearLog: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = "Diagnostics", onBack = onBack) {
        SectionCard("Status") {
            StatusLine(true, "Overlay permission: not required (strips use the accessibility overlay)")
            StatusLine(serviceEnabled, "Accessibility service enabled in Settings: ${if (serviceEnabled) "yes" else "no"}")
            StatusLine(runtime.serviceConnected, "Gesture service running: ${if (runtime.serviceConnected) "yes" else "no"}")
            StatusLine(settings.masterEnabled, "Master switch: ${if (settings.masterEnabled) "on" else "off"}")
            for (side in EdgeSide.entries) {
                StatusLine(runtime.stripShown(side), "${side.title} strip shown: ${if (runtime.stripShown(side)) "yes" else "no"}")
            }
            runtime.lastError?.let { WarningText("Last error: $it") }
            Text(
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard("Selected actions") {
            for (side in EdgeSide.entries) {
                Text(side.title, style = MaterialTheme.typography.titleSmall)
                for (type in GestureType.entries) {
                    val b = settings.edge(side).binding(type)
                    val s = ActionRegistry.support(b.action, Build.VERSION.SDK_INT, serviceEnabled)
                    val title = ActionRegistry.info(b.action.type).title
                    StatusLine(
                        s.supported && (s.warning == null || serviceEnabled),
                        "${type.title}: $title" + if (!b.enabled) " (gesture off)" else "",
                    )
                    s.warning?.let { WarningText(it) }
                }
            }
        }

        SectionCard("Recent gesture results (this session only)") {
            if (log.isEmpty()) {
                Text("Nothing yet. Swipe from an edge strip.", style = MaterialTheme.typography.bodySmall)
            } else {
                val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (e in log) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(fmt.format(Date(e.timeMillis)), style = MaterialTheme.typography.labelSmall)
                            Text(
                                (e.side?.title?.let { "$it: " } ?: "") + e.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (e.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                OutlinedButton(onClick = onClearLog) { Text("Clear log") }
            }
            Text(
                "This log lives in memory only. Nothing about your screen or apps is recorded, stored or sent anywhere.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
