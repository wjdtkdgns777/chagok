package com.example.chagok.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chagok.Badge
import com.example.chagok.Drive
import com.example.chagok.R
import com.example.chagok.Regions
import com.example.chagok.Rules
import com.example.chagok.Store
import java.time.YearMonth
import kotlin.math.cos

@Composable
internal fun MapPage() {
    val palette = LocalPalette.current
    val regions = Store.regions
    val stamped = Store.stamps.map { it.code }.toSet()
    if (regions == null) {
        Text(stringResource(R.string.map_loading), Type.body, palette.textSub)
        return
    }
    TwoColumns(
        left = {
            val count = regions.all.count { it.code in stamped }
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(count, Type.hero.copy(brush = palette.heroBrush), Color.Unspecified, fromZero = true)
                Spacer(Modifier.width(Space.s))
                Text(stringResource(R.string.map_total, regions.all.size), Type.title, palette.text, Modifier.padding(bottom = 18.dp))
            }
            Spacer(Modifier.height(Space.m))
            StampMap(regions, stamped)
        },
        right = {
            SectionHeader(stringResource(R.string.map_provinces), null)
            Panel(regions.all.groupBy { it.province }.entries.sortedByDescending { (_, list) -> list.count { it.code in stamped } }) { (province, list) ->
                val done = list.count { it.code in stamped }
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(province, Type.label, if (done > 0) palette.text else palette.textSub, Modifier.width(240.dp))
                    Box(Modifier.weight(1f).height(6.dp).background(palette.line, CircleShape)) {
                        if (done > 0) Box(Modifier.fillMaxWidth(done / list.size.toFloat()).fillMaxHeight().background(palette.accentBrush, CircleShape))
                    }
                    Text("$done/${list.size}", Type.label, if (done > 0) palette.text else palette.textSub, Modifier.width(96.dp), TextAlign.End)
                }
            }
            Spacer(Modifier.height(Space.xl))
            SectionHeader(stringResource(R.string.map_spots), stringResource(R.string.map_spot_count, Rules.spots.count { it.code in stamped }, Rules.spots.size))
            Spacer(Modifier.height(Space.xl))
            SectionHeader(stringResource(R.string.map_recent), null)
            val recent = Store.stamps.sortedByDescending { it.at }.take(RECENT_STAMPS)
            if (recent.isEmpty()) Text(stringResource(R.string.map_empty), Type.body, palette.textSub, Modifier.padding(vertical = Space.m))
            else Panel(recent) { stamp ->
                Row(Modifier.fillMaxWidth().heightIn(min = Space.touch), verticalAlignment = Alignment.CenterVertically) {
                    StampMark(true, Modifier.size(32.dp))
                    Spacer(Modifier.width(Space.m))
                    Text(stampName(stamp.code), Type.body, palette.text, Modifier.weight(1f))
                    Text(shortDate(stamp.at.toLocalDate()), Type.body, palette.textSub)
                }
            }
        },
    )
}

@Composable
private fun StampMap(regions: Regions, stamped: Set<String>) {
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
    val lit = remember(bounds, palette) { Brush.linearGradient(listOf(palette.accent, palette.accentEnd), bounds.topLeft, bounds.bottomRight) }
    Canvas(Modifier.fillMaxWidth().aspectRatio(bounds.width / bounds.height)) {
        val scale = size.width / bounds.width
        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
            translate(-bounds.left, -bounds.top)
        }) {
            shapes.forEach { (code, path) ->
                if (code in stamped) drawPath(path, lit) else drawPath(path, palette.line)
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
    val wiperKm = (Store.trips.sumOf { it.wiperM } / 1000).toInt()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionHeader(stringResource(R.string.badges_title), stringResource(R.string.badges_count, earned.size, Badge.entries.size))
        Panel(Badge.entries) { badge ->
            val got = earned[badge]
            Row(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                StampMark(got != null, Modifier.size(40.dp))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(badge.title), Type.bodyStrong, if (got != null) palette.text else palette.textSub)
                    Text(stringResource(badge.rule), Type.label, palette.textSub)
                }
                val status = when {
                    got != null -> recordDate(got.at.toLocalDate())
                    badge == Badge.RAIN_DRIVER -> stringResource(R.string.badge_progress_km, wiperKm, (Rules.RAIN_DRIVER_M / 1000).toInt())
                    else -> stringResource(R.string.badge_locked)
                }
                Text(status, Type.label, if (got != null) palette.text else palette.textSub)
            }
        }
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
                R.string.recap_new_regions to stringResource(R.string.unit_count, recap.newRegions),
                R.string.recap_top_region to (recap.topRegion?.let { stampName(it) } ?: dash),
                R.string.recap_rainy_days to stringResource(R.string.unit_days, recap.rainyDays),
                R.string.recap_night to stringResource(R.string.unit_percent, recap.nightPercent),
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
internal fun SettingsPage() {
    val palette = LocalPalette.current
    val settings = Store.settings
    var clearStep by rememberSaveable { mutableStateOf(0) }
    TwoColumns(
        left = {
            SectionHeader(stringResource(R.string.settings_title), null)
            Box(Modifier.glass(palette).padding(horizontal = Space.l)) {
                SwitchRow(stringResource(R.string.settings_voice), stringResource(R.string.settings_voice_detail), settings.voice) {
                    Store.update(settings.copy(voice = it))
                }
            }
            Spacer(Modifier.height(Space.l))
            Text(stringResource(R.string.settings_privacy), Type.body, palette.textSub)
            Spacer(Modifier.height(Space.m))
            val label = when (clearStep) {
                0 -> R.string.settings_clear
                1 -> R.string.settings_clear_confirm
                else -> R.string.settings_cleared
            }
            ActionButton(stringResource(label), {
                if (clearStep == 1) Store.clear()
                clearStep = (clearStep + 1).coerceAtMost(2)
            })
            Spacer(Modifier.height(Space.xl))
            SectionHeader(stringResource(R.string.info_title), null)
            Text(stringResource(R.string.info_boundary), Type.label, palette.textSub, Modifier.padding(vertical = Space.m))
        },
        right = {
            SectionHeader(stringResource(R.string.demo_title), null)
            Text(stringResource(R.string.demo_detail), Type.label, palette.textSub, Modifier.padding(vertical = Space.s))
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
        },
    )
}

private const val RECENT_STAMPS = 6
private val MAP_LON_SCALE = cos(Math.toRadians(36.0))
