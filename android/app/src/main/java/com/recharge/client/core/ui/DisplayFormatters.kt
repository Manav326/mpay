package com.recharge.client.core.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val displayZone: ZoneId = ZoneId.of("Asia/Kolkata")
private val exactDateTimeFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm:ss a, EEEE", Locale.ENGLISH)
private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm a, EEEE", Locale.ENGLISH)
private val shortDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

fun formatExactTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    return runCatching { Instant.parse(value).atZone(displayZone).format(exactDateTimeFormatter) }.getOrElse { value }
}

fun formatReadableTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    return runCatching { Instant.parse(value).atZone(displayZone).format(dateTimeFormatter) }.getOrElse { value }
}

fun formatPeriod(from: String?, to: String?): String {
    if (from.isNullOrBlank() || to.isNullOrBlank()) return "—"
    return runCatching {
        val start = Instant.parse(from).atZone(displayZone)
        val end = Instant.parse(to).atZone(displayZone)
        "${start.format(shortDateFormatter)} → ${end.format(shortDateFormatter)}"
    }.getOrElse { "—" }
}

fun formatAsOf(value: String?): String = formatReadableTimestamp(value)
