package com.example.data

import com.example.model.PeriodTime
import com.example.model.RoomLesson
import com.example.model.RoomSchedule
import com.example.util.TimeHelper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class DiscoveredLinks(
    val listUrl: String?,
    val roomUrls: List<Pair<String, String>>,   // url to displayName
    val classUrls: List<Pair<String, String>>   // url to displayName
)

object OptivumTimetableParser {

    /**
     * Inspects the entry HTML document (index.html or lista.html) to locate frame targets
     * and collect links to classroom pages (plany/s*.html) and class pages (plany/o*.html).
     */
    fun discoverLinks(html: String, baseUrl: String): DiscoveredLinks {
        val doc = Jsoup.parse(html, baseUrl)

        val frameSrc = doc.select("frame[src], iframe[src]")
            .mapNotNull { el ->
                val src = el.absUrl("src").ifBlank { el.attr("src") }
                src.takeIf { it.isNotBlank() }
            }
            .firstOrNull { it.contains("lista", ignoreCase = true) || it.endsWith(".html", ignoreCase = true) }

        val links = doc.select("a[href]")
        val roomUrls = mutableListOf<Pair<String, String>>()
        val classUrls = mutableListOf<Pair<String, String>>()
        var listaLink: String? = null

        val roomHrefRegex = Regex("""(?:^|/)s\d+\.html?(?:[?#].*)?$""", RegexOption.IGNORE_CASE)
        val classHrefRegex = Regex("""(?:^|/)o\d+\.html?(?:[?#].*)?$""", RegexOption.IGNORE_CASE)

        for (a in links) {
            val href = a.attr("href").trim()
            if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:", ignoreCase = true)) continue
            val abs = a.absUrl("href").ifBlank { resolveUrl(baseUrl, href) }
            val text = a.text().trim()

            when {
                href.contains("lista.html", ignoreCase = true) -> {
                    listaLink = abs
                }
                roomHrefRegex.containsMatchIn(href) -> {
                    roomUrls.add(abs to text)
                }
                classHrefRegex.containsMatchIn(href) -> {
                    classUrls.add(abs to text)
                }
            }
        }

        val resolvedListUrl = listaLink ?: frameSrc?.let { resolveUrl(baseUrl, it) }
        return DiscoveredLinks(
            listUrl = resolvedListUrl,
            roomUrls = roomUrls.distinctBy { it.first },
            classUrls = classUrls.distinctBy { it.first }
        )
    }

    /**
     * Parses a single Optivum classroom timetable page (plany/s*.html).
     */
    fun parseRoomPage(html: String, fallbackName: String = ""): Pair<RoomSchedule, List<PeriodTime>>? {
        val doc = Jsoup.parse(html)
        val rawTitle = extractTitle(doc, fallbackName)
        if (rawTitle.isBlank()) return null

        val roomCode = TimeHelper.normalizeRoomCode(rawTitle).ifBlank { rawTitle }
        val table = findTimetableTable(doc) ?: return null
        val rows = table.select("tr")
        if (rows.isEmpty()) return null

        val weekdayColumnMap = detectWeekdayColumns(rows.firstOrNull())
        val lessonsByWeekday = mutableMapOf<Int, MutableList<RoomLesson>>()
        val discoveredPeriods = mutableMapOf<Int, PeriodTime>()

        for (row in rows.drop(1)) {
            val cells = row.select("td, th")
            if (cells.size < 3) continue

            val nrText = cells.getOrNull(0)?.text()?.trim().orEmpty()
            val periodNr = nrText.toIntOrNull() ?: continue

            val timeText = cells.getOrNull(1)?.text()?.trim().orEmpty()
            val parsedRange = TimeHelper.parseTimeRange(timeText)
            val periodTime = if (parsedRange != null) {
                PeriodTime(periodNr, parsedRange.first, parsedRange.second)
            } else {
                TimeHelper.defaultPeriods.find { it.number == periodNr }
                    ?: continue
            }
            discoveredPeriods[periodNr] = periodTime

            for ((colIndex, weekdayIndex) in weekdayColumnMap) {
                val cell = cells.getOrNull(colIndex) ?: continue
                val parsedEntries = parseLessonCell(cell, isRoomPage = true, fixedRoomName = roomCode)
                for (entry in parsedEntries) {
                    val list = lessonsByWeekday.getOrPut(weekdayIndex) { mutableListOf() }
                    list.add(
                        RoomLesson(
                            periodNumber = periodNr,
                            timeRange = periodTime,
                            roomName = roomCode,
                            className = entry.className,
                            subject = entry.subject,
                            teacher = entry.teacher
                        )
                    )
                }
            }
        }

        val schedule = RoomSchedule(
            roomCode = roomCode,
            fullTitle = rawTitle,
            lessonsByWeekday = lessonsByWeekday.mapValues { (_, list) ->
                list.sortedBy { it.periodNumber }
            }
        )
        return schedule to discoveredPeriods.values.sortedBy { it.number }
    }

    /**
     * Fallback parser: parses an Optivum class timetable page (plany/o*.html) and emits RoomLesson items
     * keyed by each mentioned room (<span class="s">).
     */
    fun parseClassPage(html: String, fallbackClassName: String = ""): Pair<List<Pair<Int, RoomLesson>>, List<PeriodTime>> {
        val doc = Jsoup.parse(html)
        val classTitle = extractTitle(doc, fallbackClassName)
            .replace(Regex("""\s+[-–].*$"""), "")
            .trim()
            .ifBlank { fallbackClassName }
        val table = findTimetableTable(doc) ?: return emptyList<Pair<Int, RoomLesson>>() to emptyList()
        val rows = table.select("tr")
        if (rows.isEmpty()) return emptyList<Pair<Int, RoomLesson>>() to emptyList()

        val weekdayColumnMap = detectWeekdayColumns(rows.firstOrNull())
        val lessons = mutableListOf<Pair<Int, RoomLesson>>()
        val discoveredPeriods = mutableMapOf<Int, PeriodTime>()

        for (row in rows.drop(1)) {
            val cells = row.select("td, th")
            if (cells.size < 3) continue
            val periodNr = cells.getOrNull(0)?.text()?.trim()?.toIntOrNull() ?: continue
            val timeText = cells.getOrNull(1)?.text()?.trim().orEmpty()
            val parsedRange = TimeHelper.parseTimeRange(timeText)
            val periodTime = if (parsedRange != null) {
                PeriodTime(periodNr, parsedRange.first, parsedRange.second)
            } else {
                TimeHelper.defaultPeriods.find { it.number == periodNr } ?: continue
            }
            discoveredPeriods[periodNr] = periodTime

            for ((colIndex, weekdayIndex) in weekdayColumnMap) {
                val cell = cells.getOrNull(colIndex) ?: continue
                val parsedEntries = parseLessonCell(cell, isRoomPage = false, fixedRoomName = "")
                for (entry in parsedEntries) {
                    val room = TimeHelper.normalizeRoomCode(entry.roomName)
                    if (room.isBlank()) continue
                    lessons.add(
                        weekdayIndex to RoomLesson(
                            periodNumber = periodNr,
                            timeRange = periodTime,
                            roomName = room,
                            className = entry.className.ifBlank { classTitle },
                            subject = entry.subject,
                            teacher = entry.teacher
                        )
                    )
                }
            }
        }
        return lessons to discoveredPeriods.values.sortedBy { it.number }
    }

    private data class CellLessonInfo(
        val className: String,
        val subject: String,
        val teacher: String,
        val roomName: String
    )

    private fun parseLessonCell(
        cell: Element,
        isRoomPage: Boolean,
        fixedRoomName: String
    ): List<CellLessonInfo> {
        val rawCellText = cell.text().replace("\u00A0", " ").trim()
        if (rawCellText.isBlank() || rawCellText == "-") return emptyList()

        // Split by <br> or block-level child wrappers (e.g. multiple groups in one period)
        val htmlChunks = cell.html()
            .split(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val results = mutableListOf<CellLessonInfo>()
        if (htmlChunks.size > 1) {
            for (chunk in htmlChunks) {
                val frag = Jsoup.parseBodyFragment(chunk)
                val info = extractChunkInfo(frag.body(), isRoomPage, fixedRoomName)
                if (info != null) results.add(info)
            }
        } else {
            val info = extractChunkInfo(cell, isRoomPage, fixedRoomName)
            if (info != null) results.add(info)
        }

        // Deduplicate identical entries inside same cell
        return results.distinct()
    }

    private fun extractChunkInfo(
        container: Element,
        isRoomPage: Boolean,
        fixedRoomName: String
    ): CellLessonInfo? {
        val text = container.text().replace("\u00A0", " ").trim()
        if (text.isBlank() || text == "-") return null

        val classes = container.select("span.o, a.o").map { it.text().trim() }.filter { it.isNotBlank() }
        val subjects = container.select("span.p").map { it.text().trim() }.filter { it.isNotBlank() }
        val teachers = container.select("span.n, a.n").map { it.text().trim() }.filter { it.isNotBlank() }
        val rooms = container.select("span.s, a.s").map { it.text().trim() }.filter { it.isNotBlank() }

        val className = classes.joinToString(", ")
        val subject = subjects.joinToString(" / ").ifBlank {
            if (classes.isEmpty() && teachers.isEmpty()) text else ""
        }
        val teacher = teachers.joinToString(", ")
        val roomName = if (isRoomPage) fixedRoomName else rooms.firstOrNull().orEmpty()

        if (className.isBlank() && subject.isBlank() && teacher.isBlank() && roomName.isBlank()) {
            return null
        }

        return CellLessonInfo(
            className = className,
            subject = subject.ifBlank { "Zajęcia" },
            teacher = teacher,
            roomName = roomName
        )
    }

    private fun extractTitle(doc: Document, fallback: String): String {
        val fromSpan = doc.selectFirst(".tytulnapis, h1, h2")?.text()?.trim()
        if (!fromSpan.isNullOrBlank()) return fromSpan
        val fromTitle = doc.title().trim()
        if (fromTitle.isNotBlank() && !fromTitle.equals("Plan lekcji", ignoreCase = true)) {
            return fromTitle.removePrefix("Plan lekcji - ").removePrefix("Plan lekcji ").trim()
        }
        return fallback.trim()
    }

    private fun findTimetableTable(doc: Document): Element? {
        val explicit = doc.selectFirst("table.tabela")
        if (explicit != null) return explicit
        return doc.select("table").firstOrNull { table ->
            val headerText = table.select("tr").firstOrNull()?.text()?.lowercase().orEmpty()
            headerText.contains("poniedziałek") || headerText.contains("wtorek") || headerText.contains("godz")
        } ?: doc.select("table").maxByOrNull { it.select("tr").size }
    }

    private fun detectWeekdayColumns(headerRow: Element?): Map<Int, Int> {
        if (headerRow == null) return mapOf(2 to 0, 3 to 1, 4 to 2, 5 to 3, 6 to 4)
        val cells = headerRow.select("th, td")
        val map = mutableMapOf<Int, Int>()
        cells.forEachIndexed { idx, cell ->
            val t = cell.text().trim().lowercase()
            when {
                t.startsWith("pon") -> map[idx] = 0
                t.startsWith("wto") || t == "wt" -> map[idx] = 1
                t.startsWith("śro") || t.startsWith("sro") || t == "śr" -> map[idx] = 2
                t.startsWith("czw") || t == "cz" -> map[idx] = 3
                t.startsWith("pią") || t.startsWith("pia") || t == "pt" -> map[idx] = 4
            }
        }
        return if (map.isNotEmpty()) map else mapOf(2 to 0, 3 to 1, 4 to 2, 5 to 3, 6 to 4)
    }

    fun resolveUrl(base: String, relative: String): String {
        if (relative.startsWith("http://", ignoreCase = true) ||
            relative.startsWith("https://", ignoreCase = true)
        ) {
            return relative
        }
        return try {
            java.net.URI(base.ensureTrailingSlashIfRoot()).resolve(relative).toString()
        } catch (_: Exception) {
            val cleanBase = base.trimEnd('/')
            val cleanRel = relative.trimStart('/')
            "$cleanBase/$cleanRel"
        }
    }

    private fun String.ensureTrailingSlashIfRoot(): String {
        val uri = try { java.net.URI(this) } catch (_: Exception) { return this }
        val path = uri.path
        return if (path.isNullOrEmpty()) "$this/" else this
    }
}
