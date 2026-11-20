package com.example

import com.example.data.OptivumTimetableParser
import com.example.data.ScheduleAnalyzer
import com.example.data.SubstitutionsParser
import com.example.model.ClockTime
import com.example.model.ParsedTimetable
import com.example.model.RoomAvailabilityStatus
import com.example.model.SchoolWeekday
import com.example.model.SubstitutionKind
import com.example.data.SettingsDataStore
import com.example.model.FloorLevelMatcher
import com.example.util.SchoolDateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEngineTest {

    private val wednesday = SchoolDateInfo(
        year = 2026,
        month = 11,
        dayOfMonth = 18,
        weekday = SchoolWeekday.WEDNESDAY
    )

    private fun sampleRoomHtml(roomTitle: String, wednesdayRows: Map<Int, Pair<String, String>>): String {
        val rowsHtml = (1..7).joinToString("\n") { nr ->
            val time = when (nr) {
                1 -> "08:00-08:45"
                2 -> "08:55-09:40"
                3 -> "09:50-10:35"
                4 -> "10:45-11:30"
                5 -> "11:50-12:35"
                6 -> "12:45-13:30"
                else -> "13:40-14:25"
            }
            val wed = wednesdayRows[nr]?.let { (cls, subj) ->
                """<span class="o">$cls</span> <span class="p">$subj</span> <span class="n">JK</span>"""
            } ?: "&nbsp;"
            """
            <tr>
              <td class="nr">$nr</td>
              <td class="g">$time</td>
              <td class="l">&nbsp;</td>
              <td class="l">&nbsp;</td>
              <td class="l">$wed</td>
              <td class="l">&nbsp;</td>
              <td class="l">&nbsp;</td>
            </tr>
            """.trimIndent()
        }

        return """
        <html>
          <head><title>Plan lekcji - $roomTitle</title></head>
          <body>
            <span class="tytulnapis">$roomTitle</span>
            <table class="tabela">
              <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
              $rowsHtml
            </table>
          </body>
        </html>
        """.trimIndent()
    }

    @Test
    fun `discovers Optivum links and analyzes dismissed and moved lessons`() {
        val listHtml = """
            <html><body>
              <ul>
                <li><a href="plany/s1.html">104</a></li>
                <li><a href="plany/s2.html">205</a></li>
              </ul>
            </body></html>
        """.trimIndent()

        val links = OptivumTimetableParser.discoverLinks(listHtml, "https://plan.zsme.pl/lista.html")
        assertEquals(2, links.roomUrls.size)

        val (room104, periods104) = OptivumTimetableParser.parseRoomPage(
            sampleRoomHtml("104", mapOf(
                5 to ("3Ti" to "Matematyka"),
                6 to ("3Ti" to "Fizyka")
            ))
        )!!
        val (room205, _) = OptivumTimetableParser.parseRoomPage(
            sampleRoomHtml("205", mapOf(
                3 to ("2A" to "Informatyka"),
                5 to ("2B" to "Polski")
            ))
        )!!

        val timetable = ParsedTimetable(
            rooms = mapOf("104" to room104, "205" to room205),
            periodTimes = periods104
        )

        // Room 104: 6th lesson (3Ti) is dismissed early -> room 104 becomes free after 5th lesson (12:35)!
        // Room 205: 3rd lesson (2A) is moved to 7th lesson -> room 205 becomes occupied until 14:25!
        val tvHtml = """
            <html><body>
              <h2>Zastępstwa na dzień 18.11.2026 (środa)</h2>
              <table>
                <tr><th>Lekcja</th><th>Nauczyciel</th><th>Klasa</th><th>Sala</th><th>Uwagi</th></tr>
                <tr><td>6</td><td>JK</td><td>3Ti</td><td>104</td><td>Klasa zwolniona do domu</td></tr>
                <tr><td>3</td><td>JK</td><td>2A</td><td>205</td><td>Lekcja przeniesiona na 7. lekcję</td></tr>
              </table>
            </body></html>
        """.trimIndent()

        val subs = SubstitutionsParser.parse(tvHtml, wednesday)
        assertTrue(subs.matchesToday)
        assertEquals(2, subs.entries.size)
        assertEquals(SubstitutionKind.CANCELLED_DISMISSED, subs.entries[0].kind)
        assertEquals(SubstitutionKind.MOVED_PERIOD, subs.entries[1].kind)
        assertEquals(7, subs.entries[1].targetPeriodNumber)

        // At 12:40 (break before 6th lesson):
        // - Room 104's last lesson was 5th (ended 12:35), because 6th was dismissed -> FREE_FOR_TODAY & becameFreeEarlier!
        // - Room 205 has 7th lesson (moved from 3rd) -> OCCUPIED_LATER (not free after 6th) & becameFreeLater!
        val analyzed = ScheduleAnalyzer.analyze(
            timetable = timetable,
            substitutions = subs,
            dateInfo = wednesday,
            nowTime = ClockTime(12, 40),
            cleanedRoomsToday = emptySet()
        )

        val sum104 = analyzed.rooms.first { it.roomName == "104" }
        assertEquals(RoomAvailabilityStatus.FREE_FOR_TODAY, sum104.status)
        assertTrue(sum104.becameFreeEarlier)
        assertEquals("12:35", sum104.freeFromTime?.formattedHm())

        val sum205 = analyzed.rooms.first { it.roomName == "205" }
        assertEquals(RoomAvailabilityStatus.OCCUPIED_LATER, sum205.status)
        assertTrue(sum205.becameFreeLater)
        assertEquals("14:25", sum205.freeFromTime?.formattedHm())

        // At 13:35 (break before 7th lesson): Room 205 is now FREE_AFTER_NEXT!
        val analyzedLater = ScheduleAnalyzer.analyze(
            timetable = timetable,
            substitutions = subs,
            dateInfo = wednesday,
            nowTime = ClockTime(13, 35),
            cleanedRoomsToday = emptySet()
        )
        val sum205Later = analyzedLater.rooms.first { it.roomName == "205" }
        assertEquals(RoomAvailabilityStatus.FREE_AFTER_NEXT, sum205Later.status)
        assertFalse(sum205Later.becameFreeEarlier)
        assertTrue(sum205Later.becameFreeLater)
    }

    @Test
    fun `floor level matcher classifies default levels, workshops W, and custom rules accurately`() {
        val levels = SettingsDataStore.DEFAULT_FLOOR_LEVELS.associateBy { it.id }
        val basement = levels.getValue("basement").rules
        val ground = levels.getValue("ground").rules
        val first = levels.getValue("first").rules
        val second = levels.getValue("second").rules
        val third = levels.getValue("third").rules
        val workshops = levels.getValue("workshops").rules

        assertTrue(FloorLevelMatcher.matchesAnyRule("1", basement))
        assertTrue(FloorLevelMatcher.matchesAnyRule("9", basement))
        assertFalse(FloorLevelMatcher.matchesAnyRule("10", basement))

        assertTrue(FloorLevelMatcher.matchesAnyRule("10", ground))
        assertTrue(FloorLevelMatcher.matchesAnyRule("29", ground))
        assertFalse(FloorLevelMatcher.matchesAnyRule("30", ground))

        assertTrue(FloorLevelMatcher.matchesAnyRule("30", first))
        assertTrue(FloorLevelMatcher.matchesAnyRule("39", first))
        assertFalse(FloorLevelMatcher.matchesAnyRule("40", first))

        assertTrue(FloorLevelMatcher.matchesAnyRule("40", second))
        assertTrue(FloorLevelMatcher.matchesAnyRule("57", second))
        assertFalse(FloorLevelMatcher.matchesAnyRule("58", second))

        assertTrue(FloorLevelMatcher.matchesAnyRule("58", third))
        assertTrue(FloorLevelMatcher.matchesAnyRule("69", third))
        assertFalse(FloorLevelMatcher.matchesAnyRule("70", third))

        assertTrue(FloorLevelMatcher.matchesAnyRule("W1", workshops))
        assertTrue(FloorLevelMatcher.matchesAnyRule("w12", workshops))
        assertFalse(FloorLevelMatcher.matchesAnyRule("12", workshops))
        assertFalse(FloorLevelMatcher.matchesAnyRule("W1", basement))

        val allDefaultRules = levels.values.flatMap { it.rules }
        assertFalse(FloorLevelMatcher.matchesAnyRule("Aula", allDefaultRules))
        assertFalse(FloorLevelMatcher.matchesAnyRule("SG", allDefaultRules))
        assertFalse(FloorLevelMatcher.matchesAnyRule("75", allDefaultRules))

        // Custom exact room assignment
        val customGround = listOf("10-29", "Aula", "14a")
        assertTrue(FloorLevelMatcher.matchesAnyRule("Aula", customGround))
        assertTrue(FloorLevelMatcher.matchesAnyRule("14a", customGround))
        assertFalse(FloorLevelMatcher.matchesAnyRule("SG", customGround))
    }
}
