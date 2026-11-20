package com.example.model

import java.util.Locale

/**
 * Simple API-24-compatible representation of a time of day.
 */
data class ClockTime(
    val hour: Int,
    val minute: Int,
    val second: Int = 0
) : Comparable<ClockTime> {
    val totalMinutes: Int get() = hour * 60 + minute

    fun formattedHm(): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    fun formattedHms(): String = String.format(Locale.US, "%02d:%02d:%02d", hour, minute, second)

    override fun compareTo(other: ClockTime): Int {
        val minDiff = totalMinutes.compareTo(other.totalMinutes)
        return if (minDiff != 0) minDiff else second.compareTo(other.second)
    }
}

enum class SchoolWeekday(val dayIndex: Int, val polishName: String, val shortPolish: String) {
    MONDAY(0, "Poniedziałek", "Pn"),
    TUESDAY(1, "Wtorek", "Wt"),
    WEDNESDAY(2, "Środa", "Śr"),
    THURSDAY(3, "Czwartek", "Cz"),
    FRIDAY(4, "Piątek", "Pt"),
    SATURDAY(5, "Sobota", "Sb"),
    SUNDAY(6, "Niedziela", "Nd");

    val isSchoolDay: Boolean get() = dayIndex in 0..4
}

data class PeriodTime(
    val number: Int,
    val start: ClockTime,
    val end: ClockTime
) {
    val formattedRange: String get() = "${start.formattedHm()}–${end.formattedHm()}"
}

data class RoomLesson(
    val periodNumber: Int,
    val timeRange: PeriodTime,
    val roomName: String,
    val className: String,
    val subject: String,
    val teacher: String,
    val isSubstitutionAdded: Boolean = false,
    val substitutionNote: String? = null
)

data class CancelledLesson(
    val periodNumber: Int,
    val timeRange: PeriodTime?,
    val roomName: String,
    val className: String,
    val subject: String,
    val reason: String
)

enum class SubstitutionKind(val label: String) {
    CANCELLED_DISMISSED("Zwolniona / Odwołana"),
    MOVED_PERIOD("Przeniesiona na inną godzinę"),
    ROOM_CHANGED("Zmiana sali"),
    TEACHER_REPLACED("Zastępstwo nauczyciela"),
    OTHER("Inna zmiana")
}

data class SubstitutionEntry(
    val id: String,
    val dateText: String? = null,
    val periodNumber: Int? = null,
    val targetPeriodNumber: Int? = null,
    val timeText: String? = null,
    val className: String? = null,
    val teacherAbsent: String? = null,
    val substituteTeacher: String? = null,
    val subject: String? = null,
    val originalRoom: String? = null,
    val newRoom: String? = null,
    val kind: SubstitutionKind,
    val rawText: String,
    val impactSummary: String? = null
)

enum class RoomAvailabilityStatus {
    FREE_FOR_TODAY,
    FREE_AFTER_NEXT,
    OCCUPIED_LATER
}

data class RoomDaySummary(
    val roomName: String,
    val roomTitle: String,
    val status: RoomAvailabilityStatus,
    val freeFromTime: ClockTime?,
    val freeAfterPeriod: Int?,
    val nextLessonEnding: RoomLesson?,
    val activeLessons: List<RoomLesson>,
    val cancelledLessons: List<CancelledLesson>,
    val substitutionNotes: List<String>,
    val becameFreeEarlier: Boolean,
    val becameFreeLater: Boolean,
    val isCleanedToday: Boolean = false
)

data class ParsedTimetable(
    val rooms: Map<String, RoomSchedule>,
    val periodTimes: List<PeriodTime>,
    val generatedInfo: String? = null
)

data class RoomSchedule(
    val roomCode: String,
    val fullTitle: String,
    val lessonsByWeekday: Map<Int, List<RoomLesson>>
)

data class ParsedSubstitutions(
    val entries: List<SubstitutionEntry>,
    val headerDateText: String? = null,
    val matchesToday: Boolean = true
)

data class CachedSubstitutionsDay(
    val isoDate: String,
    val displayDateText: String? = null,
    val entries: List<SubstitutionEntry> = emptyList(),
    val savedAtText: String? = null
)

data class ParsedSubstitutionsByDate(
    val entriesByDate: Map<String, List<SubstitutionEntry>>,
    val displayDateByIso: Map<String, String>,
    val detectedDatesIso: List<String>,
    val headerDateText: String?,
    val unassociatedEntries: List<SubstitutionEntry> = emptyList()
)

data class BellStatus(
    val headline: String,
    val detail: String,
    val activePeriod: PeriodTime?,
    val nextPeriod: PeriodTime?,
    val isBreak: Boolean,
    val isAfterSchool: Boolean
)

data class FloorLevelConfig(
    val id: String,
    val name: String,
    val rules: List<String>
)

object FloorLevelMatcher {
    private val rangeRegex = Regex("""^\s*(\d+)\s*[-–]\s*(\d+)\s*$""")
    private val leadingNumberRegex = Regex("""^(\d+)""")

    fun matchesAnyRule(roomName: String, rules: List<String>): Boolean {
        val cleanRoom = com.example.util.TimeHelper.normalizeRoomCode(roomName).trim()
        if (cleanRoom.isEmpty()) return false
        return rules.any { rule -> matchesSingleRule(cleanRoom, rule) }
    }

    fun matchesSingleRule(cleanRoom: String, rawRule: String): Boolean {
        val rule = rawRule.trim()
        if (rule.isEmpty()) return false

        // 1. Numeric range like "1-9" or "10-29"
        val rangeMatch = rangeRegex.matchEntire(rule)
        if (rangeMatch != null) {
            val min = rangeMatch.groupValues[1].toIntOrNull() ?: return false
            val max = rangeMatch.groupValues[2].toIntOrNull() ?: return false
            // Do not match workshop rooms like "W1" against numeric ranges
            if (cleanRoom.firstOrNull()?.isLetter() == true) return false
            val roomNum = leadingNumberRegex.find(cleanRoom)?.groupValues?.get(1)?.toIntOrNull()
                ?: return false
            return roomNum in minOf(min, max)..maxOf(min, max)
        }

        // 2. Wildcard prefix like "W*"
        if (rule.endsWith("*")) {
            val prefix = rule.dropLast(1).trim()
            return prefix.isNotEmpty() && cleanRoom.startsWith(prefix, ignoreCase = true)
        }

        // 3. Exact room code match (e.g., "14", "104a", "W1", "Aula")
        return cleanRoom.equals(rule, ignoreCase = true)
    }
}

