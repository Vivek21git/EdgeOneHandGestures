package com.edgeone.gestures.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.edgeone.gestures.core.EdgeConfig
import com.edgeone.gestures.core.EdgeSide
import com.edgeone.gestures.core.GestureOutcome
import com.edgeone.gestures.core.GestureTracker
import com.edgeone.gestures.core.GestureType
import com.edgeone.gestures.core.RecognizerParams

/**
 * One thin, transparent, touchable strip. It sees only touches that START inside its own bounds
 * (Android keeps delivering that pointer's later events to this window even after the finger
 * leaves it), which is exactly the "gesture must begin in the edge zone" rule.
 */
class EdgeStripView(
    context: Context,
    private val side: EdgeSide,
    private val onGesture: (EdgeSide, GestureType) -> Unit,
    private val onLog: (EdgeSide, String, Boolean) -> Unit,
) : View(context) {

    var config: EdgeConfig = EdgeConfig()
    var showZone: Boolean = false
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val tracker = GestureTracker()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var flash = 0f
    private var flashAnimator: ValueAnimator? = null
    private val exclusion = Rect()

    init {
        isHapticFeedbackEnabled = true
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // Best effort: ask the system not to start its own Back gesture inside the strip.
        // The system caps this (about 200dp of height per edge) and may ignore it for overlay
        // windows; this is NOT verified on the target phone. See README.
        exclusion.set(0, 0, r - l, b - t)
        systemGestureExclusionRects = listOf(exclusion)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> tracker.onDown(event.x, event.y, event.eventTime)
            MotionEvent.ACTION_POINTER_DOWN -> tracker.onExtraPointer()
            MotionEvent.ACTION_MOVE -> tracker.onMove(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                val outcome = tracker.onUp(
                    event.x, event.y, event.eventTime, side, RecognizerParams.from(config, density),
                )
                if (outcome != null) handle(outcome)
            }
            MotionEvent.ACTION_CANCEL -> {
                if (tracker.active && tracker.displacementPx > TAP_SLOP_DP * density) {
                    onLog(side, "Touch cancelled by the system (possibly its own Back gesture took over)", false)
                }
                tracker.reset()
            }
        }
        return true
    }

    private fun handle(outcome: GestureOutcome) {
        when (outcome) {
            is GestureOutcome.Recognized -> {
                if (config.hapticEnabled) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                if (config.animationEnabled) flash()
                onGesture(side, outcome.type)
            }
            is GestureOutcome.Rejected -> {
                // Plain taps are not interesting; only log real attempts.
                if (outcome.distancePx > TAP_SLOP_DP * density) {
                    onLog(side, "Ignored: ${outcome.reason.text} (${outcome.distancePx.toInt()}px, ${outcome.angleDeg.toInt()}°)", true)
                }
            }
        }
    }

    private fun flash() {
        flashAnimator?.cancel()
        flashAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 220
            addUpdateListener { flash = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val alpha = maxOf(if (showZone) 0.28f else 0f, flash * 0.6f)
        if (alpha <= 0f) return
        paint.color = Color.argb((alpha * 255).toInt(), 0, 200, 180)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    fun release() {
        flashAnimator?.cancel()
        flashAnimator = null
        tracker.reset()
    }

    private companion object {
        const val TAP_SLOP_DP = 8f
    }
}
