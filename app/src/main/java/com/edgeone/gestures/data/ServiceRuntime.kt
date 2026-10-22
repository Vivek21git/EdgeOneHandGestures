package com.edgeone.gestures.data

import com.edgeone.gestures.core.EdgeSide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RuntimeState(
    val serviceConnected: Boolean = false,
    val leftStripShown: Boolean = false,
    val rightStripShown: Boolean = false,
    val lastError: String? = null,
) {
    fun stripShown(side: EdgeSide) = if (side == EdgeSide.LEFT) leftStripShown else rightStripShown
}

data class LogEntry(
    val timeMillis: Long,
    val side: EdgeSide?,
    val message: String,
    val ok: Boolean,
)

/**
 * In-process, in-memory status shared by the accessibility service and the UI (both run in the same
 * process). Nothing here is persisted or transmitted; the log is capped and cleared when the
 * process dies.
 */
object ServiceRuntime {
    private const val MAX_LOG = 60

    private val _state = MutableStateFlow(RuntimeState())
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    fun setConnected(connected: Boolean) = _state.update {
        if (connected) it.copy(serviceConnected = true)
        else RuntimeState(serviceConnected = false, lastError = it.lastError)
    }

    fun setStripShown(side: EdgeSide, shown: Boolean) = _state.update {
        if (side == EdgeSide.LEFT) it.copy(leftStripShown = shown) else it.copy(rightStripShown = shown)
    }

    fun setError(message: String?) = _state.update { it.copy(lastError = message) }

    fun log(side: EdgeSide?, message: String, ok: Boolean) {
        val entry = LogEntry(System.currentTimeMillis(), side, message, ok)
        _log.update { (listOf(entry) + it).take(MAX_LOG) }
        if (!ok) setError(message)
    }

    fun clearLog() {
        _log.value = emptyList()
        setError(null)
    }
}
