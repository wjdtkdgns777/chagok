package com.example.chagok

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZonedDateTime
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 도장을 받은 순간. 같은 곳도 순간이 다르면 다른 도장입니다. */
enum class Moment(@StringRes val label: Int, @StringRes val named: Int, @DrawableRes val icon: Int) {
    DAY(R.string.moment_day, R.string.named_day, R.drawable.moment_day),
    RAIN(R.string.moment_rain, R.string.named_rain, R.drawable.moment_rain),
    NIGHT(R.string.moment_night, R.string.named_night, R.drawable.moment_night),
    DAWN(R.string.moment_dawn, R.string.named_dawn, R.drawable.moment_dawn),
    SUNSET(R.string.moment_sunset, R.string.named_sunset, R.drawable.moment_sunset),
}

enum class Season(@StringRes val label: Int) {
    SPRING(R.string.season_spring),
    SUMMER(R.string.season_summer),
    AUTUMN(R.string.season_autumn),
    WINTER(R.string.season_winter),
    ;

    companion object {
        fun of(date: LocalDate) = when (date.monthValue) {
            in 3..5 -> SPRING
            in 6..8 -> SUMMER
            in 9..11 -> AUTUMN
            else -> WINTER
        }
    }
}

/** 같은 곳을 지난 날수로 올라가는 등급 */
enum class Level(val visits: Int, @StringRes val label: Int) {
    BRONZE(1, R.string.level_bronze),
    SILVER(5, R.string.level_silver),
    GOLD(20, R.string.level_gold),
    ;

    companion object {
        fun of(visits: Int) = entries.lastOrNull { visits >= it.visits }
    }
}

enum class SpotKind(@StringRes val label: Int) {
    COAST(R.string.spot_coast),
    PASS(R.string.spot_pass),
    BLOSSOM(R.string.spot_blossom),
    FOLIAGE(R.string.spot_foliage),
    SUNRISE(R.string.spot_sunrise),
}

/** 명소 구간. 기간이 있으면 그 기간에만 도장이 찍힙니다. */
data class Spot(val code: String, @StringRes val name: Int, val kind: SpotKind, val start: Point, val end: Point, val open: ClosedRange<MonthDay>? = null) {
    fun isOpen(date: LocalDate) = open?.contains(MonthDay.from(date)) ?: true
}

data class Point(val lat: Double, val lon: Double)

data class Route(@StringRes val name: Int, @StringRes val detail: Int, val waypoints: List<Point>)

/** 오래 쌓아야 받는 큰 목표 */
enum class Badge(@StringRes val title: Int, @StringRes val rule: Int, @DrawableRes val icon: Int) {
    TEN_REGIONS(R.string.badge_ten_regions, R.string.badge_ten_regions_rule, R.drawable.badge_ten_regions),
    FIFTY_REGIONS(R.string.badge_fifty_regions, R.string.badge_fifty_regions_rule, R.drawable.badge_fifty_regions),
    GANGWON(R.string.badge_gangwon, R.string.badge_gangwon_rule, R.drawable.badge_gangwon),
    THOUSAND_KM(R.string.badge_thousand_km, R.string.badge_thousand_km_rule, R.drawable.badge_thousand_km),
    RAIN_DRIVER(R.string.badge_rain_driver, R.string.badge_rain_driver_rule, R.drawable.badge_rain_driver),
    COAST_SPOTS(R.string.badge_coast_spots, R.string.badge_coast_spots_rule, R.drawable.badge_coast_spots),
    PASS_SPOTS(R.string.badge_pass_spots, R.string.badge_pass_spots_rule, R.drawable.badge_pass_spots),
    LIMITED(R.string.badge_limited, R.string.badge_limited_rule, R.drawable.badge_limited),
    ALL_MOMENTS(R.string.badge_all_moments, R.string.badge_all_moments_rule, R.drawable.badge_all_moments),
    FOUR_SEASONS(R.string.badge_four_seasons, R.string.badge_four_seasons_rule, R.drawable.badge_four_seasons),
    FULL_BOOK(R.string.badge_full_book, R.string.badge_full_book_rule, R.drawable.badge_full_book),
    REGULAR(R.string.badge_regular, R.string.badge_regular_rule, R.drawable.badge_regular),
}

/** 배지까지의 진행도. 단위 문구는 km, 곳, 가지, 칸, 번 가운데 하나입니다. */
data class Progress(val done: Int, val goal: Int, @StringRes val format: Int)

/**
 * 한 번의 주행 기록.
 * [places]는 시군구마다 순간별로 달린 거리, [spots]는 끝까지 지난 명소와 그때의 순간입니다.
 */
data class Trip(
    val id: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val distanceM: Double,
    val places: Map<String, Map<Moment, Double>>,
    val wiperM: Double,
    val spots: Map<String, Moment>,
) {
    /** 시군구마다 이번에 가장 오래 달린 순간. 도장 거리를 넘긴 곳만 들어갑니다. */
    val regions get() = places.filterValues { it.values.sum() >= Rules.REGION_STAMP_M }.mapValues { it.value.maxBy { m -> m.value }.key }

    /** 이번에 지난 시군구와 명소. 기간이 지난 명소는 뺍니다. */
    val passed get() = regions + spots.filterKeys { Rules.spot(it)?.isOpen(endedAt.toLocalDate()) == true }

    /** 이번 주행의 순간을 오래 달린 순서로 */
    val moments get() = places.values.flatMap { it.entries }.groupBy({ it.key }, { it.value }).entries.sortedByDescending { it.value.sum() }.map { it.key }
}

/** 장소, 순간, 계절이 같으면 같은 도장입니다. */
data class Stamp(val code: String, val moment: Moment, val season: Season, val at: LocalDateTime)

data class Earned(val badge: Badge, val at: LocalDateTime)

data class LevelUp(val code: String, val level: Level)

data class Reveal(val trip: Trip, val stamps: List<Stamp>, val levelUps: List<LevelUp>, val badges: List<Earned>) {
    val isEmpty get() = stamps.isEmpty() && levelUps.isEmpty() && badges.isEmpty()
}

data class Recap(
    val distanceM: Double,
    val newStamps: Int,
    val topRegion: String?,
    val rainyDays: Int,
    val nightM: Double,
    val badges: List<Earned>,
)

class Recorder(private val id: String, private val startedAt: LocalDateTime) {

    private var distanceM = 0.0
    private val places = mutableMapOf<String, MutableMap<Moment, Double>>()
    private var wiperM = 0.0
    private val spots = mutableMapOf<String, Moment>()
    private var section: Spot? = null

    fun move(meters: Double, region: String?, moment: Moment, wiper: Boolean) {
        distanceM += meters
        if (region != null) places.getOrPut(region) { mutableMapOf() }.merge(moment, meters, Double::plus)
        if (wiper) wiperM += meters
    }

    /** 명소는 시작점을 지난 뒤 끝점에 닿아야 도장이 찍히고, 끝점에서의 순간을 씁니다. */
    fun enter(spot: Spot, atEnd: Boolean, moment: Moment) {
        if (!atEnd) {
            section = spot
            return
        }
        if (section != spot) return
        spots[spot.code] = moment
        section = null
    }

    fun snapshot(endedAt: LocalDateTime) = Trip(id, startedAt, endedAt, distanceM, places.mapValues { it.value.toMap() }, wiperM, spots.toMap())
}

object Rules {

    const val REGION_STAMP_M = 500.0
    const val SPOT_RADIUS_M = 300f
    const val SUNSET_WINDOW_MINUTES = 15L
    const val DAWN_BEFORE_MINUTES = 60L
    const val DAWN_AFTER_MINUTES = 30L
    const val REPLAY_STEP_M = 100.0
    const val GANGWON_PREFIX = "32"
    private const val GANGWON_COUNT = 18
    private const val TEN_REGIONS = 10
    private const val FIFTY_REGIONS = 50
    private const val THOUSAND_KM = 1_000
    private const val RAIN_DRIVER_KM = 100
    private const val SPOTS_GOAL = 3

    val spots = listOf(
        Spot("S-GN-COAST", R.string.spot_gangneung_coast, SpotKind.COAST, Point(37.7930, 128.9150), Point(37.8380, 128.8660)),
        Spot("S-DGR-PASS", R.string.spot_daegwallyeong, SpotKind.PASS, Point(37.6886, 128.7550), Point(37.7180, 128.8350)),
        Spot("S-HWD-COAST", R.string.spot_heonhwa, SpotKind.COAST, Point(37.6880, 129.0350), Point(37.6650, 129.0530)),
        Spot("S-MSR-PASS", R.string.spot_misiryeong, SpotKind.PASS, Point(38.2120, 128.3990), Point(38.2190, 128.4720)),
        Spot("S-HGR-PASS", R.string.spot_hangyeryeong, SpotKind.PASS, Point(38.1000, 128.3700), Point(38.0870, 128.4520)),
        Spot("S-DMJ-COAST", R.string.spot_dalmaji, SpotKind.COAST, Point(35.1600, 129.1700), Point(35.1550, 129.1830)),
        Spot("S-AW-COAST", R.string.spot_aewol, SpotKind.COAST, Point(33.4660, 126.3330), Point(33.4400, 126.2900)),
        Spot("S-MM-COAST", R.string.spot_mulmi, SpotKind.COAST, Point(34.8100, 128.0400), Point(34.7700, 128.0450)),
        Spot("S-YJR-BLOSSOM", R.string.spot_yunjungro, SpotKind.BLOSSOM, Point(37.5318, 126.9147), Point(37.5262, 126.9289), MonthDay.of(4, 1)..MonthDay.of(4, 30)),
        Spot("S-NJS-FOLIAGE", R.string.spot_naejangsan, SpotKind.FOLIAGE, Point(35.4850, 126.8850), Point(35.4920, 126.9010), MonthDay.of(10, 15)..MonthDay.of(11, 15)),
        Spot("S-HMG-SUNRISE", R.string.spot_homigot, SpotKind.SUNRISE, Point(36.0730, 129.5520), Point(36.0770, 129.5670), MonthDay.of(1, 1)..MonthDay.of(1, 1)),
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

    /** 비가 가장 앞서고, 그다음 노을, 새벽, 밤 순서로 봅니다. 전조등을 켜면 밤으로 칩니다. */
    fun moment(at: ZonedDateTime, point: Point, wiper: Boolean, lowBeam: Boolean): Moment {
        if (wiper) return Moment.RAIN
        val date = at.toLocalDate()
        val time = at.toLocalTime()
        val set = Sun.sunset(date, point.lat, point.lon, at.zone)
        if (set != null && !time.isBefore(set.minusMinutes(SUNSET_WINDOW_MINUTES)) && !time.isAfter(set.plusMinutes(SUNSET_WINDOW_MINUTES))) return Moment.SUNSET
        val rise = Sun.sunrise(date, point.lat, point.lon, at.zone)
        if (rise != null && !time.isBefore(rise.minusMinutes(DAWN_BEFORE_MINUTES)) && !time.isAfter(rise.plusMinutes(DAWN_AFTER_MINUTES))) return Moment.DAWN
        if (lowBeam || Sun.isNight(at, point.lat, point.lon)) return Moment.NIGHT
        return Moment.DAY
    }

    /** 장소마다 지난 날수. 같은 날 여러 번 지나도 하루로 셉니다. */
    fun visits(trips: List<Trip>): Map<String, Int> =
        trips.flatMap { trip -> trip.passed.keys.map { it to trip.endedAt.toLocalDate() } }.distinct().groupingBy { it.first }.eachCount()

    fun judge(trip: Trip, history: List<Trip>, stamps: List<Stamp>, earned: List<Earned>): Reveal {
        val at = trip.endedAt
        val season = Season.of(at.toLocalDate())
        val owned = stamps.map { Triple(it.code, it.moment, it.season) }.toSet()
        val newStamps = trip.passed.filter { (code, moment) -> Triple(code, moment, season) !in owned }.map { (code, moment) -> Stamp(code, moment, season, at) }
        val before = visits(history)
        val after = visits(history + trip)
        val levelUps = trip.passed.keys.mapNotNull { code ->
            val level = Level.of(after[code] ?: 0)
            if (level != null && level != Level.BRONZE && level != Level.of(before[code] ?: 0)) LevelUp(code, level) else null
        }
        val have = earned.map { it.badge }.toSet()
        val newBadges = Badge.entries
            .filter { it !in have && progress(it, history + trip, stamps + newStamps).let { p -> p.done >= p.goal } }
            .map { Earned(it, at) }
        return Reveal(trip, newStamps, levelUps, newBadges)
    }

    fun progress(badge: Badge, trips: List<Trip>, stamps: List<Stamp>): Progress {
        val regions = stamps.map { it.code }.filter { spot(it) == null }.toSet()
        val byPlace = stamps.groupBy { it.code }
        fun spotsOf(kinds: Set<SpotKind>) = stamps.map { it.code }.toSet().count { spot(it)?.kind in kinds }
        return when (badge) {
            Badge.TEN_REGIONS -> Progress(regions.size, TEN_REGIONS, R.string.progress_places)
            Badge.FIFTY_REGIONS -> Progress(regions.size, FIFTY_REGIONS, R.string.progress_places)
            Badge.GANGWON -> Progress(regions.count { it.startsWith(GANGWON_PREFIX) }, GANGWON_COUNT, R.string.progress_places)
            Badge.THOUSAND_KM -> Progress((trips.sumOf { it.distanceM } / 1000).toInt(), THOUSAND_KM, R.string.progress_km)
            Badge.RAIN_DRIVER -> Progress((trips.sumOf { it.wiperM } / 1000).toInt(), RAIN_DRIVER_KM, R.string.progress_km)
            Badge.COAST_SPOTS -> Progress(spotsOf(setOf(SpotKind.COAST)), SPOTS_GOAL, R.string.progress_places)
            Badge.PASS_SPOTS -> Progress(spotsOf(setOf(SpotKind.PASS)), SPOTS_GOAL, R.string.progress_places)
            Badge.LIMITED -> Progress(spotsOf(setOf(SpotKind.BLOSSOM, SpotKind.FOLIAGE, SpotKind.SUNRISE)), 1, R.string.progress_places)
            Badge.ALL_MOMENTS -> Progress(byPlace.values.maxOfOrNull { s -> s.map { it.moment }.toSet().size } ?: 0, Moment.entries.size, R.string.progress_kinds)
            Badge.FOUR_SEASONS -> Progress(byPlace.values.maxOfOrNull { s -> s.map { it.season }.toSet().size } ?: 0, Season.entries.size, R.string.progress_kinds)
            Badge.FULL_BOOK -> Progress(byPlace.values.maxOfOrNull { it.size } ?: 0, Moment.entries.size * Season.entries.size, R.string.progress_cells)
            Badge.REGULAR -> Progress(visits(trips).values.maxOrNull() ?: 0, Level.GOLD.visits, R.string.progress_days)
        }
    }

    /** 정차 중 가까운 곳 가운데 아직 도장이 하나도 없고, 오늘 찍을 수 있는 명소 */
    fun nearest(here: Point, stamps: List<Stamp>, date: LocalDate): Pair<Spot, Double>? {
        val stamped = stamps.map { it.code }.toSet()
        return spots.filter { it.code !in stamped && it.isOpen(date) }
            .map { it to distanceM(here, it.start) }
            .minByOrNull { it.second }
    }

    fun recap(month: YearMonth, trips: List<Trip>, stamps: List<Stamp>, earned: List<Earned>): Recap {
        val inMonth = trips.filter { YearMonth.from(it.endedAt) == month }
        val regions = inMonth.flatMap { it.places.entries }.groupBy({ it.key }, { it.value.values.sum() }).mapValues { it.value.sum() }
        return Recap(
            distanceM = inMonth.sumOf { it.distanceM },
            newStamps = stamps.count { YearMonth.from(it.at) == month },
            topRegion = regions.maxByOrNull { it.value }?.key,
            rainyDays = inMonth.filter { it.wiperM > 0 }.map { it.endedAt.toLocalDate() }.distinct().size,
            nightM = inMonth.sumOf { trip -> trip.places.values.sumOf { it[Moment.NIGHT] ?: 0.0 } },
            badges = earned.filter { YearMonth.from(it.at) == month },
        )
    }

    /**
     * 시연용 지난 기록. 강원 여러 곳을 1년에 걸쳐 지나서 동, 은, 금 등급과 지도 농도가 고루 보입니다.
     * 곳마다 횟수를 정하고, 각 곳의 방문을 1년에 고르게 나눠 날짜와 순간을 돌려 씁니다.
     */
    fun sampleTrips(today: LocalDate): List<Trip> {
        val plan = listOf("32030" to 22, "32540" to 9, "32060" to 6, "32510" to 3, "32610" to 2, "32040" to 1, "32550" to 1, "S-DGR-PASS" to 3, "S-GN-COAST" to 1)
        val visits = plan.flatMap { (code, count) -> (0 until count).map { code to it.toDouble() / count } }.sortedBy { it.second }
        return visits.mapIndexed { index, (code, share) ->
            val end = today.minusDays(SAMPLE_DAYS - (share * SAMPLE_DAYS).toLong() + 1).atTime(18, 0)
            val moment = Moment.entries[index % Moment.entries.size]
            val meters = SAMPLE_KM * 1000
            val isSpot = spot(code) != null
            Trip(
                "sample-$index", end.minusHours(1), end, meters,
                if (isSpot) emptyMap() else mapOf(code to mapOf(moment to meters)),
                if (moment == Moment.RAIN) meters else 0.0,
                if (isSpot) mapOf(code to moment) else emptyMap(),
            )
        }.sortedBy { it.endedAt }
    }

    private const val SAMPLE_DAYS = 360L
    private const val SAMPLE_KM = 24.0
    private const val EARTH_M = 6_371_000.0
}
