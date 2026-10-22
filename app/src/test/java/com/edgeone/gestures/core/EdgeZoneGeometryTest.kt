package com.edgeone.gestures.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeZoneGeometryTest {
    private val cfg = EdgeConfig(widthDp = 20, verticalStartPct = 25, verticalEndPct = 75)
    private val w = 1080
    private val h = 2400
    private val density = 2f // 20dp -> 40px

    @Test fun leftZoneRect() {
        val z = EdgeZoneGeometry.zone(EdgeSide.LEFT, w, h, density, cfg)
        assertEquals(ZoneRect(0, 600, 40, 1800), z)
    }

    @Test fun rightZoneRect() {
        val z = EdgeZoneGeometry.zone(EdgeSide.RIGHT, w, h, density, cfg)
        assertEquals(ZoneRect(1040, 600, 1080, 1800), z)
    }

    @Test fun boundariesAreHalfOpen() {
        val l = EdgeSide.LEFT
        fun hit(x: Float, y: Float) = EdgeZoneGeometry.startsInZone(l, x, y, w, h, density, cfg)
        assertTrue(hit(0f, 600f))        // top-left corner included
        assertTrue(hit(39.9f, 1799.9f))  // just inside
        assertFalse(hit(40f, 1000f))     // first pixel past the width
        assertFalse(hit(10f, 599f))      // above the vertical range
        assertFalse(hit(10f, 1800f))     // below it
    }

    @Test fun rightEdgeBoundaries() {
        val r = EdgeSide.RIGHT
        fun hit(x: Float, y: Float) = EdgeZoneGeometry.startsInZone(r, x, y, w, h, density, cfg)
        assertTrue(hit(1079f, 1000f))
        assertTrue(hit(1040f, 1000f))
        assertFalse(hit(1039f, 1000f))
        assertFalse(hit(5f, 1000f)) // the left edge is not the right zone
    }

    @Test fun landscapeRecomputesFromNewSize() {
        val z = EdgeZoneGeometry.zone(EdgeSide.RIGHT, 2400, 1080, density, cfg)
        assertEquals(ZoneRect(2360, 270, 2400, 810), z)
    }

    @Test fun widthNeverExceedsQuarterOfScreen() {
        val huge = cfg.copy(widthDp = 40)
        val z = EdgeZoneGeometry.zone(EdgeSide.LEFT, 200, 400, 10f, huge)
        assertEquals(50, z.width)
    }

    @Test fun invalidConfigIsSanitized() {
        val bad = EdgeConfig(widthDp = 999, verticalStartPct = 95, verticalEndPct = 5)
        val s = bad.sanitized()
        assertEquals(EdgeConfig.MAX_WIDTH_DP, s.widthDp)
        assertTrue(s.verticalEndPct - s.verticalStartPct >= EdgeConfig.MIN_RANGE_PCT)
        assertTrue(s.verticalStartPct in 0..100 && s.verticalEndPct in 0..100)
    }
}
