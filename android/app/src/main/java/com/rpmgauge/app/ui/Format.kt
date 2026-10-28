package com.rpmgauge.app.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatRpm(rpm: Double): String = String.format(Locale.getDefault(), "%.0f", rpm)

fun formatDateTime(millis: Long, dateStyle: Int, timeStyle: Int): String =
    DateFormat.getDateTimeInstance(dateStyle, timeStyle).format(Date(millis))

fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(millis))
