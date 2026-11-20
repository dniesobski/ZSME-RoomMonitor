package com.example

import com.example.data.OptivumTimetableParser
import com.example.data.ScheduleAnalyzer
import com.example.data.ScheduleCacheStore
import com.example.data.SubstitutionsParser
import com.example.model.ClockTime
import com.example.model.ParsedSubstitutions
import com.example.model.ParsedTimetable
import com.example.model.RoomAvailabilityStatus
import com.example.model.SchoolWeekday
import com.example.util.SchoolDateInfo
import com.example.util.TimeHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScheduleCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val friday = SchoolDateInfo(2026, 11, 20, SchoolWeekday.FRIDAY)
    private val monday = SchoolDateInfo(2026, 11, 23, SchoolWeekday.MONDAY)

    private fun sampleTimetable(): ParsedTimetable {
        val html = """
            <html><body>
              <span class="tytulnapis">104</span>
              <table class="tabela">
                <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
                <tr>
                  <td class="nr">5</td><td class="g">11:50-12:35</td>
                  <td class="l"><span class="o">2A</span> <span class="p">Fizyka</span> <span class="n">JK</span></td>
                  <td class="l">&nbsp;</td><td class="l">&nbsp;</td><td class="l">&nbsp;</td>
                  <td class="l"><span class="o">3Ti</span> <span class="p">Matematyka</span> <span class="n">JK</span></td>
                </tr>
                <tr>
                  <td class="nr">6</td><td class="g">12:45-13:30</td>
                  <td class="l"><span class="o">2B</span> <span class="p">Polski</span> <span class="n">AB</span></td>
                  <td class="l">&nbsp;</td><td class="l">&nbsp;</td><td class="l">&nbsp;</td>
                  <td class="l"><span class="o">3Ti</span> <span class="p">Informatyka</span> <span class="n">JK</span></td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()
        val (room104, periods) = OptivumTimetableParser.parseRoomPage(html)!!
        return ParsedTimetable(rooms = mapOf("104" to room104), periodTimes = periods)
    }

    @Test
    fun `preserves today substitutions when tv updates to next day and purges old day next morning`() = runTest {
        val cacheFile = tempFolder.newFile("cache.json")
        val ttUrl = "https://plan.zsme.pl"
        val subsUrl = "https://sekretariat.zsme.pl/tv.php"
        val store = ScheduleCacheStore(cacheFile)

        // 1. Morning on Friday 20.11.2026: fetch timetable + today's substitutions (6th lesson dismissed)
        val fridayHtml = """
            <html><body>
              <h2>Zastępstwa na dzień 20.11.2026 (piątek)</h2>
              <table>
                <tr><th>Lekcja</th><th>Nauczyciel</th><th>Klasa</th><th>Sala</th><th>Uwagi</th></tr>
                <tr><td>6</td><td>JK</td><td>3Ti</td><td>104</td><td>Klasa zwolniona do domu</td></tr>
              </table>
            </body></html>
        """.trimIndent()

        val timetable = sampleTimetable()
        store.saveTimetable(ttUrl, timetable, "08:00:00", friday)
        val parsedFriday = SubstitutionsParser.parseByDate(fridayHtml, friday)
        var cache = store.mergeSubstitutions(subsUrl, parsedFriday, "08:00:00", friday)

        assertEquals(1, cache.substitutionsForDate(friday.isoKey)?.entries?.size)

        // 2. Afternoon on Friday 20.11.2026: tv.php is updated to show ONLY Monday 23.11.2026!
        val mondayOnlyHtml = """
            <html><body>
              <h2>Zastępstwa na dzień 23 listopada 2026 (poniedziałek)</h2>
              <table>
                <tr><th>Lekcja</th><th>Nauczyciel</th><th>Klasa</th><th>Sala</th><th>Uwagi</th></tr>
                <tr><td>6</td><td>AB</td><td>2B</td><td>104</td><td>Klasa zwolniona do domu</td></tr>
              </table>
            </body></html>
        """.trimIndent()

        val parsedMondayUpdate = SubstitutionsParser.parseByDate(mondayOnlyHtml, friday)
        cache = store.mergeSubstitutions(subsUrl, parsedMondayUpdate, "13:15:00", friday)

        // Friday's substitutions MUST still be preserved in cache, AND Monday's must be cached!
        val keptFriday = cache.substitutionsForDate(friday.isoKey)
        val savedMonday = cache.substitutionsForDate(monday.isoKey)
        assertNotNull(keptFriday)
        assertEquals(1, keptFriday!!.entries.size)
        assertNotNull(savedMonday)
        assertEquals(1, savedMonday!!.entries.size)
        assertEquals("2B", savedMonday.entries.first().className)

        // Room 104 occupancy on Friday at 12:40 still sees 6th lesson cancelled -> FREE_FOR_TODAY!
        val fridayAnalysis = ScheduleAnalyzer.analyze(
            timetable = cache.timetable!!,
            substitutions = ParsedSubstitutions(keptFriday.entries, keptFriday.displayDateText, matchesToday = true),
            dateInfo = friday,
            nowTime = ClockTime(12, 40),
            cleanedRoomsToday = emptySet()
        )
        assertEquals(RoomAvailabilityStatus.FREE_FOR_TODAY, fridayAnalysis.rooms.first().status)

        // 3. Re-open app on Monday 23.11.2026 with a fresh ScheduleCacheStore reading from disk
        val freshStoreInstance = ScheduleCacheStore(cacheFile)
        val mondayCache = freshStoreInstance.loadAndPrune(ttUrl, subsUrl, monday)

        // Friday (expired) was purged; Monday (previously cached) is immediately available!
        assertNull(mondayCache.substitutionsForDate(friday.isoKey))
        val activeMonday = mondayCache.substitutionsForDate(monday.isoKey)
        assertNotNull(activeMonday)
        assertEquals(1, activeMonday!!.entries.size)
        assertNotNull(mondayCache.timetable)

        // Room 104 occupancy on Monday at 12:40 immediately uses the cached substitution!
        val mondayAnalysis = ScheduleAnalyzer.analyze(
            timetable = mondayCache.timetable!!,
            substitutions = ParsedSubstitutions(activeMonday.entries, activeMonday.displayDateText, matchesToday = true),
            dateInfo = monday,
            nowTime = ClockTime(12, 40),
            cleanedRoomsToday = emptySet()
        )
        assertEquals(RoomAvailabilityStatus.FREE_FOR_TODAY, mondayAnalysis.rooms.first().status)
    }

    @Test
    fun `parses multi-date substitution page and Polish date formats`() {
        assertEquals("2026-11-23", TimeHelper.parseDateToIsoKey("23 listopada 2026", friday))
        assertEquals("2026-11-21", TimeHelper.parseDateToIsoKey("21.11.2026", friday))
        assertEquals("2026-11-21", TimeHelper.parseDateToIsoKey("21.11.26", friday))
        assertEquals(
            "2026-11-23",
            TimeHelper.parseDateToIsoKey("Zastępstwa na poniedziałek", friday, allowWeekdayFallback = true)
        )

        val multiDateHtml = """
            <html><body>
              <h2>20.11.2026</h2>
              <table>
                <tr><th>Lekcja</th><th>Klasa</th><th>Sala</th><th>Uwagi</th></tr>
                <tr><td>6</td><td>3Ti</td><td>104</td><td>Zwolniona</td></tr>
              </table>
              <h2>21.11.2026</h2>
              <table>
                <tr><th>Lekcja</th><th>Klasa</th><th>Sala</th><th>Uwagi</th></tr>
                <tr><td>4</td><td>1A</td><td>105</td><td>Odwołana</td></tr>
              </table>
            </body></html>
        """.trimIndent()

        val parsed = SubstitutionsParser.parseByDate(multiDateHtml, friday)
        assertTrue(parsed.entriesByDate.containsKey("2026-11-20"))
        assertTrue(parsed.entriesByDate.containsKey("2026-11-21"))
        assertEquals("3Ti", parsed.entriesByDate["2026-11-20"]?.first()?.className)
        assertEquals("1A", parsed.entriesByDate["2026-11-21"]?.first()?.className)
    }
}
