package com.rpmgauge.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)
    private val analyzer = RpmAnalyzer(app)

    val sessions = store.sessions
    val currentRpm = analyzer.currentRpm
    val isRunning = analyzer.isRunning

    var bladeCount by mutableIntStateOf(2)
    var selectedIds by mutableStateOf(emptySet<String>())
        private set
    var errorMessage by mutableStateOf<String?>(null)

    private var currentSession: MeasurementSession? = null
    private var timer: Job? = null

    fun start() {
        if (isRunning.value) return
        analyzer.bladeCount = bladeCount
        try {
            analyzer.start()
        } catch (e: Exception) {
            errorMessage = "Microphone error: ${e.message}"
            return
        }
        currentSession = MeasurementSession()

        // Sample once per second from the latest FFT result
        timer = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val rpm = analyzer.currentRpm.value
                if (rpm > 0) {
                    currentSession = currentSession?.let { it.copy(readings = it.readings + RpmReading(rpm = rpm)) }
                }
            }
        }
    }

    fun stop() {
        if (!isRunning.value) return
        analyzer.stop()
        timer?.cancel()
        timer = null
        currentSession?.takeIf { it.readings.isNotEmpty() }?.let(store::addSession)
        currentSession = null
    }

    fun toggleSelection(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun deleteSelected() {
        store.deleteSessions(selectedIds)
        selectedIds = emptySet()
    }

    override fun onCleared() {
        stop()
    }
}
