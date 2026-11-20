package com.example.data

import com.example.model.ParsedSubstitutions
import com.example.model.ParsedSubstitutionsByDate
import com.example.model.ParsedTimetable
import com.example.model.PeriodTime
import com.example.model.RoomLesson
import com.example.model.RoomSchedule
import com.example.util.SchoolDateInfo
import com.example.util.TimeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

data class FetchedSchoolData(
    val timetable: ParsedTimetable,
    val substitutions: ParsedSubstitutions,
    val substitutionsByDate: ParsedSubstitutionsByDate? = null,
    val timetableWarning: String? = null,
    val substitutionsWarning: String? = null,
    val timetableFromNetwork: Boolean = true,
    val substitutionsFromNetwork: Boolean = true
)

class TimetableRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    suspend fun fetchAll(
        timetableUrl: String,
        substitutionsUrl: String,
        today: SchoolDateInfo = TimeHelper.todayDateInfo(),
        fallbackTimetable: ParsedTimetable? = null
    ): Result<FetchedSchoolData> = withContext(Dispatchers.IO) {
        try {
            coroutineScope {
                val timetableDeferred = async { fetchTimetable(timetableUrl) }
                val substitutionsDeferred = async { fetchSubstitutionsByDate(substitutionsUrl, today) }

                val timetableResult = timetableDeferred.await()
                val substitutionsResult = substitutionsDeferred.await()

                var timetableWarning: String? = null
                var timetableFromNetwork = true
                val timetable = timetableResult.getOrElse { err ->
                    if (fallbackTimetable != null) {
                        timetableWarning = "Brak aktualizacji planu z sieci (${err.localizedMessage ?: "błąd połączenia"}). Użyto planu z pamięci."
                        timetableFromNetwork = false
                        fallbackTimetable
                    } else {
                        return@coroutineScope Result.failure(err)
                    }
                }

                var subsFromNetwork = true
                val (parsedByDate, subWarning) = substitutionsResult.fold(
                    onSuccess = { it to null },
                    onFailure = { err ->
                        subsFromNetwork = false
                        null to "Nie udało się pobrać zastępstw (${err.localizedMessage ?: "błąd sieci"}). Pokazano dane z pamięci."
                    }
                )

                val todaySubs = if (parsedByDate != null) {
                    val todayIso = today.isoKey
                    val matchesToday = parsedByDate.detectedDatesIso.isEmpty() ||
                        parsedByDate.detectedDatesIso.contains(todayIso)
                    val entries = parsedByDate.entriesByDate[todayIso]
                        ?: if (parsedByDate.detectedDatesIso.isEmpty()) parsedByDate.unassociatedEntries else emptyList()
                    ParsedSubstitutions(
                        entries = entries,
                        headerDateText = parsedByDate.headerDateText,
                        matchesToday = matchesToday
                    )
                } else {
                    ParsedSubstitutions(emptyList(), null, true)
                }

                Result.success(
                    FetchedSchoolData(
                        timetable = timetable,
                        substitutions = todaySubs,
                        substitutionsByDate = parsedByDate,
                        timetableWarning = timetableWarning,
                        substitutionsWarning = subWarning,
                        timetableFromNetwork = timetableFromNetwork,
                        substitutionsFromNetwork = subsFromNetwork
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTimetable(baseUrl: String): Result<ParsedTimetable> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = baseUrl.trim()
            val entryPage = fetchHtmlWithRedirect(cleanBase)
            var discovered = OptivumTimetableParser.discoverLinks(entryPage.html, entryPage.finalUrl)

            // If index.html used frameset or linked to lista.html, fetch lista.html
            if (discovered.roomUrls.isEmpty() && discovered.classUrls.isEmpty()) {
                val listUrl = discovered.listUrl
                    ?: OptivumTimetableParser.resolveUrl(entryPage.finalUrl, "lista.html")
                val listPage = fetchHtmlWithRedirect(listUrl)
                discovered = OptivumTimetableParser.discoverLinks(listPage.html, listPage.finalUrl)
            }

            val semaphore = Semaphore(8)
            val allPeriods = mutableMapOf<Int, PeriodTime>()
            val roomSchedules = mutableMapOf<String, RoomSchedule>()

            if (discovered.roomUrls.isNotEmpty()) {
                val parsedRooms = coroutineScope {
                    discovered.roomUrls.map { (url, label) ->
                        async {
                            semaphore.withPermit {
                                runCatching {
                                    val page = fetchHtmlWithRedirect(url)
                                    OptivumTimetableParser.parseRoomPage(page.html, label)
                                }.getOrNull()
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                for ((schedule, periods) in parsedRooms) {
                    roomSchedules[schedule.roomCode] = schedule
                    for (p in periods) {
                        allPeriods[p.number] = p
                    }
                }
            } else if (discovered.classUrls.isNotEmpty()) {
                // Fallback: derive room schedules from class pages (plany/o*.html)
                val byRoomWeekday = mutableMapOf<String, MutableMap<Int, MutableList<RoomLesson>>>()
                val parsedClasses = coroutineScope {
                    discovered.classUrls.map { (url, label) ->
                        async {
                            semaphore.withPermit {
                                runCatching {
                                    val page = fetchHtmlWithRedirect(url)
                                    OptivumTimetableParser.parseClassPage(page.html, label)
                                }.getOrNull()
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                for ((lessons, periods) in parsedClasses) {
                    for (p in periods) allPeriods[p.number] = p
                    for ((weekdayIdx, lesson) in lessons) {
                        val dayMap = byRoomWeekday.getOrPut(lesson.roomName) { mutableMapOf() }
                        dayMap.getOrPut(weekdayIdx) { mutableListOf() }.add(lesson)
                    }
                }

                for ((roomCode, dayMap) in byRoomWeekday) {
                    roomSchedules[roomCode] = RoomSchedule(
                        roomCode = roomCode,
                        fullTitle = "Sala $roomCode",
                        lessonsByWeekday = dayMap.mapValues { (_, list) ->
                            list.distinctBy { it.periodNumber to it.className to it.subject }
                                .sortedBy { it.periodNumber }
                        }
                    )
                }
            } else {
                // Maybe the user pasted a direct single room page URL
                val single = OptivumTimetableParser.parseRoomPage(entryPage.html)
                if (single != null) {
                    roomSchedules[single.first.roomCode] = single.first
                    single.second.forEach { allPeriods[it.number] = it }
                }
            }

            if (roomSchedules.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Nie znaleziono sal lekcyjnych pod adresem $cleanBase. Sprawdź link w ustawieniach.")
                )
            }

            Result.success(
                ParsedTimetable(
                    rooms = roomSchedules,
                    periodTimes = allPeriods.values.sortedBy { it.number }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSubstitutions(
        url: String,
        today: SchoolDateInfo = TimeHelper.todayDateInfo()
    ): Result<ParsedSubstitutions> = withContext(Dispatchers.IO) {
        try {
            val page = fetchHtmlWithRedirect(url.trim())
            val parsed = SubstitutionsParser.parse(page.html, today)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSubstitutionsByDate(
        url: String,
        today: SchoolDateInfo = TimeHelper.todayDateInfo()
    ): Result<ParsedSubstitutionsByDate> = withContext(Dispatchers.IO) {
        try {
            val page = fetchHtmlWithRedirect(url.trim())
            val parsed = SubstitutionsParser.parseByDate(page.html, today)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class HtmlResponse(val html: String, val finalUrl: String)

    private fun fetchHtmlWithRedirect(rawUrl: String): HtmlResponse {
        return try {
            executeGet(rawUrl)
        } catch (sslError: SSLException) {
            if (rawUrl.startsWith("https://", ignoreCase = true)) {
                val httpFallback = "http://" + rawUrl.removePrefix("https://")
                executeGet(httpFallback)
            } else {
                throw sslError
            }
        }
    }

    private fun executeGet(url: String): HtmlResponse {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.5")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Błąd serwera HTTP ${response.code} dla $url")
            }
            val body = response.body ?: throw IOException("Pusta odpowiedź serwera dla $url")
            val bytes = body.bytes()
            val charset = detectCharset(response.header("Content-Type"), bytes)
            val html = String(bytes, charset)
            return HtmlResponse(
                html = html,
                finalUrl = response.request.url.toString()
            )
        }
    }

    private fun detectCharset(contentType: String?, bytes: ByteArray): Charset {
        val headerCharset = contentType
            ?.substringAfter("charset=", "")
            ?.substringBefore(";")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (headerCharset != null) {
            runCatching { return Charset.forName(headerCharset) }
        }

        // Peek into first 1024 bytes for <meta charset="..."> or windows-1250 / iso-8859-2
        val preview = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1)
        val metaMatch = Regex("""charset\s*=\s*["']?([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)
            .find(preview)
        val metaCharset = metaMatch?.groupValues?.get(1)
        if (metaCharset != null) {
            runCatching { return Charset.forName(metaCharset) }
        }
        return Charsets.UTF_8
    }
}
