package com.edgeone.gestures.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.edgeone.gestures.core.ActionRegistry
import com.edgeone.gestures.core.AppSettings
import com.edgeone.gestures.core.EdgeSide
import com.edgeone.gestures.core.EdgeZoneGeometry
import com.edgeone.gestures.core.GestureOutcome
import com.edgeone.gestures.core.GestureTracker
import com.edgeone.gestures.core.RecognizerParams
import com.edgeone.gestures.data.RuntimeState

@Composable
fun DashboardScreen(
    settings: AppSettings,
    runtime: RuntimeState,
    serviceEnabled: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onOpenEdge: (EdgeSide) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    ScreenScaffold(title = "Edge One-Hand Gestures", onBack = null) {
        StatusCard(settings, runtime, serviceEnabled, onOpenSetup)

        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Edge gestures", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Master switch. Off removes both edge strips completely.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = settings.masterEnabled,
                    onCheckedChange = { on -> onUpdate { it.copy(masterEnabled = on) } },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show strips on screen", style = MaterialTheme.typography.bodyMedium)
                    Text("Faint overlay so you can see and calibrate the zones.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = settings.showZones,
                    onCheckedChange = { on -> onUpdate { it.copy(showZones = on) } },
                )
            }
        }

        SectionCard("Edges") {
            ZonePreview(settings, highlight = null)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (side in EdgeSide.entries) {
                    OutlinedButton(onClick = { onOpenEdge(side) }, modifier = Modifier.weight(1f)) {
                        Text(side.title)
                    }
                }
            }
        }

        TestPad(settings)

        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenSetup, modifier = Modifier.weight(1f)) { Text("Setup") }
                OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.weight(1f)) { Text("Diagnostics") }
            }
            TextButton(onClick = onResetDefaults) { Text("Reset all settings to defaults") }
        }
    }
}

@Composable
private fun StatusCard(settings: AppSettings, runtime: RuntimeState, serviceEnabled: Boolean, onOpenSetup: () -> Unit) {
    val anyStrip = runtime.leftStripShown || runtime.rightStripShown
    val (ok, headline, detail) = when {
        !serviceEnabled -> Triple(
            false, "Accessibility service is off",
            "Edge gestures cannot work until you enable \"Edge One-Hand Gestures\" in Android's Accessibility settings.",
        )
        !runtime.serviceConnected -> Triple(
            false, "Enabled, but not running yet",
            "Android lists the service as enabled but has not started it. Wait a few seconds; if this persists, " +
                "switch it off and on again in Accessibility settings, and check the battery notes in Setup.",
        )
        !settings.masterEnabled -> Triple(true, "Paused", "The master switch below is off, so no edge strips are active.")
        !anyStrip -> Triple(
            false, "Running, but no strips active",
            "Both edges are switched off, or every gesture on them is disabled / set to \"No action\".",
        )
        else -> Triple(
            true, "Active",
            listOfNotNull(
                "Left strip".takeIf { runtime.leftStripShown },
                "Right strip".takeIf { runtime.rightStripShown },
            ).joinToString(" and ") + " listening.",
        )
    }
    SectionCard("Status") {
        StatusLine(ok, headline)
        Text(detail, style = MaterialTheme.typography.bodySmall)
        runtime.lastError?.let { WarningText("Last error: $it") }
        if (!serviceEnabled || !runtime.serviceConnected) {
            Button(onClick = onOpenSetup) { Text("Open setup") }
        }
    }
}

/**
 * In-app sandbox that uses the SAME recogniser and thresholds as the real overlay, but only reports
 * what would happen; it never runs the action. Start a swipe inside one of the teal strips.
 */
@Composable
private fun TestPad(settings: AppSettings) {
    var result by remember { mutableStateOf("Start a swipe inside a teal strip below.") }
    val shape = RoundedCornerShape(16.dp)
    val primary = MaterialTheme.colorScheme.primary
    SectionCard("Gesture test area") {
        Text(result, style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .pointerInput(settings) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width
                        val h = size.height
                        val side = EdgeSide.entries.firstOrNull {
                            EdgeZoneGeometry.startsInZone(it, down.position.x, down.position.y, w, h, density, settings.edge(it))
                        }
                        if (side == null) {
                            result = "Ignored: the touch did not start inside an edge strip."
                            do {
                                val e = awaitPointerEvent()
                            } while (e.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        val cfg = settings.edge(side)
                        val tracker = GestureTracker()
                        tracker.onDown(down.position.x, down.position.y, down.uptimeMillis)
                        var outcome: GestureOutcome? = null
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.count { it.pressed } > 1) tracker.onExtraPointer()
                            val c = ev.changes.first()
                            if (c.pressed) {
                                tracker.onMove(c.position.x, c.position.y)
                            } else {
                                outcome = tracker.onUp(
                                    c.position.x, c.position.y, c.uptimeMillis, side,
                                    RecognizerParams.from(cfg, density),
                                )
                                break
                            }
                        }
                        result = when (outcome) {
                            is GestureOutcome.Recognized -> {
                                val b = cfg.binding(outcome.type)
                                val what = if (!b.enabled) "gesture disabled, nothing would happen"
                                else "would run: ${ActionRegistry.info(b.action.type).title}"
                                "${side.title}: ${outcome.type.title} (${outcome.angleDeg.toInt()}°, ${outcome.distancePx.toInt()}px) → $what"
                            }
                            is GestureOutcome.Rejected ->
                                "${side.title}: rejected – ${outcome.reason.text} (${outcome.distancePx.toInt()}px)"
                            null -> "Touch cancelled."
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                for (side in EdgeSide.entries) {
                    val z = EdgeZoneGeometry.zone(side, size.width.toInt(), size.height.toInt(), density, settings.edge(side))
                    val active = settings.masterEnabled && settings.edge(side).enabled
                    drawRect(
                        color = primary.copy(alpha = if (active) 0.55f else 0.12f),
                        topLeft = Offset(z.left.toFloat(), z.top.toFloat()),
                        size = Size(z.width.toFloat(), z.height.toFloat()),
                    )
                }
            }
            Text(
                "Swipe from a strip",
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
