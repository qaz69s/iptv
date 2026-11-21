package com.streambox.player.core.parser.xmltv

import java.util.Locale

/**
 * XMLTV timestamps: `yyyyMMddHHmmss ±hhmm` (the offset optionally glued to the digits or written
 * `±hh:mm` / `±hh`, `Z`, `UTC` / `GMT`). Missing seconds, minutes or the whole time of day count as
 * zero; a missing offset means UTC. The result is UTC epoch milliseconds.
 *
 * The conversion is plain calendar arithmetic (no `java.time`, which needs API 26 on Android), and
 * it only ever uses the offset written in the value: daylight-saving rules never enter into it.
 */
object XmltvTime {
    private const val MINUTES_PER_HOUR = 60
    private const val MILLIS_PER_SECOND = 1000L
    private const val OFFSET_HOURS_ONLY = 2
    private const val OFFSET_HOURS_AND_MINUTES = 4
    private const val SECONDS_PER_DAY = 86_400L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MAX_OFFSET_MINUTES = 18 * 60
    private const val MAX_HOUR = 23
    private const val MAX_MINUTE = 59
    private const val MAX_SECOND = 60 // a leap second is accepted and rolls over
    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2999
    private const val MONTHS = 12
    private val DIGIT_COUNTS = setOf(8, 10, 12, 14)
    private val UTC_NAMES = setOf("Z", "UTC", "GMT")

    /** Epoch milliseconds, or null when [value] is not a valid XMLTV timestamp. */
    fun parse(value: String): Long? {
        val trimmed = value.trim()
        var end = 0
        while (end < trimmed.length && trimmed[end] in '0'..'9') end++
        val digits = trimmed.substring(0, end)
        val offsetMinutes = parseOffset(trimmed.substring(end).trim())
        return if (digits.length in DIGIT_COUNTS && offsetMinutes != null) {
            localSeconds(digits)?.let { (it - offsetMinutes * SECONDS_PER_MINUTE) * MILLIS_PER_SECOND }
        } else {
            null
        }
    }

    /** Offset east of UTC in minutes; null when malformed. */
    internal fun parseOffset(text: String): Int? {
        val upper = text.uppercase()
        return when {
            upper.isEmpty() || upper in UTC_NAMES -> 0
            upper[0] != '+' && upper[0] != '-' -> null
            else -> signedOffset(if (upper[0] == '-') -1 else 1, upper.substring(1).replace(":", ""))
        }
    }

    private fun signedOffset(sign: Int, body: String): Int? {
        val lengthOk = body.length == OFFSET_HOURS_ONLY || body.length == OFFSET_HOURS_AND_MINUTES
        if (!lengthOk || body.any { it !in '0'..'9' }) return null
        val hours = body.substring(0, OFFSET_HOURS_ONLY).toInt()
        val minutes = if (body.length == OFFSET_HOURS_AND_MINUTES) body.substring(OFFSET_HOURS_ONLY).toInt() else 0
        val total = hours * MINUTES_PER_HOUR + minutes
        return if (minutes <= MAX_MINUTE && total <= MAX_OFFSET_MINUTES) sign * total else null
    }

    /** Seconds since the epoch of the wall-clock time in [digits], read as UTC. */
    @Suppress("MagicNumber") // Field positions of yyyyMMddHHmmss.
    private fun localSeconds(digits: String): Long? {
        fun field(from: Int): Int = if (digits.length >= from + 2) digits.substring(from, from + 2).toInt() else 0
        val year = digits.substring(0, 4).toInt()
        val month = digits.substring(4, 6).toInt()
        val day = digits.substring(6, 8).toInt()
        val hour = field(8)
        val minute = field(10)
        val second = field(12)
        val valid = year in MIN_YEAR..MAX_YEAR && month in 1..MONTHS && day in 1..daysInMonth(year, month) &&
            hour <= MAX_HOUR && minute <= MAX_MINUTE && second <= MAX_SECOND
        if (!valid) return null
        return daysFromCivil(year, month, day) * SECONDS_PER_DAY + hour * SECONDS_PER_HOUR +
            minute * SECONDS_PER_MINUTE + second
    }

    @Suppress("MagicNumber") // Gregorian calendar rules.
    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /** Days since 1970-01-01 of a proleptic Gregorian date (H. Hinnant's days_from_civil). */
    @Suppress("MagicNumber") // The published algorithm's constants.
    internal fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yearOfEra = y - era * 400
        val dayOfYear = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097L + dayOfEra - 719_468L
    }

    /** Formats epoch milliseconds as `yyyyMMddHHmmss +0000` (used by tests and generated samples). */
    @Suppress("MagicNumber") // Inverse of daysFromCivil (H. Hinnant's civil_from_days).
    fun format(epochMillis: Long): String {
        val totalSeconds = Math.floorDiv(epochMillis, 1000L)
        val days = Math.floorDiv(totalSeconds, SECONDS_PER_DAY)
        val secondOfDay = Math.floorMod(totalSeconds, SECONDS_PER_DAY)
        val z = days + 719_468L
        val era = Math.floorDiv(z, 146_097L)
        val dayOfEra = z - era * 146_097L
        val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val mp = (5 * dayOfYear + 2) / 153
        val day = dayOfYear - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
        val hour = secondOfDay / SECONDS_PER_HOUR
        val minute = secondOfDay % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val second = secondOfDay % SECONDS_PER_MINUTE
        return String.format(Locale.ROOT, "%04d%02d%02d%02d%02d%02d +0000", year, month, day, hour, minute, second)
    }
}
