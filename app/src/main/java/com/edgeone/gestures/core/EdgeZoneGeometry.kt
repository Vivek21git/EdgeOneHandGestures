package com.edgeone.gestures.core

import kotlin.math.roundToInt

/** Integer pixel rectangle in screen coordinates. [right] and [bottom] are exclusive. */
data class ZoneRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

object EdgeZoneGeometry {
    /** A strip may never take more than this fraction of the screen width. */
    private const val MAX_WIDTH_FRACTION = 0.25f

    /** Pixel rectangle of the activation strip for one edge. */
    fun zone(side: EdgeSide, screenWidthPx: Int, screenHeightPx: Int, density: Float, config: EdgeConfig): ZoneRect {
        val c = config.sanitized()
        val w = (c.widthDp * density).roundToInt().coerceIn(1, (screenWidthPx * MAX_WIDTH_FRACTION).roundToInt().coerceAtLeast(1))
        val top = (screenHeightPx * c.verticalStartPct / 100f).roundToInt()
        val bottom = (screenHeightPx * c.verticalEndPct / 100f).roundToInt().coerceAtLeast(top + 1)
        return if (side == EdgeSide.LEFT) {
            ZoneRect(0, top, w, bottom)
        } else {
            ZoneRect(screenWidthPx - w, top, screenWidthPx, bottom)
        }
    }

    /** True if a touch at ([x],[y]) would start a gesture on this edge. */
    fun startsInZone(
        side: EdgeSide, x: Float, y: Float,
        screenWidthPx: Int, screenHeightPx: Int, density: Float, config: EdgeConfig,
    ): Boolean = zone(side, screenWidthPx, screenHeightPx, density, config).contains(x, y)
}
