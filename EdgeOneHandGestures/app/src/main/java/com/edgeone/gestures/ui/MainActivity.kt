package com.edgeone.gestures.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeone.gestures.core.EdgeSide

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EdgeTheme { AppRoot(vm) } }
    }
}

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_SETUP = "setup"
private const val ROUTE_DIAG = "diagnostics"
private const val ROUTE_EDGE_PREFIX = "edge:"

@Composable
private fun AppRoot(vm: MainViewModel) {
    // Single-level navigation: every sub-screen returns to the dashboard.
    var route by rememberSaveable { mutableStateOf(ROUTE_DASHBOARD) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val runtime by vm.runtime.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val serviceEnabled by vm.serviceEnabledInSettings.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshServiceStatus() }
    BackHandler(enabled = route != ROUTE_DASHBOARD) { route = ROUTE_DASHBOARD }
    val back = { route = ROUTE_DASHBOARD }

    when {
        route == ROUTE_SETUP -> SetupScreen(serviceEnabled = serviceEnabled, onBack = back)
        route == ROUTE_DIAG -> DiagnosticsScreen(
            settings = settings, runtime = runtime, log = log, serviceEnabled = serviceEnabled,
            onClearLog = vm::clearLog, onBack = back,
        )
        route.startsWith(ROUTE_EDGE_PREFIX) -> {
            val side = runCatching { EdgeSide.valueOf(route.removePrefix(ROUTE_EDGE_PREFIX)) }.getOrDefault(EdgeSide.LEFT)
            EdgeConfigScreen(side = side, settings = settings, serviceEnabled = serviceEnabled, onUpdate = vm::update, onBack = back)
        }
        else -> DashboardScreen(
            settings = settings, runtime = runtime, serviceEnabled = serviceEnabled,
            onUpdate = vm::update,
            onOpenEdge = { route = ROUTE_EDGE_PREFIX + it.name },
            onOpenSetup = { route = ROUTE_SETUP },
            onOpenDiagnostics = { route = ROUTE_DIAG },
            onResetDefaults = vm::resetDefaults,
        )
    }
}
