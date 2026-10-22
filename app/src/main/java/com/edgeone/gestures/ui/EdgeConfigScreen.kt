package com.edgeone.gestures.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.edgeone.gestures.core.ActionRegistry
import com.edgeone.gestures.core.ActionType
import com.edgeone.gestures.core.AppSettings
import com.edgeone.gestures.core.EdgeConfig
import com.edgeone.gestures.core.EdgeSide
import com.edgeone.gestures.core.GestureAction
import com.edgeone.gestures.core.GestureBinding
import com.edgeone.gestures.core.GestureType

@Composable
fun EdgeConfigScreen(
    side: EdgeSide,
    settings: AppSettings,
    serviceEnabled: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val cfg = settings.edge(side)
    val context = LocalContext.current
    fun edit(transform: (EdgeConfig) -> EdgeConfig) = onUpdate { it.withEdge(side, transform(it.edge(side))) }
    fun setAction(type: GestureType, action: GestureAction) = edit { c ->
        c.copy(gestures = c.gestures + (type to c.binding(type).copy(action = action)))
    }

    // Which gesture row is currently choosing an action, and which sub-dialog is open.
    var pickingFor by remember { mutableStateOf<GestureType?>(null) }
    var pickingApp by remember { mutableStateOf(false) }

    // Shortcut flow: (1) choose which app's shortcut creator, (2) run it and read the result.
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val target = pickingFor
        val data = r.data
        if (target != null && data != null) {
            val shortcutIntent = shortcutIntentFrom(data)
            if (shortcutIntent != null) {
                val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Shortcut"
                setAction(target, GestureAction(ActionType.LAUNCH_SHORTCUT, shortcutUri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME), label = name))
            } else {
                Toast.makeText(context, "That app did not return a usable shortcut.", Toast.LENGTH_LONG).show()
            }
        }
        pickingFor = null
    }
    val chooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val component: ComponentName? = r.data?.component
        if (component != null) {
            try {
                createLauncher.launch(Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(component))
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open that shortcut creator.", Toast.LENGTH_LONG).show()
                pickingFor = null
            }
        } else {
            pickingFor = null
        }
    }

    ScreenScaffold(title = side.title, onBack = onBack) {
        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Enable ${side.title.lowercase()}", style = MaterialTheme.typography.titleMedium)
                }
                Switch(checked = cfg.enabled, onCheckedChange = { on -> edit { it.copy(enabled = on) } })
            }
            ZonePreview(settings, highlight = side)
        }

        SectionCard("Zone and sensitivity") {
            CommitSlider(
                label = "Edge activation width",
                value = cfg.widthDp.toFloat(),
                range = EdgeConfig.MIN_WIDTH_DP.toFloat()..EdgeConfig.MAX_WIDTH_DP.toFloat(),
                valueText = { "${it.toInt()} dp" },
                onCommit = { v -> edit { it.copy(widthDp = v.toInt()) } },
            )
            CommitRangeSlider(
                label = "Vertical activation range",
                start = cfg.verticalStartPct, end = cfg.verticalEndPct,
                onCommit = { s, e -> edit { it.copy(verticalStartPct = s, verticalEndPct = e) } },
            )
            CommitSlider(
                label = "Gesture sensitivity",
                value = cfg.sensitivity,
                range = 0f..1f,
                valueText = { "${(it * 100).toInt()}%" },
                onCommit = { v -> edit { it.copy(sensitivity = v) } },
            )
            CommitSlider(
                label = "Minimum swipe distance",
                value = cfg.minDistanceDp.toFloat(),
                range = EdgeConfig.MIN_DISTANCE_DP.toFloat()..EdgeConfig.MAX_DISTANCE_DP.toFloat(),
                valueText = { "${it.toInt()} dp" },
                onCommit = { v -> edit { it.copy(minDistanceDp = v.toInt()) } },
            )
            Text(
                "Narrow strips block less of the app underneath; wider strips are easier to hit. " +
                    "Higher sensitivity shortens the required distance and allows more angle error.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard("Feedback") {
            ToggleRow("Haptic feedback", cfg.hapticEnabled) { on -> edit { it.copy(hapticEnabled = on) } }
            ToggleRow("Gesture animation (strip flash)", cfg.animationEnabled) { on -> edit { it.copy(animationEnabled = on) } }
        }

        SectionCard("Gestures") {
            for (type in GestureType.entries) {
                val binding = cfg.binding(type)
                val support = ActionRegistry.support(binding.action, Build.VERSION.SDK_INT, serviceEnabled)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(type.title, style = MaterialTheme.typography.titleSmall)
                            Text(type.hint, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = binding.enabled,
                            onCheckedChange = { on ->
                                edit { c -> c.copy(gestures = c.gestures + (type to GestureBinding(on, c.binding(type).action))) }
                            },
                        )
                    }
                    OutlinedButton(onClick = { pickingFor = type }, modifier = Modifier.fillMaxWidth()) {
                        Text(actionLabel(binding.action))
                    }
                    support.warning?.let { WarningText(it) }
                }
            }
        }
    }

    val target = pickingFor
    if (target != null && !pickingApp) {
        ActionPickerDialog(
            current = cfg.binding(target).action,
            serviceEnabled = serviceEnabled,
            onDismiss = { pickingFor = null },
            onPick = { setAction(target, it); pickingFor = null },
            onChooseApp = { pickingApp = true },
            onChooseShortcut = {
                try {
                    val creators = Intent(Intent.ACTION_CREATE_SHORTCUT)
                    chooserLauncher.launch(
                        Intent(Intent.ACTION_PICK_ACTIVITY)
                            .putExtra(Intent.EXTRA_INTENT, creators)
                            .putExtra(Intent.EXTRA_TITLE, "Choose shortcut source"),
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "No shortcut chooser is available on this device.", Toast.LENGTH_LONG).show()
                    pickingFor = null
                }
            },
        )
    }
    if (target != null && pickingApp) {
        AppPickerDialog(
            onDismiss = { pickingApp = false; pickingFor = null },
            onPick = { label, pkg ->
                setAction(target, GestureAction(ActionType.LAUNCH_APP, packageName = pkg, label = label))
                pickingApp = false
                pickingFor = null
            },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun actionLabel(a: GestureAction): String {
    val base = ActionRegistry.info(a.type).title
    return when {
        a.type == ActionType.LAUNCH_APP && !a.label.isNullOrBlank() -> "$base: ${a.label}"
        a.type == ActionType.LAUNCH_SHORTCUT && !a.label.isNullOrBlank() -> "$base: ${a.label}"
        else -> base
    }
}

@Suppress("DEPRECATION")
private fun shortcutIntentFrom(data: Intent): Intent? =
    if (Build.VERSION.SDK_INT >= 33) data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
    else data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
