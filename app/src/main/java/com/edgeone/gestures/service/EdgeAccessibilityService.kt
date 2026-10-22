package com.edgeone.gestures.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.edgeone.gestures.core.AppSettings
import com.edgeone.gestures.core.EdgeSide
import com.edgeone.gestures.core.GestureType
import com.edgeone.gestures.data.ServiceRuntime
import com.edgeone.gestures.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts the edge strips and performs the system actions.
 *
 * It subscribes to NO accessibility events and cannot read window content (see
 * accessibility_service_config.xml). It exists only because (a) performGlobalAction() needs it
 * and (b) accessibility-overlay windows are how a non-root app can place touchable strips above
 * other apps without the "Display over other apps" permission.
 */
class EdgeAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var settingsJob: Job? = null
    private var latest = AppSettings()
    private var overlay: EdgeOverlayManager? = null
    private var executor: ActionExecutor? = null
    private var displayManager: DisplayManager? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = scheduleRefresh()
    }

    private val refreshRunnable = Runnable { overlay?.apply(latest) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Guard against a second onServiceConnected without onUnbind: never keep two overlay sets.
        teardown()
        executor = ActionExecutor(this)
        overlay = EdgeOverlayManager(this, ::onGesture)
        displayManager = (getSystemService(DISPLAY_SERVICE) as DisplayManager).also {
            it.registerDisplayListener(displayListener, mainHandler)
        }
        ServiceRuntime.setConnected(true)
        ServiceRuntime.log(null, "Accessibility service connected", true)

        settingsJob = scope.launch {
            SettingsRepository(applicationContext).settings.collect { s ->
                latest = s
                overlay?.apply(s)
            }
        }
    }

    private fun onGesture(side: EdgeSide, type: GestureType) {
        val binding = latest.edge(side).binding(type)
        if (!binding.enabled) {
            ServiceRuntime.log(side, "${type.title}: disabled, ignored", true)
            return
        }
        val result = executor?.execute(binding.action) ?: ExecResult.Failure("Service not ready")
        when (result) {
            ExecResult.Success -> ServiceRuntime.log(side, "${type.title} → ${binding.action.type.name}", true)
            is ExecResult.Failure -> ServiceRuntime.log(side, "${type.title} failed: ${result.message}", false)
        }
    }

    /** Rotation / fold / resolution changes: rebuild strip geometry (debounced). */
    private fun scheduleRefresh() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, 120)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scheduleRefresh()
    }

    // Intentionally empty: no events are requested in the service configuration.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        ServiceRuntime.setConnected(false)
        ServiceRuntime.log(null, "Accessibility service disconnected", true)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        ServiceRuntime.setConnected(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun teardown() {
        mainHandler.removeCallbacks(refreshRunnable)
        settingsJob?.cancel(); settingsJob = null
        displayManager?.unregisterDisplayListener(displayListener); displayManager = null
        overlay?.removeAll(); overlay = null
        executor = null
    }
}
