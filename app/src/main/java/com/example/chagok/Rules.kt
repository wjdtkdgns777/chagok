package com.example.chagok

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class SpotKind { COAST, PASS }

data class Spot(val code: String, @StringRes val name: Int, val kind: SpotKind, val start: Point, val end: Point)

data class Point(val lat: Double, val lon: Double)

data class Route(@StringRes val name: Int, @StringRes val detail: Int, val waypoints: List<Point>)

enum class Badge(@StringRes val title: Int, @StringRes val rule: Int, @DrawableRes val icon: Int) {
    COAST_WIND(R.string.badge_coast_wind, R.string.badge_coast_wind_rule, R.drawable.badge_coast_wind),
    NIGHT_PASS(R.string.badge_night_pass, R.string.badge_night_pass_rule, R.drawable.badge_night_pass),
    SUNRISE_START(R.string.badge_sunrise_start, R.string.badge_sunrise_start_rule, R.drawable.badge_sunrise_start),
    SUNSET_DRIVE(R.string.badge_sunset_drive, R.string.badge_sunset_drive_rule, R.drawable.badge_sunset_drive),
    SHOWER(R.string.badge_shower, R.string.badge_shower_rule, R.drawable.badge_shower),
    NIGHT_RUN(R.string.badge_night_run, R.string.badge_night_run_rule, R.drawable.badge_night_run),
    LONG_DRIVE(R.string.badge_long_drive, R.string.badge_long_drive_rule, R.drawable.badge_long_drive),
    RAIN_DRIVER(R.string.badge_rain_driver, R.string.badge_rain_driver_rule, R.drawable.badge_rain_driver),
    THOUSAND_KM(R.string.badge_thousand_km, R.string.badge_thousand_km_rule, R.drawable.badge_thousand_km),
    TEN_REGIONS(R.string.badge_ten_regions, R.string.badge_ten_regions_rule, R.drawable.badge_ten_regions),
    COAST_SPOTS(R.string.badge_coast_spots, R.string.badge_coast_spots_rule, R.drawable.badge_coast_spots),
    PASS_SPOTS(R.string.badge_pass_spots, R.string.badge_pass_spots_rule, R.drawable.badge_pass_spots),
}

/** 여러 주행에 걸쳐 모으는 배지의 진행도. 단위는 km 또는 곳입니다. */
data class Progress(val done: Int, val goal: Int, @StringRes val format: Int)

data class Trip(
    val id: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val distanceM: Double,
    val regionM: Map<String, Double>,
    val wiperM: Double,
    val lowBeamM: Double,
    val coastWindowM: Double,
    val spots: List<String>,
    val litPasses: List<String>,
    val sunrise: Boolean,
    val sunset: Boolean,
)

data class Stamp(val code: String, val at: LocalDateTime)

data class Earned(val badge: Badge, val at: LocalDateTime)

data class Reveal(val stamps: List<Stamp>, val badges: List<Earned>)

data class Recap(
    val distanceM: Double,
    val newStamps: Int,
    val topRegion: String?,
    val rainyDays: Int,
    val nightPercent: Int,
    val badges: List<Earned>,
)

class Recorder(private val id: String, private val startedAt: LocalDateTime, private val sunrise: Boolean) {

    private var distanceM = 0.0
    private val regionM = mutableMapOf<String, Double>()
    private var wiperM = 0.0
    private var lowBeamM = 0.0
    private var coastWindowM = 0.0
    private val spots = mutableListOf<String>()
    private val litPasses = mutableListOf<String>()
    private var sunset = false
    private var section: Spot? = null
    private var sectionLit = false

    fun move(meters: Double, region: String?, wiper: Boolean, window: Boolean, lowBeam: Boolean, nearSunset: Boolean) {
        distanceM += meters
        if (region != null) regionM[region] = (regionM[region] ?: 0.0) + meters
        if (wiper) wiperM += meters
        if (lowBeam) lowBeamM += meters
        if (nearSunset) sunset = true
        val spot = section ?: return
        if (spot.kind == SpotKind.COAST && window) coastWindowM += meters
        if (!lowBeam) sectionLit = false
    }

    fun enter(spot: Spot, atEnd: Boolean, lowBeam: Boolean) {
        if (!atEnd) {
            section = spot
            sectionLit = lowBeam
            return
        }
        if (section != spot) return
        spots += spot.code
        if (spot.kind == SpotKind.PASS && sectionLit && lowBeam) litPasses += spot.code
        section = null
    }

    fun snapshot(endedAt: LocalDateTime) = Trip(id, startedAt, endedAt, distanceM, regionM.toMap(), wiperM, lowBeamM, coastWindowM, spots.toList(), litPasses.toList(), sunrise, sunset)
}

object Rules {

    const val REGION_STAMP_M = 500.0
    const val SHOWER_M = 10_000.0
    const val NIGHT_RUN_M = 30_000.0
    const val LONG_DRIVE_M = 100_000.0
    private const val RAIN_DRIVER_KM = 100
    private const val THOUSAND_KM = 1_000
    private const val TEN_REGIONS = 10
    private const val SPOTS_GOAL = 3
    const val COAST_WINDOW_M = 1_000.0
    const val SPOT_RADIUS_M = 300f
    const val SUNSET_WINDOW_MINUTES = 15L
    const val SUNRISE_WINDOW_MINUTES = 60L
    const val REPLAY_STEP_M = 100.0

    val spots = listOf(
        Spot("S-GN-COAST", R.string.spot_gangneung_coast, SpotKind.COAST, Point(37.7930, 128.9150), Point(37.8380, 128.8660)),
        Spot("S-DGR-PASS", R.string.spot_daegwallyeong, SpotKind.PASS, Point(37.6886, 128.7550), Point(37.7180, 128.8350)),
        Spot("S-HWD-COAST", R.string.spot_heonhwa, SpotKind.COAST, Point(37.6880, 129.0350), Point(37.6650, 129.0530)),
        Spot("S-MSR-PASS", R.string.spot_misiryeong, SpotKind.PASS, Point(38.2120, 128.3990), Point(38.2190, 128.4720)),
        Spot("S-HGR-PASS", R.string.spot_hangyeryeong, SpotKind.PASS, Point(38.1000, 128.3700), Point(38.0870, 128.4520)),
        Spot("S-DMJ-COAST", R.string.spot_dalmaji, SpotKind.COAST, Point(35.1600, 129.1700), Point(35.1550, 129.1830)),
        Spot("S-AW-COAST", R.string.spot_aewol, SpotKind.COAST, Point(33.4660, 126.3330), Point(33.4400, 126.2900)),
        Spot("S-MM-COAST", R.string.spot_mulmi, SpotKind.COAST, Point(34.8100, 128.0400), Point(34.7700, 128.0450)),
    )

    val routes = listOf(
        Route(R.string.route_gangneung_coast, R.string.route_gangneung_coast_detail, listOf(Point(37.7850, 128.9150), Point(37.7930, 128.9150), Point(37.8050, 128.9040), Point(37.8200, 128.8870), Point(37.8380, 128.8660))),
        Route(R.string.route_daegwallyeong, R.string.route_daegwallyeong_detail, listOf(Point(37.6770, 128.7090), Point(37.6886, 128.7550), Point(37.6990, 128.7800), Point(37.7180, 128.8350))),
    )

    fun path(route: Route): List<Point> = route.waypoints.zipWithNext().flatMap { (a, b) ->
        val steps = ceil(distanceM(a, b) / REPLAY_STEP_M).toInt().coerceAtLeast(1)
        (0 until steps).map { Point(a.lat + (b.lat - a.lat) * it / steps, a.lon + (b.lon - a.lon) * it / steps) }
    } + route.waypoints.last()

    fun spot(code: String) = spots.firstOrNull { it.code == code }

    fun distanceM(a: Point, b: Point): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val h = sin((p2 - p1) / 2).pow(2) + cos(p1) * cos(p2) * sin(Math.toRadians(b.lon - a.lon) / 2).pow(2)
        return 2 * EARTH_M * asin(sqrt(h))
    }

    fun isSunriseStart(at: LocalDateTime, point: Point, zone: ZoneId): Boolean {
        val rise = Sun.sunrise(at.toLocalDate(), point.lat, point.lon, zone) ?: return false
        val time = at.toLocalTime()
        return time.isBefore(rise) && !time.isBefore(rise.minusMinutes(SUNRISE_WINDOW_MINUTES))
    }

    fun isNearSunset(at: LocalDateTime, point: Point, zone: ZoneId): Boolean {
        val set = Sun.sunset(at.toLocalDate(), point.lat, point.lon, zone) ?: return false
        val time = at.toLocalTime()
        return !time.isBefore(set.minusMinutes(SUNSET_WINDOW_MINUTES)) && !time.isAfter(set.plusMinutes(SUNSET_WINDOW_MINUTES))
    }

    fun judge(trip: Trip, history: List<Trip>, stamps: List<Stamp>, earned: List<Earned>): Reveal {
        val at = trip.endedAt
        val stamped = stamps.map { it.code }.toSet()
        val newStamps = (trip.regionM.filterValues { it >= REGION_STAMP_M }.keys + trip.spots)
            .distinct()
            .filter { it !in stamped }
            .map { Stamp(it, at) }
        val owned = earned.map { it.badge }.toSet()
        val newBadges = Badge.entries.filter { badge ->
            badge !in owned && when (badge) {
                Badge.COAST_WIND -> trip.coastWindowM >= COAST_WINDOW_M
                Badge.NIGHT_PASS -> trip.litPasses.isNotEmpty()
                Badge.SUNRISE_START -> trip.sunrise
                Badge.SUNSET_DRIVE -> trip.sunset
                Badge.SHOWER -> trip.wiperM >= SHOWER_M
                Badge.NIGHT_RUN -> trip.lowBeamM >= NIGHT_RUN_M
                Badge.LONG_DRIVE -> trip.distanceM >= LONG_DRIVE_M
                else -> progress(badge, history + trip, stamps + newStamps)!!.let { it.done >= it.goal }
            }
        }.map { Earned(it, at) }
        return Reveal(newStamps, newBadges)
    }

    /** 여러 주행에 걸쳐 모으는 배지만 진행도가 있습니다. */
    fun progress(badge: Badge, trips: List<Trip>, stamps: List<Stamp>): Progress? = when (badge) {
        Badge.RAIN_DRIVER -> Progress((trips.sumOf { it.wiperM } / 1000).toInt(), RAIN_DRIVER_KM, R.string.progress_km)
        Badge.THOUSAND_KM -> Progress((trips.sumOf { it.distanceM } / 1000).toInt(), THOUSAND_KM, R.string.progress_km)
        Badge.TEN_REGIONS -> Progress(stamps.count { spot(it.code) == null }, TEN_REGIONS, R.string.progress_places)
        Badge.COAST_SPOTS -> Progress(stamps.count { spot(it.code)?.kind == SpotKind.COAST }, SPOTS_GOAL, R.string.progress_places)
        Badge.PASS_SPOTS -> Progress(stamps.count { spot(it.code)?.kind == SpotKind.PASS }, SPOTS_GOAL, R.string.progress_places)
        else -> null
    }

    fun recap(month: YearMonth, trips: List<Trip>, stamps: List<Stamp>, earned: List<Earned>): Recap {
        val inMonth = trips.filter { YearMonth.from(it.endedAt) == month }
        val distance = inMonth.sumOf { it.distanceM }
        val regions = inMonth.flatMap { it.regionM.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        return Recap(
            distanceM = distance,
            newStamps = stamps.count { YearMonth.from(it.at) == month },
            topRegion = regions.maxByOrNull { it.value }?.key,
            rainyDays = inMonth.filter { it.wiperM > 0 }.map { it.endedAt.toLocalDate() }.distinct().size,
            nightPercent = if (distance > 0) (inMonth.sumOf { it.lowBeamM } * 100 / distance).toInt() else 0,
            badges = earned.filter { YearMonth.from(it.at) == month },
        )
    }

    private const val EARTH_M = 6_371_000.0
}
