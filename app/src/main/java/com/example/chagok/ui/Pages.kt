package com.example.chagok.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chagok.Badge
import com.example.chagok.Drive
import com.example.chagok.R
import com.example.chagok.Regions
import com.example.chagok.Rules
import com.example.chagok.Level
import com.example.chagok.Moment
import com.example.chagok.Season
import com.example.chagok.Stamp
import com.example.chagok.Store
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.cos
import kotlin.math.roundToInt

@Composable
internal fun MapPage() {
    val palette = LocalPalette.current
    val regions = Store.regions
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    if (regions == null) {
        Text(stringResource(R.string.map_loading), Type.body, palette.textSub)
        return
    }
    val visits = Rules.visits(Store.trips)
    val places = Store.stamps.groupBy { it.code }
    TwoColumns(
        left = {
            Text(stringResource(R.string.map_title), Type.body, palette.textSub)
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(regions.all.count { it.code in places }, Type.hero.copy(brush = palette.heroBrush), Color.Unspecified, fromZero = true)
                Spacer(Modifier.width(Space.s))
                Text(stringResource(R.string.map_total, regions.all.size), Type.title, palette.text, Modifier.padding(bottom = 18.dp))
            }
            Spacer(Modifier.height(Space.m))
            StampMap(regions, visits)
        },
        right = {
            val code = open
            if (code == null) {
                Nearby()
                PlaceList(places, visits) { open = it }
            }
            else StampBook(code, places[code].orEmpty(), visits[code] ?: 0) { open = null }
        },
    )
}

/** 도장을 받은 곳 목록. 줄마다 순간 5칸이 붙고, 누르면 도장첩이 열립니다. */
@Composable
private fun PlaceList(places: Map<String, List<Stamp>>, visits: Map<String, Int>, onOpen: (String) -> Unit) {
    val palette = LocalPalette.current
    SectionHeader(stringResource(R.string.map_recent), null)
    val recent = places.entries.sortedByDescending { entry -> entry.value.maxOf { it.at } }
    if (recent.isEmpty()) Text(stringResource(R.string.map_empty), Type.body, palette.textSub, Modifier.padding(vertical = Space.m))
    else Panel(recent) { (code, stamps) ->
        val count = visits[code] ?: 0
        Pressable({ onOpen(code) }, Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stampName(code), Type.body, palette.text)
                        Level.of(count)?.let { Spacer(Modifier.width(Space.xs)); LevelTag(it) }
                    }
                    // 명소는 종류를, 시군구는 지난 횟수를 적습니다
                    Text(Rules.spot(code)?.let { stringResource(it.kind.label) } ?: stringResource(R.string.visits, count), Type.label, palette.textSub)
                }
                val got = stamps.map { it.moment }.toSet()
                val level = Level.of(count)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Moment.entries.forEach { MomentDot(it, level.takeIf { _ -> it in got }, Modifier.size(48.dp)) }
                }
            }
        }
    }
}

/** 한 곳의 도장첩. 순간 5가지와 계절 4가지를 곱한 20칸이고, 찍힌 칸은 그곳 등급 색입니다. */
@Composable
private fun StampBook(code: String, stamps: List<Stamp>, visits: Int, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val level = Level.of(visits)
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("‹", stringResource(R.string.book_back), true, onBack)
        Spacer(Modifier.width(Space.m))
        Text(stampName(code), Type.title, palette.text)
        level?.let { Spacer(Modifier.width(Space.s)); LevelTag(it) }
    }
    Spacer(Modifier.height(Space.m))
    val got = stamps.map { it.moment to it.season }.toSet()
    Column(Modifier.fillMaxWidth().glass(palette).padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(SEASON_COLUMN))
            Moment.entries.forEach { Text(stringResource(it.label), Type.label, palette.textSub, Modifier.weight(1f), align = TextAlign.Center) }
        }
        Season.entries.forEach { season ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(season.label), Type.body, palette.text, Modifier.width(SEASON_COLUMN))
                Moment.entries.forEach { moment ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { MomentDot(moment, level.takeIf { (moment to season) in got }, Modifier.size(56.dp)) }
                }
            }
        }
    }
}

/** 정차 중 가장 가까운 안 찍은 명소. 누르면 내비가 안내합니다. */
@Composable
private fun Nearby() {
    val palette = LocalPalette.current
    val (spot, meters) = Drive.here?.let { Rules.nearest(it, Store.stamps, LocalDate.now()) } ?: return
    Row(Modifier.fillMaxWidth().glass(palette).padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.nearby_title), Type.label, palette.textSub)
            Text(stringResource(R.string.nearby_detail, stringResource(spot.name), (meters / 1000).roundToInt()), Type.bodyStrong, palette.text)
        }
        ActionButton(stringResource(R.string.nearby_guide), { Drive.guide(spot) })
    }
    Spacer(Modifier.height(Space.m))
}

@Composable
private fun StampMap(regions: Regions, visits: Map<String, Int>) {
    val palette = LocalPalette.current
    val shapes = remember(regions) {
        regions.all.map { region ->
            region.code to Path().apply {
                region.rings.forEach { ring ->
                    moveTo(x(ring[0]), -ring[1].toFloat())
                    for (i in 2 until ring.size step 2) lineTo(x(ring[i]), -ring[i + 1].toFloat())
                    close()
                }
            }
        }
    }
    val bounds = remember(shapes) { shapes.map { it.second.getBounds() }.reduce { a, b -> a.copy(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) } }
    Canvas(Modifier.fillMaxWidth().aspectRatio(bounds.width / bounds.height)) {
        val scale = size.width / bounds.width
        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
            translate(-bounds.left, -bounds.top)
        }) {
            shapes.forEach { (code, path) ->
                // 많이 지난 곳일수록 진하게 칠합니다. 금 등급 횟수에서 가장 진합니다.
                val count = visits[code] ?: 0
                drawPath(path, if (count == 0) palette.line else palette.accent.copy(alpha = MAP_ALPHA_MIN + (1 - MAP_ALPHA_MIN) * count.coerceAtMost(Level.GOLD.visits) / Level.GOLD.visits))
                drawPath(path, palette.background, style = Stroke(width = 1.dp.toPx() / scale))
            }
        }
    }
}

private fun x(lon: Double) = (lon * MAP_LON_SCALE).toFloat()

@Composable
internal fun BadgesPage() {
    val palette = LocalPalette.current
    val earned = Store.earned.associateBy { it.badge }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionHeader(stringResource(R.string.badges_title), stringResource(R.string.badges_count, earned.size, Badge.entries.size))
        Spacer(Modifier.height(Space.s))
        Badge.entries.chunked(BADGE_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                row.forEach { badge ->
                    val got = earned[badge]
                    val progress = Rules.progress(badge, Store.trips, Store.stamps)
                    Column(Modifier.weight(1f).glass(palette).padding(Space.m), horizontalAlignment = Alignment.CenterHorizontally) {
                        Medal(badge, got != null)
                        Spacer(Modifier.height(Space.s))
                        Text(stringResource(badge.title), Type.bodyStrong, if (got != null) palette.text else palette.textSub, align = TextAlign.Center)
                        Text(stringResource(badge.rule), Type.label, palette.textSub, align = TextAlign.Center)
                        val status = if (got != null) shortDate(got.at.toLocalDate()) else stringResource(progress.format, progress.done.coerceAtMost(progress.goal), progress.goal)
                        Text(status, Type.labelStrong, if (got != null) palette.accent else palette.text, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** 배지 메달. 받으면 파란 빛 위에 흰 그림, 받기 전이면 유리 위에 흐린 그림입니다. */
@Composable
private fun Medal(badge: Badge, earned: Boolean) {
    val palette = LocalPalette.current
    Box(Modifier.size(MEDAL).then(if (earned) Modifier.lit(palette, CircleShape) else Modifier.glass(palette, CircleShape)), contentAlignment = Alignment.Center) {
        Image(painterResource(badge.icon), null, Modifier.size(MEDAL / 2), colorFilter = ColorFilter.tint(if (earned) palette.onAccent else palette.textSub))
    }
}

@Composable
internal fun RecapPage() {
    val palette = LocalPalette.current
    var monthsBack by rememberSaveable { mutableIntStateOf(0) }
    val month = YearMonth.now().minusMonths(monthsBack.toLong())
    val recap = Rules.recap(month, Store.trips, Store.stamps, Store.earned)
    val dash = stringResource(R.string.dash)
    TwoColumns(
        left = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("‹", stringResource(R.string.recap_previous), true) { monthsBack++ }
                Text(stringResource(R.string.recap_title, month.year, month.monthValue), Type.title, palette.text, Modifier.padding(horizontal = Space.m))
                StepButton("›", stringResource(R.string.recap_next), monthsBack > 0) { monthsBack-- }
            }
            Spacer(Modifier.height(Space.l))
            Text(stringResource(R.string.recap_distance), Type.body, palette.textSub)
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber((recap.distanceM / 1000).toInt(), Type.hero.copy(brush = palette.heroBrush), Color.Unspecified, fromZero = true)
                Spacer(Modifier.width(Space.s))
                Text(stringResource(R.string.unit_km), Type.title, palette.text, Modifier.padding(bottom = 18.dp))
            }
        },
        right = {
            val rows = listOf(
                R.string.recap_stamps to stringResource(R.string.unit_stamps, recap.newStamps),
                R.string.recap_top_region to (recap.topRegion?.let { stampName(it) } ?: dash),
                R.string.recap_rainy_days to stringResource(R.string.unit_days, recap.rainyDays),
                R.string.recap_night to stringResource(R.string.unit_distance, (recap.nightM / 1000).toInt()),
                R.string.recap_badges to if (recap.badges.isEmpty()) dash else recap.badges.map { stringResource(it.badge.title) }.joinToString(),
            )
            Panel(rows) { (label, value) ->
                Row(Modifier.fillMaxWidth().heightIn(min = Space.touch), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(label), Type.body, palette.textSub, Modifier.weight(1f))
                    Text(value, Type.bodyStrong, palette.text, align = TextAlign.End)
                }
            }
        },
    )
}

@Composable
internal fun DemoPage() {
    val palette = LocalPalette.current
    var clearStep by rememberSaveable { mutableIntStateOf(0) }
    var seeded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.widthIn(max = 1200.dp).verticalScroll(rememberScrollState())) {
        Panel(Rules.routes) { route ->
            val playing = Drive.replaying == route
            Row(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = Space.m)) {
                    Text(stringResource(route.name), Type.bodyStrong, palette.text)
                    Text(stringResource(route.detail), Type.label, palette.textSub)
                }
                ActionButton(stringResource(if (playing) R.string.demo_stop else R.string.demo_start), { if (playing) Drive.stopReplay() else Drive.replay(route) }, primary = playing)
            }
        }
        Spacer(Modifier.height(Space.l))
        val label = when (clearStep) {
            0 -> R.string.demo_clear
            1 -> R.string.demo_clear_confirm
            else -> R.string.demo_cleared
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            ActionButton(stringResource(if (seeded) R.string.demo_samples_done else R.string.demo_samples), {
                if (!seeded) Store.addSamples(Rules.sampleTrips(LocalDate.now()))
                seeded = true
            })
            ActionButton(stringResource(label), {
                if (clearStep == 1) Store.clear()
                clearStep = (clearStep + 1).coerceAtMost(2)
            })
        }
    }
}

private const val BADGE_COLUMNS = 4
private val SEASON_COLUMN = 96.dp
private const val MAP_ALPHA_MIN = 0.2f
private val MEDAL = 96.dp
private val MAP_LON_SCALE = cos(Math.toRadians(36.0))
