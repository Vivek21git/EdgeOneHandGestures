package com.edgeone.gestures.core

/** Which physical screen edge a gesture strip sits on. */
enum class EdgeSide(val title: String) {
    LEFT("Left edge"),
    RIGHT("Right edge"),
}

/**
 * Gestures, all expressed relative to the edge the touch started on:
 * "inward" always means toward the screen centre, so the same definition works for both edges.
 */
enum class GestureType(val title: String, val hint: String) {
    INWARD("Swipe inward", "Horizontal swipe toward the centre of the screen"),
    UP("Swipe up", "Mostly vertical swipe upward along the edge"),
    DOWN("Swipe down", "Mostly vertical swipe downward along the edge"),
    DIAGONAL_UP("Diagonal up", "Short swipe toward the centre and upward"),
    DIAGONAL_DOWN("Diagonal down", "Short swipe toward the centre and downward"),
}

/** Every action the registry knows about. Add a value here + an entry in [ActionRegistry]. */
enum class ActionType {
    NONE,
    BACK,
    HOME,
    RECENTS,
    LOCK_SCREEN,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    SCREENSHOT,
    SPLIT_SCREEN,
    LAUNCH_APP,
    LAUNCH_SHORTCUT,
    OPEN_APP_SETTINGS,
}

/**
 * An action plus its optional parameters.
 * [packageName] is used by LAUNCH_APP; [shortcutUri] (an Intent URI) by LAUNCH_SHORTCUT.
 * [label] is only for display.
 */
data class GestureAction(
    val type: ActionType,
    val packageName: String? = null,
    val shortcutUri: String? = null,
    val label: String? = null,
) {
    /** True when the action still needs a target chosen (app / shortcut). */
    val isIncomplete: Boolean
        get() = (type == ActionType.LAUNCH_APP && packageName.isNullOrBlank()) ||
            (type == ActionType.LAUNCH_SHORTCUT && shortcutUri.isNullOrBlank())

    fun encode(): String = listOf(type.name, packageName.orEmpty(), shortcutUri.orEmpty(), label.orEmpty())
        .joinToString(SEP.toString())

    companion object {
        private const val SEP = '\u001F'
        val NONE = GestureAction(ActionType.NONE)

        fun decode(raw: String?): GestureAction {
            if (raw.isNullOrEmpty()) return NONE
            val parts = raw.split(SEP)
            val type = runCatching { ActionType.valueOf(parts[0]) }.getOrNull() ?: return NONE
            return GestureAction(
                type = type,
                packageName = parts.getOrNull(1)?.ifEmpty { null },
                shortcutUri = parts.getOrNull(2)?.ifEmpty { null },
                label = parts.getOrNull(3)?.ifEmpty { null },
            )
        }
    }
}

data class GestureBinding(val enabled: Boolean, val action: GestureAction)

/** Per-edge settings. Always pass through [sanitized] before use; the UI and decoder both do. */
data class EdgeConfig(
    val enabled: Boolean = true,
    /** Width of the touch strip in dp. */
    val widthDp: Int = DEFAULT_WIDTH_DP,
    /** Vertical extent of the strip as a percentage of screen height. */
    val verticalStartPct: Int = 25,
    val verticalEndPct: Int = 80,
    /** 0 = needs deliberate swipes, 1 = triggers easily. */
    val sensitivity: Float = 0.5f,
    val minDistanceDp: Int = 48,
    val hapticEnabled: Boolean = true,
    val animationEnabled: Boolean = true,
    val gestures: Map<GestureType, GestureBinding> = defaultBindings(),
) {
    fun binding(type: GestureType): GestureBinding = gestures[type] ?: defaultBindings().getValue(type)

    val anyGestureEnabled: Boolean
        get() = GestureType.entries.any { binding(it).enabled && binding(it).action.type != ActionType.NONE }

    fun sanitized(): EdgeConfig {
        val start = verticalStartPct.coerceIn(0, 100 - MIN_RANGE_PCT)
        val end = verticalEndPct.coerceIn(start + MIN_RANGE_PCT, 100)
        return copy(
            widthDp = widthDp.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP),
            verticalStartPct = start,
            verticalEndPct = end,
            sensitivity = sensitivity.coerceIn(0f, 1f),
            minDistanceDp = minDistanceDp.coerceIn(MIN_DISTANCE_DP, MAX_DISTANCE_DP),
            gestures = GestureType.entries.associateWith { binding(it) },
        )
    }

    companion object {
        const val DEFAULT_WIDTH_DP = 16
        const val MIN_WIDTH_DP = 6
        const val MAX_WIDTH_DP = 40
        const val MIN_RANGE_PCT = 10
        const val MIN_DISTANCE_DP = 24
        const val MAX_DISTANCE_DP = 200

        fun defaultBindings(): Map<GestureType, GestureBinding> = mapOf(
            GestureType.INWARD to GestureBinding(true, GestureAction(ActionType.BACK)),
            GestureType.UP to GestureBinding(true, GestureAction(ActionType.LOCK_SCREEN)),
            GestureType.DOWN to GestureBinding(true, GestureAction(ActionType.RECENTS)),
            GestureType.DIAGONAL_UP to GestureBinding(true, GestureAction(ActionType.HOME)),
            GestureType.DIAGONAL_DOWN to GestureBinding(true, GestureAction(ActionType.NOTIFICATIONS)),
        )
    }
}

data class AppSettings(
    /** Master switch. When false no edge strips exist at all. */
    val masterEnabled: Boolean = true,
    /** Draw the strips faintly on screen, for calibration. */
    val showZones: Boolean = false,
    val left: EdgeConfig = EdgeConfig(),
    val right: EdgeConfig = EdgeConfig(),
) {
    fun edge(side: EdgeSide): EdgeConfig = if (side == EdgeSide.LEFT) left else right

    fun withEdge(side: EdgeSide, config: EdgeConfig): AppSettings =
        if (side == EdgeSide.LEFT) copy(left = config) else copy(right = config)

    fun sanitized(): AppSettings = copy(left = left.sanitized(), right = right.sanitized())
}
