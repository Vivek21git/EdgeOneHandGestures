package com.edgeone.gestures.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.edgeone.gestures.core.AppSettings
import com.edgeone.gestures.core.EdgeConfig
import com.edgeone.gestures.core.EdgeSide
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = if (ok) "OK" else "Problem",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun WarningText(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            Icons.Filled.Warning, contentDescription = "Warning",
            tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp).padding(top = 2.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

/** Slider that keeps a local value while dragging and commits to settings only on release. */
@Composable
fun CommitSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    onCommit: (Float) -> Unit,
    steps: Int = 0,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText(local), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
            steps = steps,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitRangeSlider(
    label: String,
    start: Int,
    end: Int,
    onCommit: (Int, Int) -> Unit,
) {
    var local by remember(start, end) { mutableStateOfRange(start.toFloat(), end.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${local.start.roundToInt()}% – ${local.endInclusive.roundToInt()}% of screen height",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            )
        }
        RangeSlider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local.start.roundToInt(), local.endInclusive.roundToInt()) },
            valueRange = 0f..100f,
        )
    }
}

private fun mutableStateOfRange(a: Float, b: Float) =
    androidx.compose.runtime.mutableStateOf(a..b)

/**
 * Schematic phone showing where the strips sit. The edge being edited is drawn strongly; the other
 * edge faintly. Proportions follow the real screen shape and the real dp widths.
 */
@Composable
fun ZonePreview(settings: AppSettings, highlight: EdgeSide?, modifier: Modifier = Modifier) {
    val conf = LocalConfiguration.current
    val screenWdp = minOf(conf.screenWidthDp, conf.screenHeightDp).toFloat()
    val screenHdp = maxOf(conf.screenWidthDp, conf.screenHeightDp).toFloat()
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.height(220.dp).fillMaxWidth()) {
        val h = size.height
        val w = h * (screenWdp / screenHdp)
        val left = (size.width - w) / 2f
        drawRoundRect(
            color = outline, topLeft = Offset(left, 0f), size = Size(w, h),
            cornerRadius = CornerRadius(24f, 24f), style = Stroke(width = 3f),
        )
        val pxPerDp = w / screenWdp
        for (side in EdgeSide.entries) {
            val c: EdgeConfig = settings.edge(side).sanitized()
            val active = settings.masterEnabled && c.enabled
            val stripW = c.widthDp * pxPerDp
            val top = h * c.verticalStartPct / 100f
            val bottom = h * c.verticalEndPct / 100f
            val x = if (side == EdgeSide.LEFT) left else left + w - stripW
            val alpha = when {
                !active -> 0.08f
                highlight == null || highlight == side -> 0.85f
                else -> 0.3f
            }
            drawRect(color = primary.copy(alpha = alpha), topLeft = Offset(x, top), size = Size(stripW, bottom - top))
        }
    }
}
