package com.example.chagok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class RulesTest {

    private val noon = LocalDateTime.of(2026, 11, 5, 12, 0)
    private val coast = Rules.spots.first { it.kind == SpotKind.COAST }
    private val pass = Rules.spots.first { it.kind == SpotKind.PASS }
    private val seoul = ZoneId.of("Asia/Seoul")
    private val gangneung = Point(37.7519, 128.8761)

    private fun recorder(sunrise: Boolean = false) = Recorder("t", noon.minusMinutes(30), sunrise)

    private fun Recorder.drive(meters: Double, region: String? = "32030", wiper: Boolean = false, window: Boolean = false, lowBeam: Boolean = false, steps: Int = 10) =
        repeat(steps) { move(meters / steps, region, wiper, window, lowBeam, false) }

    private fun trip(id: String = "t", at: LocalDateTime = noon, regionM: Map<String, Double> = emptyMap(), wiperM: Double = 0.0, lowBeamM: Double = 0.0, distanceM: Double = regionM.values.sum()) =
        Trip(id, at.minusMinutes(30), at, distanceM, regionM, wiperM, lowBeamM, 0.0, emptyList(), emptyList(), false, false)

    private fun judge(trip: Trip, history: List<Trip> = emptyList(), stamps: List<Stamp> = emptyList(), earned: List<Earned> = emptyList()) =
        Rules.judge(trip, history, stamps, earned)

    @Test
    fun regionStampNeeds500Meters() {
        val short = recorder().apply { drive(499.0) }.snapshot(noon)
        assertTrue(judge(short).stamps.isEmpty())
        val long = recorder().apply { drive(520.0) }.snapshot(noon)
        assertEquals(listOf("32030"), judge(long).stamps.map { it.code })
    }

    @Test
    fun revisitedRegionIsNotStampedAgain() {
        val trip = recorder().apply { drive(2_000.0) }.snapshot(noon)
        assertTrue(judge(trip, stamps = listOf(Stamp("32030", noon.minusDays(3)))).stamps.isEmpty())
    }

    @Test
    fun spotStampNeedsStartThenEnd() {
        val inOrder = recorder().apply {
            enter(coast, atEnd = false, lowBeam = false)
            drive(100.0)
            enter(coast, atEnd = true, lowBeam = false)
        }.snapshot(noon)
        assertTrue(coast.code in judge(inOrder).stamps.map { it.code })
        val reversed = recorder().apply {
            enter(coast, atEnd = true, lowBeam = false)
            enter(coast, atEnd = false, lowBeam = false)
        }.snapshot(noon)
        assertTrue(judge(reversed).stamps.isEmpty())
    }

    @Test
    fun coastWindNeedsOpenWindowForOneKilometerInsideSection() {
        val outside = recorder().apply { drive(3_000.0, window = true) }.snapshot(noon)
        assertTrue(judge(outside).badges.isEmpty())
        val inside = recorder().apply {
            enter(coast, atEnd = false, lowBeam = false)
            drive(600.0, window = true)
            drive(2_000.0)
            drive(500.0, window = true)
            enter(coast, atEnd = true, lowBeam = false)
        }.snapshot(noon)
        assertEquals(listOf(Badge.COAST_WIND), judge(inside).badges.map { it.badge })
    }

    @Test
    fun rainDriverAddsWiperDistanceAcrossTrips() {
        val history = listOf(trip("a", wiperM = 60_000.0), trip("b", wiperM = 39_000.0))
        assertTrue(judge(trip(wiperM = 999.0), history).badges.isEmpty())
        assertEquals(listOf(Badge.RAIN_DRIVER), judge(trip(wiperM = 1_000.0), history).badges.map { it.badge })
    }

    @Test
    fun nightPassNeedsLowBeamThroughWholeSection() {
        val lit = recorder().apply {
            enter(pass, atEnd = false, lowBeam = true)
            drive(5_000.0, lowBeam = true)
            enter(pass, atEnd = true, lowBeam = true)
        }.snapshot(noon)
        assertEquals(listOf(Badge.NIGHT_PASS), judge(lit).badges.map { it.badge })
        val dark = recorder().apply {
            enter(pass, atEnd = false, lowBeam = true)
            drive(2_000.0, lowBeam = true)
            drive(100.0)
            drive(2_000.0, lowBeam = true)
            enter(pass, atEnd = true, lowBeam = true)
        }.snapshot(noon)
        assertTrue(judge(dark).badges.isEmpty())
    }

    @Test
    fun singleTripBadgesCountOneDrive() {
        assertTrue(judge(trip(wiperM = 9_999.0, lowBeamM = 29_999.0, distanceM = 99_999.0)).badges.isEmpty())
        val badges = judge(trip(wiperM = 10_000.0, lowBeamM = 30_000.0, distanceM = 100_000.0)).badges.map { it.badge }
        assertEquals(listOf(Badge.SHOWER, Badge.NIGHT_RUN, Badge.LONG_DRIVE), badges)
    }

    @Test
    fun collectionBadgesCountThisTripStamps() {
        val coasts = Rules.spots.filter { it.kind == SpotKind.COAST }
        val regions = (1..9).map { Stamp("1101$it", noon.minusDays(1)) } + coasts.take(2).map { Stamp(it.code, noon.minusDays(1)) }
        assertEquals(Progress(9, 10, R.string.progress_places), Rules.progress(Badge.TEN_REGIONS, emptyList(), regions))
        val trip = trip(regionM = mapOf("32030" to 1_000.0)).copy(spots = listOf(coasts[2].code))
        assertEquals(listOf(Badge.TEN_REGIONS, Badge.COAST_SPOTS), judge(trip, stamps = regions).badges.map { it.badge })
    }

    @Test
    fun ownedBadgeIsNotGivenAgain() {
        val trip = recorder(sunrise = true).snapshot(noon)
        assertEquals(listOf(Badge.SUNRISE_START), judge(trip).badges.map { it.badge })
        assertTrue(judge(trip, earned = listOf(Earned(Badge.SUNRISE_START, noon.minusDays(1)))).badges.isEmpty())
    }

    @Test
    fun sunriseStartIsTheHourBeforeSunrise() {
        val rise = Sun.sunrise(noon.toLocalDate(), gangneung.lat, gangneung.lon, seoul)!!.atDate(noon.toLocalDate())
        assertTrue(Rules.isSunriseStart(rise.minusMinutes(20), gangneung, seoul))
        assertFalse(Rules.isSunriseStart(rise.minusMinutes(90), gangneung, seoul))
        assertFalse(Rules.isSunriseStart(rise.plusMinutes(5), gangneung, seoul))
    }

    @Test
    fun sunsetDriveIsFifteenMinutesAroundSunset() {
        val set = Sun.sunset(noon.toLocalDate(), gangneung.lat, gangneung.lon, seoul)!!.atDate(noon.toLocalDate())
        assertTrue(Rules.isNearSunset(set.minusMinutes(15), gangneung, seoul))
        assertTrue(Rules.isNearSunset(set.plusMinutes(10), gangneung, seoul))
        assertFalse(Rules.isNearSunset(set.plusMinutes(16), gangneung, seoul))
        assertFalse(Rules.isNearSunset(noon, gangneung, seoul))
    }

    @Test
    fun monthlyRecapCountsOnlyThatMonth() {
        val november = YearMonth.of(2026, 11)
        val trips = listOf(
            trip("oct", LocalDateTime.of(2026, 10, 31, 23, 50), mapOf("11010" to 9_000.0), wiperM = 1_000.0),
            trip("a", LocalDateTime.of(2026, 11, 1, 0, 10), mapOf("32030" to 6_000.0, "32540" to 2_000.0), wiperM = 500.0, lowBeamM = 2_000.0),
            trip("b", LocalDateTime.of(2026, 11, 1, 18, 0), mapOf("32030" to 2_000.0), wiperM = 300.0),
            trip("c", LocalDateTime.of(2026, 11, 30, 23, 59), mapOf("32540" to 2_000.0), lowBeamM = 1_000.0),
        )
        val stamps = listOf(
            Stamp("11010", LocalDateTime.of(2026, 10, 31, 23, 50)),
            Stamp("32030", LocalDateTime.of(2026, 11, 1, 0, 10)),
            Stamp("32540", LocalDateTime.of(2026, 11, 1, 0, 10)),
            Stamp(coast.code, LocalDateTime.of(2026, 11, 1, 0, 10)),
        )
        val earned = listOf(Earned(Badge.RAIN_DRIVER, LocalDateTime.of(2026, 10, 31, 23, 50)), Earned(Badge.NIGHT_PASS, LocalDateTime.of(2026, 11, 30, 23, 59)))
        val recap = Rules.recap(november, trips, stamps, earned)
        assertEquals(12_000.0, recap.distanceM, 0.1)
        assertEquals(3, recap.newStamps)
        assertEquals("32030", recap.topRegion)
        assertEquals(1, recap.rainyDays)
        assertEquals(25, recap.nightPercent)
        assertEquals(listOf(Badge.NIGHT_PASS), recap.badges.map { it.badge })
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
