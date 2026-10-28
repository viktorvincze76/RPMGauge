package com.rpmgauge.app

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class RpmReading(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val rpm: Double,
)

@Serializable
data class MeasurementSession(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val readings: List<RpmReading> = emptyList(),
) {
    val averageRpm: Double
        get() = if (readings.isEmpty()) 0.0 else readings.sumOf { it.rpm } / readings.size

    val maxRpm: Double
        get() = readings.maxOfOrNull { it.rpm } ?: 0.0

    val minRpm: Double
        get() = readings.minOfOrNull { it.rpm } ?: 0.0
}
