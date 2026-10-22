package com.edgeone.gestures.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.edgeone.gestures.core.AppSettings
import com.edgeone.gestures.core.SettingsCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "edge_settings")

/**
 * Persists [AppSettings] in Jetpack DataStore (local app storage only).
 * One process-wide DataStore instance is shared by the activity and the accessibility service.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val settings: Flow<AppSettings> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            SettingsCodec.decode(prefs.asMap().entries.associate { (k, v) -> k.name to v.toString() })
        }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val current = SettingsCodec.decode(prefs.asMap().entries.associate { (k, v) -> k.name to v.toString() })
            val next = transform(current).sanitized()
            for ((k, v) in SettingsCodec.encode(next)) prefs[stringPreferencesKey(k)] = v
        }
    }

    suspend fun resetToDefaults() = update { AppSettings() }
}
