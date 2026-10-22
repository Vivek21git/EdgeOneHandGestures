package com.edgeone.gestures.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Why a touch sequence was NOT turned into a gesture. Shown in Diagnostics. */
enum class RejectReason(val text: String) {
    TOO_SHORT("Swipe too short"),
    TOO_SLOW("Swipe too slow"),
    OUTWARD("Moved toward the screen edge, not inward"),
    NOT_STRAIGHT("Path not straight enough"),
    MULTI_TOUCH("More than one finger"),
}

sealed interface GestureOutcome {
    data class Recognized(
        val type: GestureType,
        val distancePx: Float,
        val angleDeg: Float,
        val durationMs: Long,
    ) : GestureOutcome

    data class Rejected(val reason: RejectReason, val distancePx: Float, val angleDeg: Float) : GestureOutcome
}

/** Tunable thresholds, already converted to pixels. Build with [from]. */
data class RecognizerParams(
    val minDistancePx: Float,
    val maxDurationMs: Long = DEFAULT_MAX_DURATION_MS,
    /** Half-width of the sector around each axis, in degrees. Clamped to 10..40 so diagonals always exist. */
    val toleranceDeg: Float = 25f,
    /** displacement / path-length. 1.0 = perfectly straight. */
    val minStraightness: Float = DEFAULT_MIN_STRAIGHTNESS,
) {
    companion object {
        const val DEFAULT_MAX_DURATION_MS = 700L
        const val DEFAULT_MIN_STRAIGHTNESS = 0.6f

        /**
         * sensitivity 0..1 (0.5 = neutral):
         *  - required distance scales from 1.5x (0) to 0.5x (1) of [EdgeConfig.minDistanceDp]
         *  - angular tolerance widens from 18 deg (0) to 32 deg (1)
         */
        fun from(config: EdgeConfig, density: Float): RecognizerParams {
            val s = config.sensitivity.coerceIn(0f, 1f)
            return RecognizerParams(
                minDistancePx = config.minDistanceDp * density * (1.5f - s),
                toleranceDeg = 18f + 14f * s,
            )
        }
    }
}

object GestureClassifier {
    /**
     * Classify a finished touch sequence that began inside an edge strip.
     *
     * Coordinates are screen-style (y grows downward). Both edges are normalised so that
     * +x means "toward the screen centre" and +y means "up".
     * Angle 0 = inward, +90 = up, -90 = down, beyond +-(90+tolerance) = outward (rejected).
     */
    fun classify(
        side: EdgeSide,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        pathLengthPx: Float,
        durationMs: Long,
        multiTouch: Boolean,
        params: RecognizerParams,
    ): GestureOutcome {
        val dx = if (side == EdgeSide.LEFT) endX - startX else startX - endX
        val dy = startY - endY
        val dist = hypot(dx, dy)
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        if (multiTouch) return GestureOutcome.Rejected(RejectReason.MULTI_TOUCH, dist, angle)
        if (dist < params.minDistancePx) return GestureOutcome.Rejected(RejectReason.TOO_SHORT, dist, angle)
        if (durationMs > params.maxDurationMs) return GestureOutcome.Rejected(RejectReason.TOO_SLOW, dist, angle)
        if (pathLengthPx > 0f && dist / pathLengthPx < params.minStraightness) {
            return GestureOutcome.Rejected(RejectReason.NOT_STRAIGHT, dist, angle)
        }

        val t = params.toleranceDeg.coerceIn(10f, 40f)
        val type = when {
            abs(angle) <= t -> GestureType.INWARD
            abs(angle - 90f) <= t -> GestureType.UP
            abs(angle + 90f) <= t -> GestureType.DOWN
            angle > t && angle < 90f - t -> GestureType.DIAGONAL_UP
            angle < -t && angle > -(90f - t) -> GestureType.DIAGONAL_DOWN
            else -> return GestureOutcome.Rejected(RejectReason.OUTWARD, dist, angle)
        }
        return GestureOutcome.Recognized(type, dist, angle, durationMs)
    }
}

/**
 * Accumulates one touch sequence. Feed it the pointer's coordinates; it is deliberately free of
 * Android types so the exact same logic runs in the overlay, the in-app test pad and unit tests.
 */
class GestureTracker {
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var startTime = 0L
    private var path = 0f
    private var multiTouch = false

    var active = false
        private set

    /** Straight-line distance from the start so far. */
    val displacementPx: Float
        get() = if (active) hypot(lastX - startX, lastY - startY) else 0f

    fun onDown(x: Float, y: Float, timeMs: Long) {
        startX = x; startY = y; lastX = x; lastY = y
        startTime = timeMs
        path = 0f
        multiTouch = false
        active = true
    }

    fun onMove(x: Float, y: Float) {
        if (!active) return
        path += hypot(x - lastX, y - lastY)
        lastX = x; lastY = y
    }

    fun onExtraPointer() {
        if (active) multiTouch = true
    }

    fun reset() {
        active = false
    }

    /** Finish the sequence. Returns null if no sequence was active. */
    fun onUp(x: Float, y: Float, timeMs: Long, side: EdgeSide, params: RecognizerParams): GestureOutcome? {
        if (!active) return null
        onMove(x, y)
        active = false
        return GestureClassifier.classify(
            side, startX, startY, x, y, path, timeMs - startTime, multiTouch, params,
        )
    }
}
