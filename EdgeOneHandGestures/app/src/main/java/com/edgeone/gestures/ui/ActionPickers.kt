package com.edgeone.gestures.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.edgeone.gestures.core.ActionRegistry
import com.edgeone.gestures.core.ActionType
import com.edgeone.gestures.core.GestureAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lists every registered action; unsupported ones are shown disabled with the reason. */
@Composable
fun ActionPickerDialog(
    current: GestureAction,
    serviceEnabled: Boolean,
    onDismiss: () -> Unit,
    onPick: (GestureAction) -> Unit,
    onChooseApp: () -> Unit,
    onChooseShortcut: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose action") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(ActionRegistry.all) { info ->
                    val probe = GestureAction(info.type, packageName = "x", shortcutUri = "x") // ignore "incomplete" for listing
                    val support = ActionRegistry.support(probe, Build.VERSION.SDK_INT, serviceEnabled)
                    val selected = info.type == current.type
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = support.supported) {
                                when (info.type) {
                                    ActionType.LAUNCH_APP -> onChooseApp()
                                    ActionType.LAUNCH_SHORTCUT -> onChooseShortcut()
                                    else -> onPick(GestureAction(info.type))
                                }
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            (if (selected) "✓ " else "") + info.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (support.supported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        )
                        Text(info.description, style = MaterialTheme.typography.bodySmall)
                        if (!support.supported) {
                            Text(support.warning.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else if (support.warning != null) {
                            Text(support.warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class AppEntry(val label: String, val pkg: String)

/**
 * Launchable apps. Visibility comes from the narrow <queries> LAUNCHER intent in the manifest,
 * so no QUERY_ALL_PACKAGES permission is needed.
 */
@Composable
fun AppPickerDialog(onDismiss: () -> Unit, onPick: (label: String, pkg: String) -> Unit) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf("") }
    val apps by produceState(initialValue = emptyList<AppEntry>()) {
        value = withContext(Dispatchers.Default) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }
    val shown = apps.filter { filter.isBlank() || it.label.contains(filter, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose app") },
        text = {
            Column {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it },
                    label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.pkg }) { app ->
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app.label, app.pkg) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
