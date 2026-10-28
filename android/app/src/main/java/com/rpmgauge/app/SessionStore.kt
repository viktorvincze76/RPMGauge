package com.rpmgauge.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SessionStore(context: Context) {
    private val file = File(context.filesDir, "sessions.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _sessions = MutableStateFlow(load())
    val sessions: StateFlow<List<MeasurementSession>> = _sessions.asStateFlow()

    fun addSession(session: MeasurementSession) {
        _sessions.value = listOf(session) + _sessions.value
        save()
    }

    fun deleteSessions(ids: Set<String>) {
        _sessions.value = _sessions.value.filterNot { it.id in ids }
        save()
    }

    private fun save() {
        try {
            // Write to a temp file and rename so a crash never leaves a half-written file
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(_sessions.value))
            tmp.renameTo(file)
        } catch (e: Exception) {
            Log.e("SessionStore", "save error", e)
        }
    }

    private fun load(): List<MeasurementSession> = try {
        if (file.exists()) json.decodeFromString(file.readText()) else emptyList()
    } catch (e: Exception) {
        Log.e("SessionStore", "load error", e)
        emptyList()
    }
}
