package com.edgeone.gestures.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class GestureClassifierTest {

    private val params = RecognizerParams(minDistancePx = 100f, toleranceDeg = 25f)

    /** Straight swipe of [dist] px at [angleDeg] (0=inward, 90=up) starting at (500,1000). */
    private fun swipe(
        side: EdgeSide, angleDeg: Double, dist: Float = 200f, durationMs: Long = 200,
        p: RecognizerParams = params, multi: Boolean = false,
    ): GestureOutcome {
        val rad = Math.toRadians(angleDeg)
        val dxIn = (cos(rad) * dist).toFloat()
        val dyUp = (sin(rad) * dist).toFloat()
        val sx = 500f
        val sy = 1000f
        val ex = if (side == EdgeSide.LEFT) sx + dxIn else sx - dxIn
        val ey = sy - dyUp
        return GestureClassifier.classify(side, sx, sy, ex, ey, dist, durationMs, multi, p)
    }

    private fun type(o: GestureOutcome) = (o as GestureOutcome.Recognized).type
    private fun reason(o: GestureOutcome) = (o as GestureOutcome.Rejected).reason

    @Test fun inwardOnBothEdges() {
        assertEquals(GestureType.INWARD, type(swipe(EdgeSide.LEFT, 0.0)))
        assertEquals(GestureType.INWARD, type(swipe(EdgeSide.RIGHT, 0.0)))
    }

    @Test fun inwardToleranceBoundary() {
        assertEquals(GestureType.INWARD, type(swipe(EdgeSide.LEFT, 24.0)))
        assertEquals(GestureType.INWARD, type(swipe(EdgeSide.LEFT, -24.0)))
        // just past 25 deg becomes a diagonal, not inward
        assertEquals(GestureType.DIAGONAL_UP, type(swipe(EdgeSide.LEFT, 27.0)))
        assertEquals(GestureType.DIAGONAL_DOWN, type(swipe(EdgeSide.LEFT, -27.0)))
    }

    @Test fun verticalUpAndDown() {
        for (side in EdgeSide.entries) {
            assertEquals(GestureType.UP, type(swipe(side, 90.0)))
            assertEquals(GestureType.DOWN, type(swipe(side, -90.0)))
        }
    }

    @Test fun verticalRequiresVerticalMovementDominating() {
        // 65 deg is the edge of the UP sector: vertical component is ~2.1x horizontal
        assertEquals(GestureType.UP, type(swipe(EdgeSide.LEFT, 66.0)))
        // 60 deg: vertical only 1.7x horizontal -> must NOT count as plain UP
        assertEquals(GestureType.DIAGONAL_UP, type(swipe(EdgeSide.LEFT, 60.0)))
        assertEquals(GestureType.DIAGONAL_DOWN, type(swipe(EdgeSide.LEFT, -60.0)))
    }

    @Test fun diagonalsAt45Degrees() {
        for (side in EdgeSide.entries) {
            assertEquals(GestureType.DIAGONAL_UP, type(swipe(side, 45.0)))
            assertEquals(GestureType.DIAGONAL_DOWN, type(swipe(side, -45.0)))
        }
    }

    @Test fun upSwipeCurvingSlightlyOutwardStillCounts() {
        assertEquals(GestureType.UP, type(swipe(EdgeSide.LEFT, 100.0)))
        assertEquals(GestureType.DOWN, type(swipe(EdgeSide.RIGHT, -100.0)))
    }

    @Test fun outwardMovementRejected() {
        assertEquals(RejectReason.OUTWARD, reason(swipe(EdgeSide.LEFT, 180.0)))
        assertEquals(RejectReason.OUTWARD, reason(swipe(EdgeSide.RIGHT, 150.0)))
        assertEquals(RejectReason.OUTWARD, reason(swipe(EdgeSide.LEFT, -140.0)))
    }

    @Test fun mirroringIsCorrect() {
        // Raw leftward movement on the RIGHT edge is inward; on the LEFT edge it is outward.
        val onRight = GestureClassifier.classify(EdgeSide.RIGHT, 500f, 1000f, 300f, 1000f, 200f, 200, false, params)
        val onLeft = GestureClassifier.classify(EdgeSide.LEFT, 500f, 1000f, 300f, 1000f, 200f, 200, false, params)
        assertEquals(GestureType.INWARD, type(onRight))
        assertEquals(RejectReason.OUTWARD, reason(onLeft))
    }

    @Test fun minimumDistance() {
        assertEquals(RejectReason.TOO_SHORT, reason(swipe(EdgeSide.LEFT, 0.0, dist = 99f)))
        assertEquals(GestureType.INWARD, type(swipe(EdgeSide.LEFT, 0.0, dist = 100f)))
    }

    @Test fun timeout() {
        assertEquals(RejectReason.TOO_SLOW, reason(swipe(EdgeSide.LEFT, 0.0, durationMs = 701)))
        assertEquals(GestureType.INWARD, type(swipe(EdgeSide.LEFT, 0.0, durationMs = 700)))
    }

    @Test fun wigglyPathRejected() {
        // 200px displacement but 500px of travel => straightness 0.4 < 0.6
        val o = GestureClassifier.classify(EdgeSide.LEFT, 0f, 1000f, 200f, 1000f, 500f, 200, false, params)
        assertEquals(RejectReason.NOT_STRAIGHT, reason(o))
    }

    @Test fun multiTouchRejected() {
        assertEquals(RejectReason.MULTI_TOUCH, reason(swipe(EdgeSide.LEFT, 0.0, multi = true)))
    }

    @Test fun tapIsRejectedAsTooShort() {
        val o = GestureClassifier.classify(EdgeSide.LEFT, 5f, 5f, 5f, 5f, 0f, 50, false, params)
        assertEquals(RejectReason.TOO_SHORT, reason(o))
    }

    @Test fun sensitivityScalesDistanceAndTolerance() {
        val cfg = EdgeConfig(minDistanceDp = 48)
        val low = RecognizerParams.from(cfg.copy(sensitivity = 0f), density = 2f)
        val mid = RecognizerParams.from(cfg.copy(sensitivity = 0.5f), density = 2f)
        val high = RecognizerParams.from(cfg.copy(sensitivity = 1f), density = 2f)
        assertEquals(48 * 2 * 1.0f, mid.minDistancePx, 0.001f)
        assertTrue(low.minDistancePx > mid.minDistancePx && mid.minDistancePx > high.minDistancePx)
        assertTrue(low.toleranceDeg < mid.toleranceDeg && mid.toleranceDeg < high.toleranceDeg)
    }

    @Test fun trackerEndToEnd() {
        val t = GestureTracker()
        t.onDown(10f, 1000f, 0)
        t.onMove(110f, 1000f)
        t.onMove(260f, 1005f)
        val o = t.onUp(310f, 1000f, 180, EdgeSide.LEFT, params)
        assertEquals(GestureType.INWARD, type(o!!))
        // After finishing, a stray UP does nothing.
        assertEquals(null, t.onUp(0f, 0f, 0, EdgeSide.LEFT, params))
    }

    @Test fun trackerSecondFingerInvalidates() {
        val t = GestureTracker()
        t.onDown(10f, 1000f, 0)
        t.onExtraPointer()
        t.onMove(300f, 1000f)
        assertEquals(RejectReason.MULTI_TOUCH, reason(t.onUp(300f, 1000f, 100, EdgeSide.LEFT, params)!!))
    }
}
