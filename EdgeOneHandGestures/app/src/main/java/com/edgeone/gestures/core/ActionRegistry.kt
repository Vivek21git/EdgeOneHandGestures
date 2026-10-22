package com.edgeone.gestures.core

/** Result of asking "can this action work here?". */
data class Support(val supported: Boolean, val warning: String? = null)

data class ActionInfo(
    val type: ActionType,
    val title: String,
    val description: String,
    /** First Android API level where the underlying API exists. */
    val minSdk: Int,
    /** True when the action is performed through the accessibility service. */
    val needsAccessibilityService: Boolean,
    /** Always-shown caveat, e.g. device-specific behaviour I could not verify. */
    val caveat: String? = null,
)

/**
 * Extensible registry: to add an action, add an [ActionType] value, an entry below, and a branch in
 * the Android-side ActionExecutor. Support checks are pure so they can be unit tested.
 */
object ActionRegistry {
    val all: List<ActionInfo> = listOf(
        ActionInfo(ActionType.NONE, "No action", "Gesture is recognised but does nothing", 1, false),
        ActionInfo(ActionType.BACK, "Back", "Same as the system Back button", 16, true),
        ActionInfo(ActionType.HOME, "Home", "Go to the home screen", 16, true),
        ActionInfo(ActionType.RECENTS, "Recent apps", "Open the app switcher", 16, true),
        ActionInfo(
            ActionType.LOCK_SCREEN, "Lock screen", "Turns the display off and locks the device (not a shutdown)", 28, true,
            caveat = "Behaves like a short press of the power button. After locking, your normal unlock method is required; " +
                "whether fingerprint is accepted straight away is decided by the phone, not this app.",
        ),
        ActionInfo(ActionType.NOTIFICATIONS, "Notifications", "Pull down the notification shade", 16, true),
        ActionInfo(ActionType.QUICK_SETTINGS, "Quick settings", "Pull down the quick settings panel", 16, true),
        ActionInfo(
            ActionType.SCREENSHOT, "Screenshot", "Take a screenshot", 28, true,
            caveat = "Some apps (banking, DRM video) block screenshots and will produce a blank image or nothing.",
        ),
        ActionInfo(
            ActionType.SPLIT_SCREEN, "Split screen", "Toggle split-screen for the current app", 24, true,
            caveat = "Only works if the current app supports multi-window; otherwise the system ignores it.",
        ),
        ActionInfo(
            ActionType.LAUNCH_APP, "Launch app", "Open an installed app", 1, false,
            caveat = "Starting an app from the background can be restricted by Android; failures appear in Diagnostics.",
        ),
        ActionInfo(
            ActionType.LAUNCH_SHORTCUT, "Launch shortcut", "Run an app shortcut (from apps that offer shortcut creators)", 1, false,
            caveat = "Only apps that publish a shortcut creator appear. Same background-start caveat as Launch app.",
        ),
        ActionInfo(ActionType.OPEN_APP_SETTINGS, "Open this app's settings", "Open Edge One-Hand Gestures", 1, false),
    )

    private val byType = all.associateBy { it.type }

    fun info(type: ActionType): ActionInfo = byType.getValue(type)

    fun support(action: GestureAction, sdkInt: Int, accessibilityServiceEnabled: Boolean): Support {
        val info = info(action.type)
        if (sdkInt < info.minSdk) {
            return Support(false, "${info.title} needs Android API ${info.minSdk}; this device is API $sdkInt.")
        }
        if (action.isIncomplete) {
            val what = if (action.type == ActionType.LAUNCH_APP) "an app" else "a shortcut"
            return Support(false, "Choose $what for this action.")
        }
        if (info.needsAccessibilityService && !accessibilityServiceEnabled) {
            return Support(true, "Requires the accessibility service to be enabled (Setup screen).")
        }
        return Support(true, info.caveat)
    }
}
