package com.edgeone.gestures.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edgeone.gestures.core.AppSettings
import com.edgeone.gestures.data.ServiceRuntime
import com.edgeone.gestures.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val runtime = ServiceRuntime.state
    val log = ServiceRuntime.log

    private val _serviceEnabled = MutableStateFlow(SystemStatus.isAccessibilityServiceEnabled(app))
    val serviceEnabledInSettings: StateFlow<Boolean> = _serviceEnabled.asStateFlow()

    /** Call from ON_RESUME: the user may have toggled the service in Settings. */
    fun refreshServiceStatus() {
        _serviceEnabled.value = SystemStatus.isAccessibilityServiceEnabled(getApplication())
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.update(transform) }
    }

    fun resetDefaults() {
        viewModelScope.launch { repo.resetToDefaults() }
    }

    fun clearLog() = ServiceRuntime.clearLog()
}
