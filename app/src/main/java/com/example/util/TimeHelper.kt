package com.example.util

import com.example.model.ClockTime
import com.example.model.PeriodTime
import com.example.model.SchoolWeekday
import java.util.Calendar
import java.util.Locale

data class SchoolDateInfo(
    val year: Int,
    val month: Int, // 1..12
    val dayOfMonth: Int,
    val weekday: SchoolWeekday
) {
    val isoKey: String
        get() = String.format(Locale.US, "%04d-%02d-%02d", year, month, dayOfMonth)

    val polishFullDate: String
        get() {
            val monthGenitive = when (month) {
                1 -> "stycznia"
                2 -> "lutego"
                3 -> "marca"
                4 -> "kwietnia"
                5 -> "maja"
                6 -> "czerwca"
                7 -> "lipca"
                8 -> "sierpnia"
                9 -> "września"
                10 -> "października"
                11 -> "listopada"
                12 -> "grudnia"
                else -> ""
            }
            return "${weekday.polishName}, $dayOfMonth $monthGenitive $year"
        }

    val dotDateCandidates: List<String>
        get() = listOf(
            String.format(Locale.US, "%02d.%02d.%04d", dayOfMonth, month, year),
            String.format(Locale.US, "%d.%02d.%04d", dayOfMonth, month, year),
            String.format(Locale.US, "%d.%d.%04d", dayOfMonth, month, year),
            String.format(Locale.US, "%02d.%02d.%02d", dayOfMonth, month, year % 100),
            String.format(Locale.US, "%04d-%02d-%02d", year, month, dayOfMonth)
        )
}

object TimeHelper {

    fun nowClockTime(calendar: Calendar = Calendar.getInstance()): ClockTime {
        return ClockTime(
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
            second = calendar.get(Calendar.SECOND)
        )
    }

    fun todayDateInfo(calendar: Calendar = Calendar.getInstance()): SchoolDateInfo {
        val weekday = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> SchoolWeekday.MONDAY
            Calendar.TUESDAY -> SchoolWeekday.TUESDAY
            Calendar.WEDNESDAY -> SchoolWeekday.WEDNESDAY
            Calendar.THURSDAY -> SchoolWeekday.THURSDAY
            Calendar.FRIDAY -> SchoolWeekday.FRIDAY
            Calendar.SATURDAY -> SchoolWeekday.SATURDAY
            else -> SchoolWeekday.SUNDAY
        }
        return SchoolDateInfo(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
            weekday = weekday
        )
    }

    fun parseTimeRange(raw: String): Pair<ClockTime, ClockTime>? {
        val regex = Regex("""(\d{1,2})[:.](\d{2})\s*[-–—]\s*(\d{1,2})[:.](\d{2})""")
        val match = regex.find(raw) ?: return null
        val sh = match.groupValues[1].toIntOrNull() ?: return null
        val sm = match.groupValues[2].toIntOrNull() ?: return null
        val eh = match.groupValues[3].toIntOrNull() ?: return null
        val em = match.groupValues[4].toIntOrNull() ?: return null
        if (sh !in 0..23 || sm !in 0..59 || eh !in 0..23 || em !in 0..59) return null
        return ClockTime(sh, sm) to ClockTime(eh, em)
    }

    /**
     * Standard fallback period schedule used if the timetable HTML has no bell times.
     */
    val defaultPeriods: List<PeriodTime> = listOf(
        PeriodTime(0, ClockTime(7, 10), ClockTime(7, 55)),
        PeriodTime(1, ClockTime(8, 0), ClockTime(8, 45)),
        PeriodTime(2, ClockTime(8, 55), ClockTime(9, 40)),
        PeriodTime(3, ClockTime(9, 50), ClockTime(10, 35)),
        PeriodTime(4, ClockTime(10, 45), ClockTime(11, 30)),
        PeriodTime(5, ClockTime(11, 50), ClockTime(12, 35)),
        PeriodTime(6, ClockTime(12, 45), ClockTime(13, 30)),
        PeriodTime(7, ClockTime(13, 40), ClockTime(14, 25)),
        PeriodTime(8, ClockTime(14, 30), ClockTime(15, 15)),
        PeriodTime(9, ClockTime(15, 20), ClockTime(16, 5))
    )

    private val roomCodeRegex = Regex("""^(\d+)([A-Za-z]*)$""")

    /**
     * Natural alphanumeric sorting so room "2" comes before "10", "104" before "104a",
     * and named rooms ("Aula", "SG") come neatly after numbered rooms.
     */
    val roomNameComparator: Comparator<String> = Comparator { a, b ->
        val cleanA = normalizeRoomCode(a)
        val cleanB = normalizeRoomCode(b)
        val matchA = roomCodeRegex.matchEntire(cleanA)
        val matchB = roomCodeRegex.matchEntire(cleanB)
        when {
            matchA != null && matchB != null -> {
                val numCmp = matchA.groupValues[1].toInt().compareTo(matchB.groupValues[1].toInt())
                if (numCmp != 0) numCmp else matchA.groupValues[2].compareTo(matchB.groupValues[2], ignoreCase = true)
            }
            matchA != null -> -1
            matchB != null -> 1
            else -> cleanA.compareTo(cleanB, ignoreCase = true)
        }
    }

    fun normalizeRoomCode(raw: String): String {
        return raw.trim()
            .replace(Regex("""^(?:sala|s\.?|gab\.?|prac\.?)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+[-–].*$"""), "")
            .trim()
    }

    fun addDays(date: SchoolDateInfo, days: Int): SchoolDateInfo {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, date.year)
            set(Calendar.MONTH, date.month - 1)
            set(Calendar.DAY_OF_MONTH, date.dayOfMonth)
            add(Calendar.DAY_OF_MONTH, days)
        }
        return todayDateInfo(cal)
    }

    fun formatIsoToDotDate(isoDate: String): String {
        val parts = isoDate.split("-")
        if (parts.size != 3) return isoDate
        return "${parts[2]}.${parts[1]}.${parts[0]}"
    }

    fun describeCachedDateLabel(isoDate: String, today: SchoolDateInfo): String {
        val dotDate = formatIsoToDotDate(isoDate)
        if (isoDate == today.isoKey) return "Dziś ($dotDate)"
        val tomorrow = addDays(today, 1)
        if (isoDate == tomorrow.isoKey) return "Jutro ($dotDate)"
        for (offset in 2..7) {
            val candidate = addDays(today, offset)
            if (candidate.isoKey == isoDate) {
                return "${candidate.weekday.polishName} ($dotDate)"
            }
        }
        return dotDate
    }

    private val isoDateRegex = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val dmyDateRegex = Regex("""\b(\d{1,2})[./\-](\d{1,2})[./\-](\d{2,4})\b""")
    private val dmMonthWordRegex = Regex(
        """\b(\d{1,2})\s+(stycznia|lutego|marca|kwietnia|maja|czerwca|lipca|sierpnia|wrze[śs]nia|pa[źz]dziernika|listopada|grudnia)(?:\s+(\d{4}))?\b""",
        RegexOption.IGNORE_CASE
    )
    private val dayMonthShortRegex = Regex("""\b(\d{1,2})\.(\d{1,2})(?!\.\d)(?!\s*[-–:]\s*\d)\b""")

    private val polishMonthMap = mapOf(
        "stycznia" to 1,
        "lutego" to 2,
        "marca" to 3,
        "kwietnia" to 4,
        "maja" to 5,
        "czerwca" to 6,
        "lipca" to 7,
        "sierpnia" to 8,
        "września" to 9,
        "wrzesnia" to 9,
        "października" to 10,
        "pazdziernika" to 10,
        "listopada" to 11,
        "grudnia" to 12
    )

    private val weekdayAliases: List<Pair<SchoolWeekday, List<String>>> = listOf(
        SchoolWeekday.MONDAY to listOf("poniedziałek", "poniedziałku", "poniedzialek", "poniedzialku"),
        SchoolWeekday.TUESDAY to listOf("wtorek", "wtorku"),
        SchoolWeekday.WEDNESDAY to listOf("środa", "środę", "środy", "sroda", "srode", "srody"),
        SchoolWeekday.THURSDAY to listOf("czwartek", "czwartku"),
        SchoolWeekday.FRIDAY to listOf("piątek", "piątku", "piatek", "piatku"),
        SchoolWeekday.SATURDAY to listOf("sobota", "sobotę", "soboty", "sobote"),
        SchoolWeekday.SUNDAY to listOf("niedziela", "niedzielę", "niedzieli", "niedziele")
    )

    fun parseDateToIsoKey(
        rawText: String,
        referenceToday: SchoolDateInfo = todayDateInfo(),
        allowShortDayMonth: Boolean = false,
        allowWeekdayFallback: Boolean = false
    ): String? {
        isoDateRegex.find(rawText)?.let { m ->
            val y = m.groupValues[1].toIntOrNull()
            val mo = m.groupValues[2].toIntOrNull()
            val d = m.groupValues[3].toIntOrNull()
            formatValidatedIso(y, mo, d)?.let { return it }
        }

        dmyDateRegex.find(rawText)?.let { m ->
            val d = m.groupValues[1].toIntOrNull()
            val mo = m.groupValues[2].toIntOrNull()
            val rawY = m.groupValues[3].toIntOrNull()
            val y = if (rawY != null && rawY < 100) 2000 + rawY else rawY
            formatValidatedIso(y, mo, d)?.let { return it }
        }

        dmMonthWordRegex.find(rawText)?.let { m ->
            val d = m.groupValues[1].toIntOrNull()
            val mo = polishMonthMap[m.groupValues[2].lowercase(Locale.ROOT)]
            val y = m.groupValues[3].toIntOrNull() ?: inferYear(mo, referenceToday)
            formatValidatedIso(y, mo, d)?.let { return it }
        }

        if (allowShortDayMonth) {
            dayMonthShortRegex.find(rawText)?.let { m ->
                val d = m.groupValues[1].toIntOrNull()
                val mo = m.groupValues[2].toIntOrNull()
                val y = inferYear(mo, referenceToday)
                formatValidatedIso(y, mo, d)?.let { return it }
            }
        }

        if (allowWeekdayFallback) {
            val lower = rawText.lowercase(Locale.ROOT)
            val matchedWeekday = weekdayAliases.firstOrNull { (_, words) ->
                words.any { w -> Regex("""\b$w\b""").containsMatchIn(lower) }
            }?.first
            if (matchedWeekday != null) {
                for (offset in 0..6) {
                    val candidate = addDays(referenceToday, offset)
                    if (candidate.weekday == matchedWeekday) {
                        return candidate.isoKey
                    }
                }
            }
        }

        return null
    }

    private fun inferYear(month: Int?, referenceToday: SchoolDateInfo): Int? {
        if (month == null) return null
        return when {
            referenceToday.month == 12 && month == 1 -> referenceToday.year + 1
            referenceToday.month == 1 && month == 12 -> referenceToday.year - 1
            else -> referenceToday.year
        }
    }

    private fun formatValidatedIso(year: Int?, month: Int?, day: Int?): String? {
        if (year == null || month == null || day == null) return null
        if (year !in 2000..2100 || month !in 1..12 || day !in 1..31) return null
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }
}

