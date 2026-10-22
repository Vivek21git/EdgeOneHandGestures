package com.edgeone.gestures.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionMappingTest {

    @Test fun defaultMappingsMatchTheSpec() {
        for (side in EdgeSide.entries) {
            val c = AppSettings().edge(side)
            assertEquals(ActionType.BACK, c.binding(GestureType.INWARD).action.type)
            assertEquals(ActionType.LOCK_SCREEN, c.binding(GestureType.UP).action.type)
            assertEquals(ActionType.RECENTS, c.binding(GestureType.DOWN).action.type)
            assertEquals(ActionType.HOME, c.binding(GestureType.DIAGONAL_UP).action.type)
            assertEquals(ActionType.NOTIFICATIONS, c.binding(GestureType.DIAGONAL_DOWN).action.type)
            assertTrue(GestureType.entries.all { c.binding(it).enabled })
        }
    }

    @Test fun leftAndRightAreIndependent() {
        val changed = AppSettings().withEdge(
            EdgeSide.LEFT,
            AppSettings().left.copy(
                gestures = AppSettings().left.gestures +
                    (GestureType.UP to GestureBinding(true, GestureAction(ActionType.SCREENSHOT))),
            ),
        )
        assertEquals(ActionType.SCREENSHOT, changed.left.binding(GestureType.UP).action.type)
        assertEquals(ActionType.LOCK_SCREEN, changed.right.binding(GestureType.UP).action.type)
    }

    @Test fun everyActionTypeIsRegistered() {
        for (t in ActionType.entries) assertNotNull("missing registry entry for $t", ActionRegistry.all.find { it.type == t })
        assertEquals(ActionType.entries.size, ActionRegistry.all.size)
    }

    @Test fun supportDependsOnSdk() {
        val lock = GestureAction(ActionType.LOCK_SCREEN)
        assertFalse(ActionRegistry.support(lock, sdkInt = 27, accessibilityServiceEnabled = true).supported)
        assertTrue(ActionRegistry.support(lock, sdkInt = 36, accessibilityServiceEnabled = true).supported)
        assertFalse(ActionRegistry.support(GestureAction(ActionType.SPLIT_SCREEN), 23, true).supported)
    }

    @Test fun warnsWhenAccessibilityServiceOff() {
        val s = ActionRegistry.support(GestureAction(ActionType.BACK), 36, accessibilityServiceEnabled = false)
        assertTrue(s.supported)
        assertNotNull(s.warning)
        // launching an app does not need the service
        assertNull(ActionRegistry.support(GestureAction(ActionType.OPEN_APP_SETTINGS), 36, false).warning)
    }

    @Test fun incompleteLaunchActionsAreUnsupported() {
        assertFalse(ActionRegistry.support(GestureAction(ActionType.LAUNCH_APP), 36, true).supported)
        assertFalse(ActionRegistry.support(GestureAction(ActionType.LAUNCH_SHORTCUT), 36, true).supported)
        assertTrue(ActionRegistry.support(GestureAction(ActionType.LAUNCH_APP, packageName = "com.x"), 36, true).supported)
    }

    @Test fun actionEncodingRoundTrips() {
        val a = GestureAction(ActionType.LAUNCH_APP, "com.example", null, "Example | App")
        assertEquals(a, GestureAction.decode(a.encode()))
        val s = GestureAction(ActionType.LAUNCH_SHORTCUT, shortcutUri = "intent:#Intent;action=a;end", label = "S")
        assertEquals(s, GestureAction.decode(s.encode()))
        assertEquals(GestureAction.NONE, GestureAction.decode("garbage"))
        assertEquals(GestureAction.NONE, GestureAction.decode(null))
    }

    @Test fun anyGestureEnabledReflectsBindings() {
        val allOff = EdgeConfig(gestures = GestureType.entries.associateWith { GestureBinding(false, GestureAction(ActionType.BACK)) })
        assertFalse(allOff.anyGestureEnabled)
        val allNone = EdgeConfig(gestures = GestureType.entries.associateWith { GestureBinding(true, GestureAction.NONE) })
        assertFalse(allNone.anyGestureEnabled)
        assertTrue(EdgeConfig().anyGestureEnabled)
    }
}
