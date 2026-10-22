package com.edgeone.gestures.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsCodecTest {

    @Test fun defaultsRoundTrip() {
        val s = AppSettings()
        assertEquals(s.sanitized(), SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test fun customisedSettingsRoundTrip() {
        val left = EdgeConfig(
            enabled = false, widthDp = 30, verticalStartPct = 10, verticalEndPct = 60,
            sensitivity = 0.8f, minDistanceDp = 70, hapticEnabled = false, animationEnabled = false,
            gestures = EdgeConfig.defaultBindings() +
                (GestureType.UP to GestureBinding(false, GestureAction(ActionType.LAUNCH_APP, "com.android.chrome", null, "Chrome"))) +
                (GestureType.DOWN to GestureBinding(true, GestureAction(ActionType.SCREENSHOT))),
        )
        val s = AppSettings(masterEnabled = false, showZones = true, left = left)
        assertEquals(s.sanitized(), SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test fun missingKeysFallBackToDefaults() {
        assertEquals(AppSettings().sanitized(), SettingsCodec.decode(emptyMap()))
    }

    @Test fun corruptValuesFallBackAndAreClamped() {
        val decoded = SettingsCodec.decode(
            mapOf(
                "left.widthDp" to "not-a-number",
                "left.enabled" to "maybe",
                "right.widthDp" to "9999",
                "right.sensitivity" to "7.5",
                "left.g.UP.action" to "NOT_AN_ACTION",
            ),
        )
        assertEquals(EdgeConfig.DEFAULT_WIDTH_DP, decoded.left.widthDp)
        assertEquals(true, decoded.left.enabled)
        assertEquals(EdgeConfig.MAX_WIDTH_DP, decoded.right.widthDp)
        assertEquals(1f, decoded.right.sensitivity)
        assertEquals(ActionType.NONE, decoded.left.binding(GestureType.UP).action.type)
    }

    @Test fun disablingOneGestureDoesNotTouchOthers() {
        val s = AppSettings().let {
            it.withEdge(EdgeSide.RIGHT, it.right.copy(gestures = it.right.gestures + (GestureType.INWARD to GestureBinding(false, GestureAction(ActionType.BACK)))))
        }
        val back = SettingsCodec.decode(SettingsCodec.encode(s))
        assertFalse(back.right.binding(GestureType.INWARD).enabled)
        assertEquals(true, back.right.binding(GestureType.UP).enabled)
        assertEquals(true, back.left.binding(GestureType.INWARD).enabled)
    }
}
