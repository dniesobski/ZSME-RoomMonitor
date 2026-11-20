package com.example.data

import android.content.Context
import com.example.model.CachedSubstitutionsDay
import com.example.model.ClockTime
import com.example.model.ParsedSubstitutionsByDate
import com.example.model.ParsedTimetable
import com.example.model.PeriodTime
import com.example.model.RoomLesson
import com.example.model.RoomSchedule
import com.example.model.SubstitutionEntry
import com.example.model.SubstitutionKind
import com.example.util.SchoolDateInfo
import com.example.util.TimeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ScheduleCacheData(
    val timetableUrl: String = "",
    val timetable: ParsedTimetable? = null,
    val timetableSavedAtText: String? = null,
    val timetableSavedDateIso: String? = null,
    val substitutionsUrl: String = "",
    val substitutionsByDate: Map<String, CachedSubstitutionsDay> = emptyMap()
) {
    fun substitutionsForDate(isoDate: String): CachedSubstitutionsDay? = substitutionsByDate[isoDate]

    fun futureSubstitutionDays(todayIso: String): List<CachedSubstitutionsDay> =
        substitutionsByDate.values
            .filter { it.isoDate > todayIso }
            .sortedBy { it.isoDate }
}

class ScheduleCacheStore(private val cacheFile: File) {

    constructor(context: Context) : this(File(context.filesDir, "schedule_cache_v1.json"))

    private val ioLock = Any()

    suspend fun loadAndPrune(
        timetableUrl: String,
        substitutionsUrl: String,
        today: SchoolDateInfo
    ): ScheduleCacheData = withContext(Dispatchers.IO) {
        synchronized(ioLock) {
            val raw = readInternal()
            val normalizedTimetableUrl = timetableUrl.trim()
            val normalizedSubsUrl = substitutionsUrl.trim()

            var modified = false
            val validTimetable = if (raw.timetableUrl == normalizedTimetableUrl) {
                raw.timetable
            } else {
                if (raw.timetable != null) modified = true
                null
            }

            val baseSubs = if (raw.substitutionsUrl == normalizedSubsUrl) {
                raw.substitutionsByDate
            } else {
                if (raw.substitutionsByDate.isNotEmpty()) modified = true
                emptyMap()
            }

            // Purge any substitution days strictly before today ("new day cleanup")
            val prunedSubs = baseSubs.filterKeys { iso ->
                val keep = iso >= today.isoKey
                if (!keep) modified = true
                keep
            }

            val cleaned = ScheduleCacheData(
                timetableUrl = if (validTimetable != null) raw.timetableUrl else normalizedTimetableUrl,
                timetable = validTimetable,
                timetableSavedAtText = if (validTimetable != null) raw.timetableSavedAtText else null,
                timetableSavedDateIso = if (validTimetable != null) raw.timetableSavedDateIso else null,
                substitutionsUrl = normalizedSubsUrl,
                substitutionsByDate = prunedSubs
            )

            if (modified) {
                writeInternal(cleaned)
            }
            cleaned
        }
    }

    suspend fun saveTimetable(
        timetableUrl: String,
        timetable: ParsedTimetable,
        savedAtText: String,
        today: SchoolDateInfo
    ): ScheduleCacheData = withContext(Dispatchers.IO) {
        synchronized(ioLock) {
            val current = readInternal()
            val prunedSubs = current.substitutionsByDate.filterKeys { it >= today.isoKey }
            val updated = current.copy(
                timetableUrl = timetableUrl.trim(),
                timetable = timetable,
                timetableSavedAtText = savedAtText,
                timetableSavedDateIso = today.isoKey,
                substitutionsByDate = prunedSubs
            )
            writeInternal(updated)
            updated
        }
    }

    suspend fun mergeSubstitutions(
        substitutionsUrl: String,
        parsed: ParsedSubstitutionsByDate,
        savedAtText: String,
        today: SchoolDateInfo
    ): ScheduleCacheData = withContext(Dispatchers.IO) {
        synchronized(ioLock) {
            val current = readInternal()
            val cleanUrl = substitutionsUrl.trim()
            val existingSubs = if (current.substitutionsUrl == cleanUrl) {
                current.substitutionsByDate.filterKeys { it >= today.isoKey }.toMutableMap()
            } else {
                mutableMapOf()
            }

            val todayIso = today.isoKey

            // Upsert every date present in the freshly fetched page that is today or in the future.
            // Crucially: if today is NOT in detectedDatesIso (because tv.php already moved to tomorrow),
            // today's previously cached substitutions remain intact in existingSubs!
            val datesToUpsert = (parsed.detectedDatesIso + parsed.entriesByDate.keys)
                .distinct()
                .filter { it >= todayIso }

            for (isoDate in datesToUpsert) {
                val entries = parsed.entriesByDate[isoDate].orEmpty()
                val display = parsed.displayDateByIso[isoDate] ?: TimeHelper.formatIsoToDotDate(isoDate)
                existingSubs[isoDate] = CachedSubstitutionsDay(
                    isoDate = isoDate,
                    displayDateText = display,
                    entries = entries,
                    savedAtText = savedAtText
                )
            }

            // Fallback: if the page had no recognizable date at all, store non-empty entries under today
            if (parsed.detectedDatesIso.isEmpty() && parsed.unassociatedEntries.isNotEmpty()) {
                existingSubs[todayIso] = CachedSubstitutionsDay(
                    isoDate = todayIso,
                    displayDateText = today.dotDateCandidates.first(),
                    entries = parsed.unassociatedEntries,
                    savedAtText = savedAtText
                )
            }

            val updated = current.copy(
                substitutionsUrl = cleanUrl,
                substitutionsByDate = existingSubs
            )
            writeInternal(updated)
            updated
        }
    }

    suspend fun pruneExpired(today: SchoolDateInfo): ScheduleCacheData = withContext(Dispatchers.IO) {
        synchronized(ioLock) {
            val current = readInternal()
            val prunedSubs = current.substitutionsByDate.filterKeys { it >= today.isoKey }
            if (prunedSubs.size != current.substitutionsByDate.size) {
                val updated = current.copy(substitutionsByDate = prunedSubs)
                writeInternal(updated)
                updated
            } else {
                current
            }
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        synchronized(ioLock) {
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }
    }

    private fun readInternal(): ScheduleCacheData {
        if (!cacheFile.exists()) return ScheduleCacheData()
        return runCatching {
            val text = cacheFile.readText(Charsets.UTF_8)
            if (text.isBlank()) return ScheduleCacheData()
            decodeCache(JSONObject(text))
        }.getOrDefault(ScheduleCacheData())
    }

    private fun writeInternal(data: ScheduleCacheData) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val json = encodeCache(data).toString()
            val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            tempFile.writeText(json, Charsets.UTF_8)
            if (!tempFile.renameTo(cacheFile)) {
                cacheFile.writeText(json, Charsets.UTF_8)
                tempFile.delete()
            }
        }
    }

    private fun encodeCache(data: ScheduleCacheData): JSONObject {
        val root = JSONObject()
        root.put("timetableUrl", data.timetableUrl)
        data.timetableSavedAtText?.let { root.put("timetableSavedAtText", it) }
        data.timetableSavedDateIso?.let { root.put("timetableSavedDateIso", it) }
        data.timetable?.let { root.put("timetable", encodeTimetable(it)) }

        root.put("substitutionsUrl", data.substitutionsUrl)
        val subsArr = JSONArray()
        data.substitutionsByDate.values.sortedBy { it.isoDate }.forEach { day ->
            subsArr.put(encodeSubsDay(day))
        }
        root.put("substitutionsByDate", subsArr)
        return root
    }

    private fun decodeCache(root: JSONObject): ScheduleCacheData {
        val timetableUrl = root.optNullableString("timetableUrl").orEmpty()
        val timetableSavedAtText = root.optNullableString("timetableSavedAtText")
        val timetableSavedDateIso = root.optNullableString("timetableSavedDateIso")
        val timetable = root.optJSONObject("timetable")?.let { decodeTimetable(it) }

        val substitutionsUrl = root.optNullableString("substitutionsUrl").orEmpty()
        val subsMap = linkedMapOf<String, CachedSubstitutionsDay>()
        val subsArr = root.optJSONArray("substitutionsByDate") ?: JSONArray()
        for (i in 0 until subsArr.length()) {
            val dayObj = subsArr.optJSONObject(i) ?: continue
            val day = decodeSubsDay(dayObj) ?: continue
            subsMap[day.isoDate] = day
        }

        return ScheduleCacheData(
            timetableUrl = timetableUrl,
            timetable = timetable,
            timetableSavedAtText = timetableSavedAtText,
            timetableSavedDateIso = timetableSavedDateIso,
            substitutionsUrl = substitutionsUrl,
            substitutionsByDate = subsMap
        )
    }

    private fun encodeTimetable(tt: ParsedTimetable): JSONObject {
        val obj = JSONObject()
        tt.generatedInfo?.let { obj.put("generatedInfo", it) }

        val periodsArr = JSONArray()
        tt.periodTimes.forEach { periodsArr.put(encodePeriod(it)) }
        obj.put("periodTimes", periodsArr)

        val roomsArr = JSONArray()
        tt.rooms.values.forEach { room ->
            val rObj = JSONObject()
            rObj.put("roomCode", room.roomCode)
            rObj.put("fullTitle", room.fullTitle)
            val daysObj = JSONObject()
            room.lessonsByWeekday.forEach { (weekdayIdx, lessons) ->
                val lArr = JSONArray()
                lessons.forEach { lArr.put(encodeLesson(it)) }
                daysObj.put(weekdayIdx.toString(), lArr)
            }
            rObj.put("lessonsByWeekday", daysObj)
            roomsArr.put(rObj)
        }
        obj.put("rooms", roomsArr)
        return obj
    }

    private fun decodeTimetable(obj: JSONObject): ParsedTimetable? {
        val generatedInfo = obj.optNullableString("generatedInfo")
        val periodsArr = obj.optJSONArray("periodTimes") ?: JSONArray()
        val periods = mutableListOf<PeriodTime>()
        for (i in 0 until periodsArr.length()) {
            periodsArr.optJSONObject(i)?.let { decodePeriod(it) }?.let { periods.add(it) }
        }

        val roomsArr = obj.optJSONArray("rooms") ?: return null
        val rooms = linkedMapOf<String, RoomSchedule>()
        for (i in 0 until roomsArr.length()) {
            val rObj = roomsArr.optJSONObject(i) ?: continue
            val code = rObj.optNullableString("roomCode") ?: continue
            val fullTitle = rObj.optNullableString("fullTitle") ?: "Sala $code"
            val daysObj = rObj.optJSONObject("lessonsByWeekday") ?: JSONObject()
            val byWeekday = linkedMapOf<Int, List<RoomLesson>>()
            val keys = daysObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val weekdayIdx = key.toIntOrNull() ?: continue
                val lArr = daysObj.optJSONArray(key) ?: continue
                val lessons = mutableListOf<RoomLesson>()
                for (j in 0 until lArr.length()) {
                    lArr.optJSONObject(j)?.let { decodeLesson(it) }?.let { lessons.add(it) }
                }
                byWeekday[weekdayIdx] = lessons
            }
            rooms[code] = RoomSchedule(code, fullTitle, byWeekday)
        }
        if (rooms.isEmpty()) return null
        return ParsedTimetable(rooms = rooms, periodTimes = periods, generatedInfo = generatedInfo)
    }

    private fun encodePeriod(p: PeriodTime): JSONObject = JSONObject().apply {
        put("number", p.number)
        put("sh", p.start.hour)
        put("sm", p.start.minute)
        put("eh", p.end.hour)
        put("em", p.end.minute)
    }

    private fun decodePeriod(obj: JSONObject): PeriodTime? {
        if (!obj.has("number")) return null
        return PeriodTime(
            number = obj.getInt("number"),
            start = ClockTime(obj.optInt("sh"), obj.optInt("sm")),
            end = ClockTime(obj.optInt("eh"), obj.optInt("em"))
        )
    }

    private fun encodeLesson(l: RoomLesson): JSONObject = JSONObject().apply {
        put("periodNumber", l.periodNumber)
        put("timeRange", encodePeriod(l.timeRange))
        put("roomName", l.roomName)
        put("className", l.className)
        put("subject", l.subject)
        put("teacher", l.teacher)
        put("isSubstitutionAdded", l.isSubstitutionAdded)
        l.substitutionNote?.let { put("substitutionNote", it) }
    }

    private fun decodeLesson(obj: JSONObject): RoomLesson? {
        val period = decodePeriod(obj.optJSONObject("timeRange") ?: return null) ?: return null
        return RoomLesson(
            periodNumber = obj.optInt("periodNumber", period.number),
            timeRange = period,
            roomName = obj.optNullableString("roomName").orEmpty(),
            className = obj.optNullableString("className").orEmpty(),
            subject = obj.optNullableString("subject").orEmpty(),
            teacher = obj.optNullableString("teacher").orEmpty(),
            isSubstitutionAdded = obj.optBoolean("isSubstitutionAdded", false),
            substitutionNote = obj.optNullableString("substitutionNote")
        )
    }

    private fun encodeSubsDay(day: CachedSubstitutionsDay): JSONObject = JSONObject().apply {
        put("isoDate", day.isoDate)
        day.displayDateText?.let { put("displayDateText", it) }
        day.savedAtText?.let { put("savedAtText", it) }
        val entriesArr = JSONArray()
        day.entries.forEach { entriesArr.put(encodeSubEntry(it)) }
        put("entries", entriesArr)
    }

    private fun decodeSubsDay(obj: JSONObject): CachedSubstitutionsDay? {
        val iso = obj.optNullableString("isoDate") ?: return null
        val display = obj.optNullableString("displayDateText")
        val savedAt = obj.optNullableString("savedAtText")
        val entriesArr = obj.optJSONArray("entries") ?: JSONArray()
        val entries = mutableListOf<SubstitutionEntry>()
        for (i in 0 until entriesArr.length()) {
            entriesArr.optJSONObject(i)?.let { decodeSubEntry(it) }?.let { entries.add(it) }
        }
        return CachedSubstitutionsDay(
            isoDate = iso,
            displayDateText = display,
            entries = entries,
            savedAtText = savedAt
        )
    }

    private fun encodeSubEntry(e: SubstitutionEntry): JSONObject = JSONObject().apply {
        put("id", e.id)
        e.dateText?.let { put("dateText", it) }
        e.periodNumber?.let { put("periodNumber", it) }
        e.targetPeriodNumber?.let { put("targetPeriodNumber", it) }
        e.timeText?.let { put("timeText", it) }
        e.className?.let { put("className", it) }
        e.teacherAbsent?.let { put("teacherAbsent", it) }
        e.substituteTeacher?.let { put("substituteTeacher", it) }
        e.subject?.let { put("subject", it) }
        e.originalRoom?.let { put("originalRoom", it) }
        e.newRoom?.let { put("newRoom", it) }
        put("kind", e.kind.name)
        put("rawText", e.rawText)
        e.impactSummary?.let { put("impactSummary", it) }
    }

    private fun decodeSubEntry(obj: JSONObject): SubstitutionEntry? {
        val id = obj.optNullableString("id") ?: return null
        val kindName = obj.optNullableString("kind") ?: SubstitutionKind.OTHER.name
        val kind = runCatching { SubstitutionKind.valueOf(kindName) }.getOrDefault(SubstitutionKind.OTHER)
        return SubstitutionEntry(
            id = id,
            dateText = obj.optNullableString("dateText"),
            periodNumber = obj.optNullableInt("periodNumber"),
            targetPeriodNumber = obj.optNullableInt("targetPeriodNumber"),
            timeText = obj.optNullableString("timeText"),
            className = obj.optNullableString("className"),
            teacherAbsent = obj.optNullableString("teacherAbsent"),
            substituteTeacher = obj.optNullableString("substituteTeacher"),
            subject = obj.optNullableString("subject"),
            originalRoom = obj.optNullableString("originalRoom"),
            newRoom = obj.optNullableString("newRoom"),
            kind = kind,
            rawText = obj.optNullableString("rawText").orEmpty(),
            impactSummary = obj.optNullableString("impactSummary")
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key)
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }
}
