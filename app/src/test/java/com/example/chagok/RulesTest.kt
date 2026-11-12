package com.example.chagok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class RulesTest {

    private val noon = LocalDateTime.of(2026, 11, 5, 12, 0)
    private val coast = Rules.spots.first { it.kind == SpotKind.COAST }
    private val blossom = Rules.spots.first { it.kind == SpotKind.BLOSSOM }
    private val seoul = ZoneId.of("Asia/Seoul")
    private val gangneung = Point(37.7519, 128.8761)

    private fun Recorder.drive(meters: Double, region: String? = "32030", moment: Moment = Moment.DAY, wiper: Boolean = false, steps: Int = 10) =
        repeat(steps) { move(meters / steps, region, moment, wiper) }

    private fun trip(id: String = "t", at: LocalDateTime = noon, places: Map<String, Map<Moment, Double>> = emptyMap(), wiperM: Double = 0.0, spots: Map<String, Moment> = emptyMap()) =
        Trip(id, at.minusMinutes(30), at, places.values.sumOf { it.values.sum() }, places, wiperM, spots)

    private fun day(code: String = "32030", meters: Double = 2_000.0) = mapOf(code to mapOf(Moment.DAY to meters))

    private fun judge(trip: Trip, history: List<Trip> = emptyList(), stamps: List<Stamp> = emptyList(), earned: List<Earned> = emptyList()) =
        Rules.judge(trip, history, stamps, earned)

    @Test
    fun regionStampNeeds500Meters() {
        assertTrue(judge(Recorder("t", noon).apply { drive(499.0) }.snapshot(noon)).stamps.isEmpty())
        val stamps = judge(Recorder("t", noon).apply { drive(520.0) }.snapshot(noon)).stamps
        assertEquals(listOf(Stamp("32030", Moment.DAY, Season.AUTUMN, noon)), stamps)
    }

    @Test
    fun regionTakesTheLongestMoment() {
        val trip = Recorder("t", noon).apply {
            drive(300.0, moment = Moment.DAY)
            drive(700.0, moment = Moment.RAIN, wiper = true)
        }.snapshot(noon)
        assertEquals(listOf(Moment.RAIN), judge(trip).stamps.map { it.moment })
        assertEquals(700.0, trip.wiperM, 0.1)
    }

    @Test
    fun samePlaceInAnotherMomentOrSeasonIsANewStamp() {
        val owned = listOf(Stamp("32030", Moment.DAY, Season.AUTUMN, noon.minusDays(3)))
        assertTrue(judge(trip(places = day()), stamps = owned).stamps.isEmpty())
        val rain = trip(places = mapOf("32030" to mapOf(Moment.RAIN to 2_000.0)))
        assertEquals(listOf(Moment.RAIN), judge(rain, stamps = owned).stamps.map { it.moment })
        val winter = trip(at = LocalDateTime.of(2026, 12, 20, 12, 0), places = day())
        assertEquals(listOf(Season.WINTER), judge(winter, stamps = owned).stamps.map { it.season })
    }

    @Test
    fun spotStampNeedsStartThenEnd() {
        val inOrder = Recorder("t", noon).apply {
            enter(coast, atEnd = false, moment = Moment.DAY)
            drive(100.0)
            enter(coast, atEnd = true, moment = Moment.SUNSET)
        }.snapshot(noon)
        assertEquals(Moment.SUNSET, judge(inOrder).stamps.first { it.code == coast.code }.moment)
        val reversed = Recorder("t", noon).apply {
            enter(coast, atEnd = true, moment = Moment.DAY)
            enter(coast, atEnd = false, moment = Moment.DAY)
        }.snapshot(noon)
        assertTrue(judge(reversed).stamps.isEmpty())
    }

    @Test
    fun limitedSpotOnlyStampsInItsWindow() {
        val april = trip(at = LocalDateTime.of(2027, 4, 10, 12, 0), spots = mapOf(blossom.code to Moment.DAY))
        assertEquals(listOf(blossom.code), judge(april).stamps.map { it.code })
        assertTrue(judge(april.copy(endedAt = noon)).stamps.isEmpty())
    }

    @Test
    fun levelGoesUpWithVisits() {
        val history = (1..4).map { trip("h$it", noon.minusDays(it.toLong()), day()) }
        val reveal = judge(trip(places = day()), history)
        assertEquals(listOf(LevelUp("32030", Level.SILVER)), reveal.levelUps)
        assertTrue(judge(trip(places = day()), history.take(2)).levelUps.isEmpty())
        assertNull(Level.of(0))
        assertEquals(Level.GOLD, Level.of(20))
    }

    @Test
    fun sameDayCountsOnceForLevel() {
        val sameDay = (1..4).map { trip("h$it", noon.minusHours(it.toLong()), day()) }
        assertEquals(1, Rules.visits(sameDay).getValue("32030"))
        assertTrue(judge(trip(places = day()), sameDay).levelUps.isEmpty())
    }

    @Test
    fun momentFollowsWiperSunAndLowBeam() {
        val date = noon.toLocalDate()
        val set = Sun.sunset(date, gangneung.lat, gangneung.lon, seoul)!!.atDate(date).atZone(seoul)
        val rise = Sun.sunrise(date, gangneung.lat, gangneung.lon, seoul)!!.atDate(date).atZone(seoul)
        val day = noon.atZone(seoul)
        assertEquals(Moment.RAIN, Rules.moment(set, gangneung, wiper = true, lowBeam = false))
        assertEquals(Moment.SUNSET, Rules.moment(set.plusMinutes(10), gangneung, wiper = false, lowBeam = false))
        assertEquals(Moment.DAWN, Rules.moment(rise.minusMinutes(40), gangneung, wiper = false, lowBeam = false))
        assertEquals(Moment.NIGHT, Rules.moment(set.plusHours(2), gangneung, wiper = false, lowBeam = false))
        assertEquals(Moment.NIGHT, Rules.moment(day, gangneung, wiper = false, lowBeam = true))
        assertEquals(Moment.DAY, Rules.moment(day, gangneung, wiper = false, lowBeam = false))
    }

    @Test
    fun badgesCountThisTripStamps() {
        val regions = (1..9).map { Stamp("1101$it", Moment.DAY, Season.AUTUMN, noon.minusDays(1)) }
        assertEquals(Progress(9, 10, R.string.progress_places), Rules.progress(Badge.TEN_REGIONS, emptyList(), regions))
        assertEquals(listOf(Badge.TEN_REGIONS), judge(trip(places = day()), stamps = regions).badges.map { it.badge })
    }

    @Test
    fun allMomentsNeedsFiveInOnePlace() {
        val four = listOf(Moment.DAY, Moment.RAIN, Moment.NIGHT, Moment.DAWN).map { Stamp("32030", it, Season.AUTUMN, noon.minusDays(1)) }
        val sunset = trip(places = mapOf("32030" to mapOf(Moment.SUNSET to 2_000.0)))
        assertTrue(Badge.ALL_MOMENTS in judge(sunset, stamps = four).badges.map { it.badge })
        val elsewhere = trip(places = mapOf("32540" to mapOf(Moment.SUNSET to 2_000.0)))
        assertTrue(Badge.ALL_MOMENTS !in judge(elsewhere, stamps = four).badges.map { it.badge })
    }

    @Test
    fun rainDriverAddsWiperDistanceAcrossTrips() {
        val history = listOf(trip("a", wiperM = 60_000.0), trip("b", wiperM = 39_000.0))
        assertTrue(Badge.RAIN_DRIVER !in judge(trip(wiperM = 999.0), history).badges.map { it.badge })
        assertTrue(Badge.RAIN_DRIVER in judge(trip(wiperM = 1_000.0), history).badges.map { it.badge })
    }

    @Test
    fun ownedBadgeIsNotGivenAgain() {
        val regions = (1..9).map { Stamp("1101$it", Moment.DAY, Season.AUTUMN, noon.minusDays(1)) }
        assertTrue(judge(trip(places = day()), stamps = regions, earned = listOf(Earned(Badge.TEN_REGIONS, noon.minusDays(1)))).badges.isEmpty())
    }

    @Test
    fun nearestSkipsStampedAndClosedSpots() {
        val here = coast.start
        assertEquals(coast, Rules.nearest(here, emptyList(), noon.toLocalDate())?.first)
        val stamped = listOf(Stamp(coast.code, Moment.DAY, Season.AUTUMN, noon))
        assertTrue(Rules.nearest(here, stamped, noon.toLocalDate())?.first != coast)
        assertTrue(Rules.nearest(blossom.start, emptyList(), noon.toLocalDate())?.first != blossom)
        assertEquals(blossom, Rules.nearest(blossom.start, emptyList(), LocalDate.of(2027, 4, 10))?.first)
    }

    @Test
    fun samplesShowEveryLevel() {
        val today = LocalDate.of(2026, 11, 8)
        val trips = Rules.sampleTrips(today)
        val visits = Rules.visits(trips)
        assertEquals(Level.GOLD, Level.of(visits.getValue("32030")))
        assertEquals(Level.SILVER, Level.of(visits.getValue("32540")))
        assertEquals(Level.BRONZE, Level.of(visits.getValue("32610")))
        assertTrue(trips.all { it.endedAt.toLocalDate() < today })
        assertEquals(Season.entries.toSet(), trips.filter { "32030" in it.places }.map { Season.of(it.endedAt.toLocalDate()) }.toSet())
    }

    @Test
    fun demoDriveAfterSamplesShowsNewStampsAndLevelUp() {
        val today = LocalDate.of(2026, 11, 13)
        val trips = Rules.sampleTrips(today)
        var stamps = emptyList<Stamp>()
        trips.forEachIndexed { index, trip -> stamps = stamps + judge(trip, trips.take(index), stamps).stamps }
        val night = trip(at = today.atTime(23, 0), places = mapOf("32030" to mapOf(Moment.NIGHT to 7_000.0)), spots = mapOf(coast.code to Moment.NIGHT))
        val reveal = judge(night, trips, stamps)
        assertEquals(setOf("32030", coast.code), reveal.stamps.map { it.code }.toSet())
        assertEquals(listOf(LevelUp(coast.code, Level.SILVER)), reveal.levelUps)
    }

    @Test
    fun monthlyRecapCountsOnlyThatMonth() {
        val november = YearMonth.of(2026, 11)
        val trips = listOf(
            trip("oct", LocalDateTime.of(2026, 10, 31, 23, 50), day("11010", 9_000.0), wiperM = 1_000.0),
            trip("a", LocalDateTime.of(2026, 11, 1, 0, 10), mapOf("32030" to mapOf(Moment.NIGHT to 6_000.0), "32540" to mapOf(Moment.RAIN to 2_000.0)), wiperM = 500.0),
            trip("b", LocalDateTime.of(2026, 11, 1, 18, 0), day("32030"), wiperM = 300.0),
            trip("c", LocalDateTime.of(2026, 11, 30, 23, 59), mapOf("32540" to mapOf(Moment.NIGHT to 1_000.0, Moment.DAY to 1_000.0))),
        )
        val stamps = listOf(
            Stamp("11010", Moment.DAY, Season.AUTUMN, LocalDateTime.of(2026, 10, 31, 23, 50)),
            Stamp("32030", Moment.NIGHT, Season.AUTUMN, LocalDateTime.of(2026, 11, 1, 0, 10)),
            Stamp("32540", Moment.RAIN, Season.AUTUMN, LocalDateTime.of(2026, 11, 1, 0, 10)),
            Stamp("32030", Moment.DAY, Season.AUTUMN, LocalDateTime.of(2026, 11, 1, 18, 0)),
        )
        val earned = listOf(Earned(Badge.RAIN_DRIVER, LocalDateTime.of(2026, 10, 31, 23, 50)), Earned(Badge.TEN_REGIONS, LocalDateTime.of(2026, 11, 30, 23, 59)))
        val recap = Rules.recap(november, trips, stamps, earned)
        assertEquals(12_000.0, recap.distanceM, 0.1)
        assertEquals(3, recap.newStamps)
        assertEquals("32030", recap.topRegion)
        assertEquals(1, recap.rainyDays)
        assertEquals(7_000.0, recap.nightM, 0.1)
        assertEquals(listOf(Badge.TEN_REGIONS), recap.badges.map { it.badge })
    }

    @Test
    fun replayPathPassesSpotEnds() {
        Rules.routes.forEach { route ->
            val path = Rules.path(route)
            path.zipWithNext().forEach { (a, b) -> assertTrue(Rules.distanceM(a, b) <= Rules.REPLAY_STEP_M + 1) }
            Rules.spots.filter { spot -> route.waypoints.contains(spot.start) }.forEach { spot ->
                assertTrue(path.any { Rules.distanceM(it, spot.end) < Rules.SPOT_RADIUS_M })
            }
        }
    }
}
