package com.edgeone.gestures.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import com.edgeone.gestures.core.AppSettings
import com.edgeone.gestures.core.EdgeSide
import com.edgeone.gestures.core.EdgeZoneGeometry
import com.edgeone.gestures.core.GestureType
import com.edgeone.gestures.data.ServiceRuntime

/**
 * Owns at most one strip window per edge. [apply] is idempotent: call it whenever settings or the
 * display change; it adds, updates or removes windows as needed, so duplicates cannot occur.
 *
 * [serviceContext] MUST be the AccessibilityService itself: its WindowManager carries the token
 * required for TYPE_ACCESSIBILITY_OVERLAY, which is why no "Display over other apps" permission is needed.
 */
class EdgeOverlayManager(
    private val serviceContext: Context,
    private val onGesture: (EdgeSide, GestureType) -> Unit,
) {
    private val wm = serviceContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val views = mutableMapOf<EdgeSide, EdgeStripView>()

    fun apply(settings: AppSettings) {
        val s = settings.sanitized()
        val (screenW, screenH) = screenSize()
        val density = serviceContext.resources.displayMetrics.density
        for (side in EdgeSide.entries) {
            val cfg = s.edge(side)
            val wanted = s.masterEnabled && cfg.enabled && cfg.anyGestureEnabled
            if (!wanted) {
                remove(side)
                continue
            }
            val zone = EdgeZoneGeometry.zone(side, screenW, screenH, density, cfg)
            val params = layoutParams(side, zone.width, zone.height, zone.top)
            val existing = views[side]
            try {
                if (existing == null) {
                    val view = EdgeStripView(
                        serviceContext, side, onGesture,
                        onLog = { sd, msg, ok -> ServiceRuntime.log(sd, msg, ok) },
                    )
                    view.config = cfg
                    view.showZone = s.showZones
                    wm.addView(view, params)
                    views[side] = view
                } else {
                    existing.config = cfg
                    existing.showZone = s.showZones
                    wm.updateViewLayout(existing, params)
                }
                ServiceRuntime.setStripShown(side, true)
            } catch (t: Throwable) {
                // e.g. BadTokenException if the service was just disabled, or IllegalStateException.
                ServiceRuntime.log(side, "Could not show ${side.title} strip: ${t.javaClass.simpleName}", false)
                views.remove(side)?.release()
                ServiceRuntime.setStripShown(side, false)
            }
        }
    }

    fun removeAll() = EdgeSide.entries.forEach { remove(it) }

    private fun remove(side: EdgeSide) {
        val view = views.remove(side)
        if (view != null) {
            view.release()
            try { wm.removeViewImmediate(view) } catch (_: Throwable) { /* already gone */ }
        }
        ServiceRuntime.setStripShown(side, false)
    }

    private fun layoutParams(side: EdgeSide, w: Int, h: Int, top: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // NOT_FOCUSABLE: never steals keyboard focus / IME.
            // NOT_TOUCH_MODAL: touches outside the strip still reach the app underneath.
            // LAYOUT_IN_SCREEN + NO_LIMITS: strip can sit flush against the physical edge.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or (if (side == EdgeSide.LEFT) Gravity.LEFT else Gravity.RIGHT)
            x = 0
            y = top
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "EdgeOneStrip-${side.name}"
        }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            return b.width() to b.height()
        }
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        return dm.widthPixels to dm.heightPixels
    }
}
