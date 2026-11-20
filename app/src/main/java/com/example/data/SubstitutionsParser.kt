package com.example.data

import com.example.model.ParsedSubstitutions
import com.example.model.ParsedSubstitutionsByDate
import com.example.model.SubstitutionEntry
import com.example.model.SubstitutionKind
import com.example.util.SchoolDateInfo
import com.example.util.TimeHelper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Locale

object SubstitutionsParser {

    private val dateDotRegex = Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{2,4})\b""")
    private val dateIsoRegex = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")

    private val cancelledRegex = Regex(
        """(zwolnion|do\s+domu|odwołan|odwolan|nie\s+ma\s+lekcj|brak\s+zaj[eę][cć]|nie\s+odb[eę]dzie|okienko|odpada|zaj[eę]cia\s+odwoł|uczniowie\s+do\s+domu)""",
        RegexOption.IGNORE_CASE
    )
    private val notCancelledGuard = Regex("""nie\s+(?:jest\s+)?odwołan""", RegexOption.IGNORE_CASE)

    private val movedZNaRegex = Regex(
        """z\s*(\d{1,2})\s*(?:\.|godz\.?|lekcj[iaę])?\s*na\s*(\d{1,2})\s*(?:\.|godz\.?|lekcj[iaę])?""",
        RegexOption.IGNORE_CASE
    )
    private val movedNaRegex = Regex(
        """(?:przeniesion\w*|przesuni[eę]t\w*|odb[eę]dzie\s+si[eę]|przeło[zż]on\w*)\s+(?:[^0-9]{0,20})na\s+(\d{1,2})\s*(?:\.|godz\.?|lekcj[iaę])?""",
        RegexOption.IGNORE_CASE
    )
    private val movedSimpleNaLessonRegex = Regex(
        """na\s+(\d{1,2})\s*(?:\.\s*)?(?:godz\.?|lekcj[iaę])""",
        RegexOption.IGNORE_CASE
    )

    private val roomExplicitRegex = Regex(
        """(?:zmiana\s+sali\s*(?:na)?|do\s+sali|w\s+sali|nowa\s+sala|sala|s\.)\s*[:.]?\s*([0-9]{1,3}[A-Za-z]?|aula|sg\d*|gimn\w*)""",
        RegexOption.IGNORE_CASE
    )

    private val classTokenRegex = Regex("""\b([1-5]\s*[A-Za-z]{1,4}(?:\s*/\s*\d)?)\b""")
    private val periodPrefixRegex = Regex("""^\s*(\d{1,2})\s*[.)\-:]""")

    fun parse(html: String, today: SchoolDateInfo = TimeHelper.todayDateInfo()): ParsedSubstitutions {
        val byDate = parseByDate(html, today)
        val todayIso = today.isoKey

        val hasTodayInDetected = byDate.detectedDatesIso.contains(todayIso)
        val matchesToday = when {
            byDate.detectedDatesIso.isEmpty() -> true
            hasTodayInDetected -> true
            else -> false
        }

        val entriesForParse = when {
            byDate.entriesByDate.containsKey(todayIso) -> byDate.entriesByDate[todayIso].orEmpty()
            byDate.detectedDatesIso.size > 1 -> byDate.entriesByDate.values.flatten()
            byDate.entriesByDate.isNotEmpty() -> byDate.entriesByDate.values.flatten()
            else -> byDate.unassociatedEntries
        }.distinctBy { Triple(it.rawText, it.periodNumber, it.className) }

        return ParsedSubstitutions(
            entries = entriesForParse,
            headerDateText = byDate.headerDateText,
            matchesToday = matchesToday
        )
    }

    fun parseByDate(html: String, today: SchoolDateInfo = TimeHelper.todayDateInfo()): ParsedSubstitutionsByDate {
        val doc = Jsoup.parse(html)
        doc.select("script, style, noscript").remove()

        val allText = doc.body()?.text()?.replace("\u00A0", " ").orEmpty()
        val foundDates = extractDatesFromText(allText, today)
        val pageHeaderDate = foundDates.firstOrNull()
        val pageHeaderIso = pageHeaderDate?.let {
            TimeHelper.parseDateToIsoKey(it, today, allowShortDayMonth = true, allowWeekdayFallback = true)
        } ?: TimeHelper.parseDateToIsoKey(allText, today, allowShortDayMonth = false, allowWeekdayFallback = true)

        val entries = mutableListOf<SubstitutionEntry>()
        val tables = doc.select("table")

        if (tables.isNotEmpty()) {
            var currentSectionDate: String? = pageHeaderDate
            var currentAbsentTeacher: String? = null

            // Scan top-level elements in document order to track date & teacher headings above tables
            val blocks = doc.body()?.select("h1, h2, h3, h4, p, div.header, div.date, table") ?: emptyList()
            val processedTables = mutableSetOf<Element>()

            for (el in blocks) {
                if (el.tagName() == "table") {
                    if (!processedTables.add(el)) continue
                    val tableEntries = parseTable(el, currentSectionDate, currentAbsentTeacher, today)
                    entries.addAll(tableEntries)
                } else {
                    val t = el.text().replace("\u00A0", " ").trim()
                    if (t.length in 3..120) {
                        val d = extractDateDisplayFromHeader(t, today)
                        if (d != null) {
                            currentSectionDate = d
                        } else if (el.tagName().startsWith("h") && !t.contains("zastępst", ignoreCase = true)) {
                            currentAbsentTeacher = t
                        }
                    }
                }
            }

            // Catch any nested tables not matched above
            for (table in tables) {
                if (processedTables.add(table)) {
                    entries.addAll(parseTable(table, currentSectionDate, currentAbsentTeacher, today))
                }
            }
        }

        // Fallback for non-table card/list layouts on tv.php
        if (entries.isEmpty()) {
            val lines = doc.body()?.select("tr, li, p, div.card, div.row, div.zastepstwo")
                ?.map { it.text().replace("\u00A0", " ").trim() }
                ?.filter { it.length >= 6 }
                ?.distinct()
                .orEmpty()

            var activeDate = pageHeaderDate
            lines.forEachIndexed { idx, line ->
                val d = if (line.length < 60) extractDateDisplayFromHeader(line, today) else null
                if (d != null && line.length < 60) {
                    activeDate = d
                } else if (looksLikeSubstitutionLine(line)) {
                    parseFreeformLine(line, idx, activeDate)?.let { entries.add(it) }
                }
            }
        }

        val entriesByDate = linkedMapOf<String, MutableList<SubstitutionEntry>>()
        val displayDateByIso = linkedMapOf<String, String>()
        val unassociated = mutableListOf<SubstitutionEntry>()

        for (entry in entries) {
            val resolvedIso = entry.dateText?.let {
                TimeHelper.parseDateToIsoKey(it, today, allowShortDayMonth = true, allowWeekdayFallback = true)
            } ?: pageHeaderIso

            if (resolvedIso != null) {
                entriesByDate.getOrPut(resolvedIso) { mutableListOf() }.add(entry)
                val display = entry.dateText ?: pageHeaderDate ?: TimeHelper.formatIsoToDotDate(resolvedIso)
                displayDateByIso.putIfAbsent(resolvedIso, display)
            } else {
                unassociated.add(entry)
            }
        }

        val detectedDatesIso = buildList {
            for (raw in foundDates) {
                val iso = TimeHelper.parseDateToIsoKey(raw, today, allowShortDayMonth = true, allowWeekdayFallback = true)
                if (iso != null) {
                    add(iso)
                    displayDateByIso.putIfAbsent(iso, raw)
                }
            }
            if (pageHeaderIso != null) add(pageHeaderIso)
            addAll(entriesByDate.keys)
        }.distinct()

        return ParsedSubstitutionsByDate(
            entriesByDate = entriesByDate.mapValues { (_, list) ->
                list.distinctBy { Triple(it.rawText, it.periodNumber, it.className) }
            },
            displayDateByIso = displayDateByIso,
            detectedDatesIso = detectedDatesIso,
            headerDateText = pageHeaderDate ?: pageHeaderIso?.let { TimeHelper.formatIsoToDotDate(it) },
            unassociatedEntries = unassociated.distinctBy { Triple(it.rawText, it.periodNumber, it.className) }
        )
    }

    private fun extractDateDisplayFromHeader(text: String, today: SchoolDateInfo): String? {
        extractDatesFromText(text, today).firstOrNull()?.let { return it }
        val iso = TimeHelper.parseDateToIsoKey(
            rawText = text,
            referenceToday = today,
            allowShortDayMonth = true,
            allowWeekdayFallback = true
        ) ?: return null
        return TimeHelper.formatIsoToDotDate(iso)
    }

    private fun parseTable(
        table: Element,
        sectionDate: String?,
        sectionTeacher: String?,
        today: SchoolDateInfo
    ): List<SubstitutionEntry> {
        val rows = table.select("tr")
        if (rows.isEmpty()) return emptyList()

        val result = mutableListOf<SubstitutionEntry>()
        var headerMap = emptyMap<String, Int>()
        var activeTeacher = sectionTeacher
        var activeDate = sectionDate

        rows.forEachIndexed { rowIdx, row ->
            val cells = row.select("th, td")
            if (cells.isEmpty()) return@forEachIndexed

            // Single-cell banner row inside table (date or absent teacher header)
            if (cells.size == 1 || (cells.size == 2 && cells[1].text().isBlank())) {
                val bannerText = cells[0].text().replace("\u00A0", " ").trim()
                val d = extractDateDisplayFromHeader(bannerText, today)
                if (d != null) {
                    activeDate = d
                } else if (bannerText.length in 3..80 && !looksLikeHeaderRow(cells)) {
                    activeTeacher = bannerText
                }
                return@forEachIndexed
            }

            if (row.select("th").isNotEmpty() || looksLikeHeaderRow(cells)) {
                headerMap = mapHeaderColumns(cells)
                return@forEachIndexed
            }

            val cellTexts = cells.map { it.text().replace("\u00A0", " ").trim() }
            if (cellTexts.all { it.isBlank() || it == "-" }) return@forEachIndexed

            val rowJoined = cellTexts.joinToString(" | ")
            val periodRaw = headerMap["period"]?.let { cellTexts.getOrNull(it) }
                ?: cellTexts.firstOrNull { it.matches(Regex("""^\d{1,2}\.?$""")) }
            val periodNumber = periodRaw?.replace(".", "")?.trim()?.toIntOrNull()
                ?: periodPrefixRegex.find(rowJoined)?.groupValues?.get(1)?.toIntOrNull()

            val classRaw = headerMap["class"]?.let { cellTexts.getOrNull(it) }
                ?: classTokenRegex.find(rowJoined)?.groupValues?.get(1)
            val teacherRaw = headerMap["teacher"]?.let { cellTexts.getOrNull(it) } ?: activeTeacher
            val subTeacherRaw = headerMap["sub"]?.let { cellTexts.getOrNull(it) }
            val subjectRaw = headerMap["subject"]?.let { cellTexts.getOrNull(it) }
            val roomColRaw = headerMap["room"]?.let { cellTexts.getOrNull(it) }
            val notesRaw = headerMap["notes"]?.let { cellTexts.getOrNull(it) }

            val combinedNotes = listOfNotNull(notesRaw, subTeacherRaw, rowJoined)
                .joinToString(" ")

            val entry = buildEntry(
                id = "sub_${rowIdx}_${rowJoined.hashCode()}",
                dateText = activeDate,
                periodNumber = periodNumber,
                timeText = headerMap["time"]?.let { cellTexts.getOrNull(it) },
                className = cleanOptional(classRaw),
                teacherAbsent = cleanOptional(teacherRaw),
                substituteTeacher = cleanOptional(subTeacherRaw),
                subject = cleanOptional(subjectRaw),
                roomColumnText = cleanOptional(roomColRaw),
                analysisText = combinedNotes,
                rawText = rowJoined
            )
            result.add(entry)
        }
        return result
    }

    private fun parseFreeformLine(line: String, index: Int, dateText: String?): SubstitutionEntry? {
        val period = periodPrefixRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""\b(\d{1,2})\s*(?:\.\s*)?(?:lekcj|godz)""", RegexOption.IGNORE_CASE)
                .find(line)?.groupValues?.get(1)?.toIntOrNull()
        val className = classTokenRegex.find(line)?.groupValues?.get(1)
        return buildEntry(
            id = "line_${index}_${line.hashCode()}",
            dateText = dateText,
            periodNumber = period,
            timeText = null,
            className = cleanOptional(className),
            teacherAbsent = null,
            substituteTeacher = null,
            subject = null,
            roomColumnText = null,
            analysisText = line,
            rawText = line
        )
    }

    private fun buildEntry(
        id: String,
        dateText: String?,
        periodNumber: Int?,
        timeText: String?,
        className: String?,
        teacherAbsent: String?,
        substituteTeacher: String?,
        subject: String?,
        roomColumnText: String?,
        analysisText: String,
        rawText: String
    ): SubstitutionEntry {
        val zNaMatch = movedZNaRegex.find(analysisText)
        val naMatch = movedNaRegex.find(analysisText) ?: movedSimpleNaLessonRegex.find(analysisText)

        val resolvedSourcePeriod = zNaMatch?.groupValues?.get(1)?.toIntOrNull() ?: periodNumber
        val resolvedTargetPeriod = when {
            zNaMatch != null -> zNaMatch.groupValues[2].toIntOrNull()
            naMatch != null -> naMatch.groupValues[1].toIntOrNull()
            else -> null
        }

        val isCancelled = cancelledRegex.containsMatchIn(analysisText) &&
            !notCancelledGuard.containsMatchIn(analysisText)

        val extractedRoomFromText = roomExplicitRegex.find(analysisText)?.groupValues?.get(1)
            ?.let { TimeHelper.normalizeRoomCode(it) }
        val normalizedRoomCol = roomColumnText?.let { TimeHelper.normalizeRoomCode(it) }
            ?.takeIf { it.isNotBlank() && it != "-" }

        val kind = when {
            isCancelled -> SubstitutionKind.CANCELLED_DISMISSED
            resolvedTargetPeriod != null && resolvedTargetPeriod != resolvedSourcePeriod ->
                SubstitutionKind.MOVED_PERIOD
            extractedRoomFromText != null || (normalizedRoomCol != null && analysisText.contains("zmian", ignoreCase = true)) ->
                SubstitutionKind.ROOM_CHANGED
            !substituteTeacher.isNullOrBlank() && substituteTeacher != "-" ->
                SubstitutionKind.TEACHER_REPLACED
            else -> SubstitutionKind.OTHER
        }

        val originalRoom = if (kind == SubstitutionKind.ROOM_CHANGED) {
            normalizedRoomCol
        } else {
            normalizedRoomCol ?: extractedRoomFromText
        }
        val newRoom = when (kind) {
            SubstitutionKind.ROOM_CHANGED -> extractedRoomFromText ?: normalizedRoomCol
            SubstitutionKind.MOVED_PERIOD -> extractedRoomFromText ?: normalizedRoomCol
            else -> extractedRoomFromText
        }

        val impact = when (kind) {
            SubstitutionKind.CANCELLED_DISMISSED ->
                "Klasa zwolniona / lekcja odwołana — sala może być szybciej wolna"
            SubstitutionKind.MOVED_PERIOD ->
                "Przeniesiona na ${resolvedTargetPeriod}. lekcję"
            SubstitutionKind.ROOM_CHANGED ->
                "Zmiana sali${newRoom?.let { " na $it" }.orEmpty()}"
            SubstitutionKind.TEACHER_REPLACED ->
                "Zastępstwo (${substituteTeacher ?: "inny nauczyciel"})"
            SubstitutionKind.OTHER -> null
        }

        return SubstitutionEntry(
            id = id,
            dateText = dateText,
            periodNumber = resolvedSourcePeriod,
            targetPeriodNumber = resolvedTargetPeriod,
            timeText = timeText,
            className = className,
            teacherAbsent = teacherAbsent,
            substituteTeacher = substituteTeacher,
            subject = subject,
            originalRoom = originalRoom,
            newRoom = newRoom,
            kind = kind,
            rawText = rawText,
            impactSummary = impact
        )
    }

    private fun looksLikeHeaderRow(cells: List<Element>): Boolean {
        val text = cells.joinToString(" ") { it.text().lowercase() }
        val hits = listOf("lekcj", "godz", "klas", "oddz", "nauczyc", "zast", "sal", "uwag", "przedmiot")
            .count { text.contains(it) }
        return hits >= 2
    }

    private fun mapHeaderColumns(cells: List<Element>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        cells.forEachIndexed { idx, cell ->
            val t = cell.text().trim().lowercase(Locale.ROOT)
            when {
                t == "nr" || t.contains("lekcj") -> map.putIfAbsent("period", idx)
                t.contains("godz") -> map.putIfAbsent("time", idx)
                t.contains("klas") || t.contains("oddz") || t.contains("grupa") -> map.putIfAbsent("class", idx)
                t.contains("zastęp") || t.contains("zastep") -> map.putIfAbsent("sub", idx)
                t.contains("nauczyc") || t.contains("nieobecn") -> map.putIfAbsent("teacher", idx)
                t.contains("przedmiot") -> map.putIfAbsent("subject", idx)
                t.contains("sal") -> map.putIfAbsent("room", idx)
                t.contains("uwag") || t.contains("opis") || t.contains("info") || t.contains("koment") ->
                    map.putIfAbsent("notes", idx)
            }
        }
        return map
    }

    private fun looksLikeSubstitutionLine(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT)
        return cancelledRegex.containsMatchIn(lower) ||
            movedZNaRegex.containsMatchIn(lower) ||
            movedNaRegex.containsMatchIn(lower) ||
            lower.contains("zastępst") ||
            lower.contains("zastepst") ||
            (classTokenRegex.containsMatchIn(line) && (lower.contains("sala") || lower.contains("lekcj")))
    }

    private fun extractDatesFromText(
        text: String,
        today: SchoolDateInfo = TimeHelper.todayDateInfo()
    ): List<String> {
        val dates = mutableListOf<String>()
        dateDotRegex.findAll(text).forEach { dates.add(it.value) }
        dateIsoRegex.findAll(text).forEach { dates.add(it.value) }
        val polishMonthRegex = Regex(
            """\b(\d{1,2})\s+(stycznia|lutego|marca|kwietnia|maja|czerwca|lipca|sierpnia|wrze[śs]nia|pa[źz]dziernika|listopada|grudnia)(?:\s+(\d{4}))?\b""",
            RegexOption.IGNORE_CASE
        )
        polishMonthRegex.findAll(text).forEach { match ->
            val iso = TimeHelper.parseDateToIsoKey(match.value, today)
            if (iso != null) dates.add(TimeHelper.formatIsoToDotDate(iso))
        }
        return dates.distinct()
    }

    private fun cleanOptional(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        return if (t.isBlank() || t == "-" || t == "—") null else t
    }
}
