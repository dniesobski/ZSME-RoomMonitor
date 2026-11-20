package com.example.data

import com.example.model.BellStatus
import com.example.model.CancelledLesson
import com.example.model.ClockTime
import com.example.model.ParsedSubstitutions
import com.example.model.ParsedTimetable
import com.example.model.PeriodTime
import com.example.model.RoomAvailabilityStatus
import com.example.model.RoomDaySummary
import com.example.model.RoomLesson
import com.example.model.SubstitutionEntry
import com.example.model.SubstitutionKind
import com.example.util.SchoolDateInfo
import com.example.util.TimeHelper

data class AnalyzedSchedule(
    val bellStatus: BellStatus,
    val rooms: List<RoomDaySummary>,
    val appliedSubstitutionsCount: Int
)

object ScheduleAnalyzer {

    fun analyze(
        timetable: ParsedTimetable,
        substitutions: ParsedSubstitutions,
        dateInfo: SchoolDateInfo,
        nowTime: ClockTime,
        cleanedRoomsToday: Set<String>
    ): AnalyzedSchedule {
        val periods = timetable.periodTimes.ifEmpty { TimeHelper.defaultPeriods }
            .sortedBy { it.number }
        val bellStatus = computeBellStatus(dateInfo, nowTime, periods)

        // Mutable working map: roomCode -> mutable list of RoomLesson
        val workingLessons = mutableMapOf<String, MutableList<RoomLesson>>()
        val roomTitles = mutableMapOf<String, String>()
        val baseLastPeriodByRoom = mutableMapOf<String, Int?>()

        val weekdayIdx = dateInfo.weekday.dayIndex
        for ((roomCode, schedule) in timetable.rooms) {
            roomTitles[roomCode] = schedule.fullTitle
            val todayLessons = if (dateInfo.weekday.isSchoolDay) {
                schedule.lessonsByWeekday[weekdayIdx].orEmpty()
            } else {
                emptyList()
            }
            workingLessons[roomCode] = todayLessons.toMutableList()
            baseLastPeriodByRoom[roomCode] = todayLessons.maxOfOrNull { it.periodNumber }
        }

        val cancelledByRoom = mutableMapOf<String, MutableList<CancelledLesson>>()
        val notesByRoom = mutableMapOf<String, MutableList<String>>()
        var appliedSubs = 0

        if (dateInfo.weekday.isSchoolDay && substitutions.matchesToday) {
            for (sub in substitutions.entries) {
                val applied = applySubstitution(
                    sub = sub,
                    periods = periods,
                    workingLessons = workingLessons,
                    roomTitles = roomTitles,
                    cancelledByRoom = cancelledByRoom,
                    notesByRoom = notesByRoom
                )
                if (applied) appliedSubs++
            }
        }

        val nowMinutes = nowTime.totalMinutes
        val boundaryPeriod = when {
            !dateInfo.weekday.isSchoolDay -> -1
            bellStatus.activePeriod != null -> bellStatus.activePeriod.number + 1
            bellStatus.nextPeriod != null -> bellStatus.nextPeriod.number
            else -> -1
        }

        val summaries = workingLessons.keys
            .sortedWith(TimeHelper.roomNameComparator)
            .map { roomCode ->
                val active = workingLessons[roomCode].orEmpty().sortedBy { it.periodNumber }
                val cancelled = cancelledByRoom[roomCode].orEmpty().sortedBy { it.periodNumber }
                val notes = notesByRoom[roomCode].orEmpty().distinct()

                val baseLast = baseLastPeriodByRoom[roomCode]
                val currentLast = active.maxOfOrNull { it.periodNumber }

                val becameFreeEarlier = when {
                    baseLast == null -> false
                    currentLast == null -> true
                    else -> currentLast < baseLast
                }
                val becameFreeLater = when {
                    currentLast == null -> false
                    baseLast == null -> true
                    else -> currentLast > baseLast
                }

                val remaining = if (!dateInfo.weekday.isSchoolDay) {
                    emptyList()
                } else {
                    active.filter { it.timeRange.end.totalMinutes > nowMinutes }
                }

                val lastActive = active.lastOrNull()
                val status: RoomAvailabilityStatus
                val freeFromTime: ClockTime?
                val freeAfterPeriod: Int?
                val nextEnding: RoomLesson?

                if (remaining.isEmpty()) {
                    status = RoomAvailabilityStatus.FREE_FOR_TODAY
                    freeFromTime = lastActive?.timeRange?.end
                    freeAfterPeriod = lastActive?.periodNumber
                    nextEnding = null
                } else {
                    val lastRemaining = remaining.maxBy { it.periodNumber }
                    freeFromTime = lastRemaining.timeRange.end
                    freeAfterPeriod = lastRemaining.periodNumber
                    nextEnding = remaining.first()
                    status = if (boundaryPeriod >= 0 && lastRemaining.periodNumber <= boundaryPeriod) {
                        RoomAvailabilityStatus.FREE_AFTER_NEXT
                    } else {
                        RoomAvailabilityStatus.OCCUPIED_LATER
                    }
                }

                RoomDaySummary(
                    roomName = roomCode,
                    roomTitle = roomTitles[roomCode] ?: "Sala $roomCode",
                    status = status,
                    freeFromTime = freeFromTime,
                    freeAfterPeriod = freeAfterPeriod,
                    nextLessonEnding = nextEnding,
                    activeLessons = active,
                    cancelledLessons = cancelled,
                    substitutionNotes = notes,
                    becameFreeEarlier = becameFreeEarlier,
                    becameFreeLater = becameFreeLater,
                    isCleanedToday = cleanedRoomsToday.contains(roomCode)
                )
            }

        return AnalyzedSchedule(
            bellStatus = bellStatus,
            rooms = summaries,
            appliedSubstitutionsCount = appliedSubs
        )
    }

    private fun applySubstitution(
        sub: SubstitutionEntry,
        periods: List<PeriodTime>,
        workingLessons: MutableMap<String, MutableList<RoomLesson>>,
        roomTitles: MutableMap<String, String>,
        cancelledByRoom: MutableMap<String, MutableList<CancelledLesson>>,
        notesByRoom: MutableMap<String, MutableList<String>>
    ): Boolean {
        val periodNr = sub.periodNumber
        val matchedRooms = findMatchingRooms(sub, periodNr, workingLessons)

        return when (sub.kind) {
            SubstitutionKind.CANCELLED_DISMISSED -> {
                var changed = false
                for (roomCode in matchedRooms) {
                    val list = workingLessons[roomCode] ?: continue
                    val toCancel = list.filter { lesson ->
                        matchesLessonForSub(lesson, sub, periodNr)
                    }
                    if (toCancel.isNotEmpty()) {
                        list.removeAll(toCancel)
                        val cancelledList = cancelledByRoom.getOrPut(roomCode) { mutableListOf() }
                        for (c in toCancel) {
                            cancelledList.add(
                                CancelledLesson(
                                    periodNumber = c.periodNumber,
                                    timeRange = c.timeRange,
                                    roomName = roomCode,
                                    className = c.className,
                                    subject = c.subject,
                                    reason = sub.impactSummary ?: "Odwołana / zwolniona do domu"
                                )
                            )
                        }
                        notesByRoom.getOrPut(roomCode) { mutableListOf() }
                            .add("Lekcja ${toCancel.first().periodNumber}. odwołana/zwolniona")
                        changed = true
                    }
                }
                changed
            }

            SubstitutionKind.MOVED_PERIOD -> {
                val targetPeriodNr = sub.targetPeriodNumber ?: return false
                val targetPeriodTime = periods.find { it.number == targetPeriodNr }
                    ?: TimeHelper.defaultPeriods.find { it.number == targetPeriodNr }
                    ?: return false

                var movedLessonTemplate: RoomLesson? = null
                var sourceRoomCode: String? = null

                for (roomCode in matchedRooms) {
                    val list = workingLessons[roomCode] ?: continue
                    val match = list.firstOrNull { matchesLessonForSub(it, sub, periodNr) }
                    if (match != null) {
                        list.remove(match)
                        movedLessonTemplate = match
                        sourceRoomCode = roomCode
                        cancelledByRoom.getOrPut(roomCode) { mutableListOf() }.add(
                            CancelledLesson(
                                periodNumber = match.periodNumber,
                                timeRange = match.timeRange,
                                roomName = roomCode,
                                className = match.className,
                                subject = match.subject,
                                reason = "Przeniesiona na ${targetPeriodNr}. lekcję"
                            )
                        )
                        break
                    }
                }

                val destRoom = sub.newRoom?.let { resolveExistingRoomKey(it, workingLessons.keys) ?: it }
                    ?: sourceRoomCode
                    ?: sub.originalRoom?.let { resolveExistingRoomKey(it, workingLessons.keys) ?: it }
                    ?: return false

                if (!workingLessons.containsKey(destRoom)) {
                    workingLessons[destRoom] = mutableListOf()
                    roomTitles[destRoom] = "Sala $destRoom"
                }

                val newLesson = RoomLesson(
                    periodNumber = targetPeriodNr,
                    timeRange = targetPeriodTime,
                    roomName = destRoom,
                    className = movedLessonTemplate?.className ?: sub.className.orEmpty(),
                    subject = movedLessonTemplate?.subject ?: sub.subject ?: "Zajęcia (przeniesione)",
                    teacher = movedLessonTemplate?.teacher ?: sub.substituteTeacher ?: sub.teacherAbsent.orEmpty(),
                    isSubstitutionAdded = true,
                    substitutionNote = "Przeniesiona z ${periodNr ?: "?"} na ${targetPeriodNr}. lekcję"
                )
                workingLessons[destRoom]?.add(newLesson)
                notesByRoom.getOrPut(destRoom) { mutableListOf() }
                    .add("Lekcja przeniesiona na ${targetPeriodNr}. godz.")
                if (sourceRoomCode != null && sourceRoomCode != destRoom) {
                    notesByRoom.getOrPut(sourceRoomCode) { mutableListOf() }
                        .add("Lekcja ${periodNr ?: ""}. przeniesiona do s. $destRoom")
                }
                true
            }

            SubstitutionKind.ROOM_CHANGED -> {
                val targetRoomRaw = sub.newRoom ?: return false
                val targetRoom = resolveExistingRoomKey(targetRoomRaw, workingLessons.keys) ?: targetRoomRaw
                var movedLesson: RoomLesson? = null
                var oldRoom: String? = null

                for (roomCode in matchedRooms) {
                    val list = workingLessons[roomCode] ?: continue
                    val match = list.firstOrNull { matchesLessonForSub(it, sub, periodNr) }
                    if (match != null && roomCode != targetRoom) {
                        list.remove(match)
                        movedLesson = match
                        oldRoom = roomCode
                        cancelledByRoom.getOrPut(roomCode) { mutableListOf() }.add(
                            CancelledLesson(
                                periodNumber = match.periodNumber,
                                timeRange = match.timeRange,
                                roomName = roomCode,
                                className = match.className,
                                subject = match.subject,
                                reason = "Przeniesiona do sali $targetRoom"
                            )
                        )
                        notesByRoom.getOrPut(roomCode) { mutableListOf() }
                            .add("Lekcja ${match.periodNumber}. przeniesiona do s. $targetRoom")
                        break
                    }
                }

                val targetPeriodNr = periodNr ?: movedLesson?.periodNumber ?: return false
                val periodTime = movedLesson?.timeRange
                    ?: periods.find { it.number == targetPeriodNr }
                    ?: TimeHelper.defaultPeriods.find { it.number == targetPeriodNr }
                    ?: return false

                if (!workingLessons.containsKey(targetRoom)) {
                    workingLessons[targetRoom] = mutableListOf()
                    roomTitles[targetRoom] = "Sala $targetRoom"
                }

                workingLessons[targetRoom]?.add(
                    RoomLesson(
                        periodNumber = targetPeriodNr,
                        timeRange = periodTime,
                        roomName = targetRoom,
                        className = movedLesson?.className ?: sub.className.orEmpty(),
                        subject = movedLesson?.subject ?: sub.subject ?: "Zmiana sali",
                        teacher = sub.substituteTeacher ?: movedLesson?.teacher ?: sub.teacherAbsent.orEmpty(),
                        isSubstitutionAdded = true,
                        substitutionNote = oldRoom?.let { "Przeniesiona z sali $it" } ?: "Zmiana sali"
                    )
                )
                notesByRoom.getOrPut(targetRoom) { mutableListOf() }
                    .add("Dodana lekcja ${targetPeriodNr}. (zmiana sali)")
                true
            }

            SubstitutionKind.TEACHER_REPLACED, SubstitutionKind.OTHER -> {
                var annotated = false
                for (roomCode in matchedRooms) {
                    val list = workingLessons[roomCode] ?: continue
                    val idx = list.indexOfFirst { matchesLessonForSub(it, sub, periodNr) }
                    if (idx >= 0) {
                        val existing = list[idx]
                        list[idx] = existing.copy(
                            teacher = sub.substituteTeacher ?: existing.teacher,
                            substitutionNote = sub.impactSummary
                        )
                        annotated = true
                    }
                }
                annotated
            }
        }
    }

    private fun findMatchingRooms(
        sub: SubstitutionEntry,
        periodNr: Int?,
        workingLessons: Map<String, List<RoomLesson>>
    ): List<String> {
        // 1. Direct room mention
        sub.originalRoom?.let { orig ->
            resolveExistingRoomKey(orig, workingLessons.keys)?.let { return listOf(it) }
        }

        // 2. Match by period + class name or absent teacher across today's lessons
        val matched = mutableListOf<String>()
        for ((roomCode, lessons) in workingLessons) {
            if (lessons.any { matchesLessonForSub(it, sub, periodNr) }) {
                matched.add(roomCode)
            }
        }
        return matched
    }

    private fun matchesLessonForSub(
        lesson: RoomLesson,
        sub: SubstitutionEntry,
        periodNr: Int?
    ): Boolean {
        if (periodNr != null && lesson.periodNumber != periodNr) return false

        val subClass = sub.className?.replace("\\s+".toRegex(), "")?.lowercase()
        val lessonClass = lesson.className.replace("\\s+".toRegex(), "").lowercase()
        if (!subClass.isNullOrBlank() && lessonClass.isNotBlank()) {
            if (lessonClass.contains(subClass) || subClass.contains(lessonClass)) {
                return true
            }
        }

        val subTeacher = sub.teacherAbsent?.trim()?.lowercase()
        val lessonTeacher = lesson.teacher.trim().lowercase()
        if (!subTeacher.isNullOrBlank() && lessonTeacher.isNotBlank()) {
            val teacherTokens = subTeacher.split(" ", ".").filter { it.length >= 2 }
            if (lessonTeacher == subTeacher ||
                (teacherTokens.isNotEmpty() && teacherTokens.any { lessonTeacher.contains(it) })
            ) {
                return true
            }
        }

        val subRoom = sub.originalRoom?.let { TimeHelper.normalizeRoomCode(it) }
        if (!subRoom.isNullOrBlank() && subRoom.equals(lesson.roomName, ignoreCase = true)) {
            return periodNr == null || lesson.periodNumber == periodNr
        }

        return false
    }

    private fun resolveExistingRoomKey(candidate: String, keys: Set<String>): String? {
        val norm = TimeHelper.normalizeRoomCode(candidate)
        return keys.firstOrNull { it.equals(norm, ignoreCase = true) }
            ?: keys.firstOrNull { it.equals(candidate.trim(), ignoreCase = true) }
    }

    fun computeBellStatus(
        dateInfo: SchoolDateInfo,
        now: ClockTime,
        periods: List<PeriodTime>
    ): BellStatus {
        if (!dateInfo.weekday.isSchoolDay) {
            return BellStatus(
                headline = "${dateInfo.weekday.polishName} — dzień wolny",
                detail = "Wszystkie sale są dziś wolne do sprzątania",
                activePeriod = null,
                nextPeriod = null,
                isBreak = false,
                isAfterSchool = true
            )
        }

        val nowMin = now.totalMinutes
        val active = periods.firstOrNull { nowMin in it.start.totalMinutes until it.end.totalMinutes }
        val next = periods.firstOrNull { it.start.totalMinutes > nowMin }

        return when {
            active != null -> {
                val nextAfter = periods.firstOrNull { it.number > active.number }
                val minsLeft = active.end.totalMinutes - nowMin
                BellStatus(
                    headline = "Trwa ${active.number}. lekcja (${active.formattedRange})",
                    detail = "Koniec za $minsLeft min • o ${active.end.formattedHm()}",
                    activePeriod = active,
                    nextPeriod = nextAfter,
                    isBreak = false,
                    isAfterSchool = false
                )
            }
            next != null -> {
                val minsToNext = next.start.totalMinutes - nowMin
                BellStatus(
                    headline = "Przerwa przed ${next.number}. lekcją",
                    detail = "Następna lekcja o ${next.start.formattedHm()} (za $minsToNext min)",
                    activePeriod = null,
                    nextPeriod = next,
                    isBreak = true,
                    isAfterSchool = false
                )
            }
            else -> {
                BellStatus(
                    headline = "Po lekcjach",
                    detail = "Wszystkie zajęcia na dziś zakończone",
                    activePeriod = null,
                    nextPeriod = null,
                    isBreak = false,
                    isAfterSchool = true
                )
            }
        }
    }
}
