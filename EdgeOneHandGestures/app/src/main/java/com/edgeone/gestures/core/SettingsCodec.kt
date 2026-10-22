package com.edgeone.gestures.core

/**
 * Flat string-map encoding of [AppSettings]. Kept free of DataStore so it is trivially testable;
 * [com.edgeone.gestures.data.SettingsRepository] just writes these entries as preference keys.
 * Decoding is forgiving: missing or corrupt entries fall back to defaults.
 */
object SettingsCodec {
    const val KEY_MASTER = "master"
    const val KEY_SHOW_ZONES = "showZones"

    private fun key(side: EdgeSide, field: String) = "${side.name.lowercase()}.$field"
    private fun gKey(side: EdgeSide, type: GestureType, field: String) =
        "${side.name.lowercase()}.g.${type.name}.$field"

    fun encode(settings: AppSettings): Map<String, String> {
        val s = settings.sanitized()
        val out = linkedMapOf(
            KEY_MASTER to s.masterEnabled.toString(),
            KEY_SHOW_ZONES to s.showZones.toString(),
        )
        for (side in EdgeSide.entries) {
            val c = s.edge(side)
            out[key(side, "enabled")] = c.enabled.toString()
            out[key(side, "widthDp")] = c.widthDp.toString()
            out[key(side, "vStart")] = c.verticalStartPct.toString()
            out[key(side, "vEnd")] = c.verticalEndPct.toString()
            out[key(side, "sensitivity")] = c.sensitivity.toString()
            out[key(side, "minDistDp")] = c.minDistanceDp.toString()
            out[key(side, "haptic")] = c.hapticEnabled.toString()
            out[key(side, "anim")] = c.animationEnabled.toString()
            for (type in GestureType.entries) {
                val b = c.binding(type)
                out[gKey(side, type, "enabled")] = b.enabled.toString()
                out[gKey(side, type, "action")] = b.action.encode()
            }
        }
        return out
    }

    fun decode(map: Map<String, String>): AppSettings {
        val d = EdgeConfig()
        fun edge(side: EdgeSide): EdgeConfig {
            val bindings = GestureType.entries.associateWith { type ->
                val def = EdgeConfig.defaultBindings().getValue(type)
                val action = map[gKey(side, type, "action")]
                GestureBinding(
                    enabled = map[gKey(side, type, "enabled")]?.toBooleanStrictOrNull() ?: def.enabled,
                    action = if (action == null) def.action else GestureAction.decode(action),
                )
            }
            return EdgeConfig(
                enabled = map[key(side, "enabled")]?.toBooleanStrictOrNull() ?: d.enabled,
                widthDp = map[key(side, "widthDp")]?.toIntOrNull() ?: d.widthDp,
                verticalStartPct = map[key(side, "vStart")]?.toIntOrNull() ?: d.verticalStartPct,
                verticalEndPct = map[key(side, "vEnd")]?.toIntOrNull() ?: d.verticalEndPct,
                sensitivity = map[key(side, "sensitivity")]?.toFloatOrNull() ?: d.sensitivity,
                minDistanceDp = map[key(side, "minDistDp")]?.toIntOrNull() ?: d.minDistanceDp,
                hapticEnabled = map[key(side, "haptic")]?.toBooleanStrictOrNull() ?: d.hapticEnabled,
                animationEnabled = map[key(side, "anim")]?.toBooleanStrictOrNull() ?: d.animationEnabled,
                gestures = bindings,
            ).sanitized()
        }
        return AppSettings(
            masterEnabled = map[KEY_MASTER]?.toBooleanStrictOrNull() ?: true,
            showZones = map[KEY_SHOW_ZONES]?.toBooleanStrictOrNull() ?: false,
            left = edge(EdgeSide.LEFT),
            right = edge(EdgeSide.RIGHT),
        )
    }
}
