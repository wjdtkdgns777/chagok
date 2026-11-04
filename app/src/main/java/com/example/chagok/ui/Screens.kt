package com.example.chagok.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chagok.Drive
import com.example.chagok.R
import com.example.chagok.Reveal
import com.example.chagok.Rules
import com.example.chagok.Store
import java.time.LocalDate

internal val wide = 1100.dp

@Composable
internal fun shortDate(date: LocalDate) = stringResource(R.string.stamp_date, date.monthValue, date.dayOfMonth)

@Composable
internal fun stampName(code: String): String {
    Rules.spot(code)?.let { return stringResource(it.name) }
    return Store.regions?.byCode(code)?.name ?: code
}

@Composable
fun ChagokApp(driving: Boolean, onAgree: () -> Unit, onLater: () -> Unit) {
    val palette = LocalPalette.current
    val reveal = Drive.reveal
    val page = when {
        !Store.settings.consented -> Page.CONSENT
        driving -> Page.DRIVING
        reveal != null -> Page.REVEAL
        else -> Page.PARKED
    }
    AnimatedContent(
        targetState = page,
        modifier = Modifier.fillMaxSize().background(palette.background),
        transitionSpec = {
            if (targetState == Page.DRIVING) EnterTransition.None togetherWith ExitTransition.None
            else fadeIn(tween(Motion.MEDIUM2, easing = Motion.easing)) togetherWith fadeOut(tween(Motion.SHORT1))
        },
        label = "page",
    ) {
        when (it) {
            Page.CONSENT -> ConsentScreen(onAgree, onLater)
            Page.DRIVING -> DrivingScreen()
            Page.REVEAL -> reveal?.let { shown -> RevealScreen(shown) { Drive.reveal = null } }
            Page.PARKED -> ParkedScreen()
        }
    }
}

private enum class Page { CONSENT, DRIVING, REVEAL, PARKED }

@Composable
private fun ConsentScreen(onAgree: () -> Unit, onLater: () -> Unit) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.xxl), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 1040.dp)) {
            Text(stringResource(R.string.consent_title), Type.headline, palette.text, Modifier.enter(0))
            Spacer(Modifier.height(Space.l))
            listOf(R.string.consent_line_1, R.string.consent_line_2, R.string.consent_line_3).forEachIndexed { index, line ->
                Row(Modifier.padding(vertical = Space.xs).enter(index + 1), verticalAlignment = Alignment.CenterVertically) {
                    StampMark(true, Modifier.size(40.dp))
                    Spacer(Modifier.width(Space.m))
                    Text(stringResource(line), Type.body, palette.text)
                }
            }
            Spacer(Modifier.height(Space.xl))
            SectionHeader(stringResource(R.string.consent_data_title), null)
            listOf(
                R.string.consent_location to R.string.consent_location_detail,
                R.string.consent_vehicle to R.string.consent_vehicle_detail,
                R.string.consent_quiet to R.string.consent_quiet_detail,
            ).forEachIndexed { index, (title, detail) ->
                Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).padding(vertical = Space.xs).enter(index + 4), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(title), Type.bodyStrong, palette.text, Modifier.width(200.dp))
                    Text(stringResource(detail), Type.body, palette.textSub, Modifier.weight(1f))
                }
                Hairline()
            }
            Spacer(Modifier.height(Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.enter(7)) {
                ActionButton(stringResource(R.string.consent_agree), onAgree, primary = true)
                ActionButton(stringResource(R.string.consent_later), onLater)
            }
        }
    }
}

@Composable
private fun DrivingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.driving_title), Type.headline, LocalPalette.current.text, align = TextAlign.Center)
    }
}

@Composable
private fun RevealScreen(reveal: Reveal, onClose: () -> Unit) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.xxl), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 1040.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.reveal_title), Type.body, palette.textSub, Modifier.enter(0))
            Text(stringResource(R.string.reveal_summary, reveal.stamps.size, reveal.badges.size), Type.headline, palette.text, Modifier.enter(1))
            Spacer(Modifier.height(Space.xl))
            val items = reveal.stamps.map { stringResource(R.string.reveal_stamp) to stampName(it.code) } +
                reveal.badges.map { stringResource(R.string.reveal_badge) to stringResource(it.badge.title) }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                items.forEachIndexed { index, (kind, name) ->
                    Column(
                        Modifier.width(260.dp).enter(index + 2).background(palette.surface, RoundedCornerShape(28.dp)).padding(Space.m),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StampMark(true, Modifier.size(96.dp).rotate(if (index % 2 == 0) -8f else 6f))
                        Spacer(Modifier.height(Space.s))
                        Text(kind, Type.label, palette.accent)
                        Text(name, Type.bodyStrong, palette.text, align = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(Space.xl))
            ActionButton(stringResource(R.string.reveal_close), onClose, Modifier.enter(items.size + 2), primary = true)
        }
    }
}

@Composable
private fun ParkedScreen() {
    val palette = LocalPalette.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.tab_map, R.string.tab_badges, R.string.tab_recap, R.string.tab_settings)
    Column(Modifier.fillMaxSize().padding(horizontal = Space.xxl, vertical = Space.xl)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), Type.title, palette.accent, Modifier.weight(1f))
            Segments(tabs.map { stringResource(it) }, tab, { tab = it }, itemWidth = 160.dp)
        }
        Spacer(Modifier.height(Space.xl))
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(Motion.MEDIUM1, Motion.SHORT1, Motion.easing)) + slideInVertically(tween(Motion.LONG1, easing = Motion.easing)) { it / 24 }) togetherWith
                    fadeOut(tween(Motion.SHORT1))
            },
            label = "tab",
        ) { page ->
            when (page) {
                0 -> MapPage()
                1 -> BadgesPage()
                2 -> RecapPage()
                else -> SettingsPage()
            }
        }
    }
}

@Composable
internal fun StampMark(on: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(modifier.background(if (on) palette.accent else palette.line, CircleShape).padding(6.dp)) {
        Box(Modifier.fillMaxSize().background(if (on) palette.onAccent.copy(alpha = 0.2f) else palette.surface, CircleShape).padding(4.dp)) {
            Box(Modifier.fillMaxSize().background(if (on) palette.accent else palette.line, CircleShape))
        }
    }
}

@Composable
internal fun TwoColumns(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    AdaptiveColumns(
        wideFrom = wide,
        wide = {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.55f).verticalScroll(rememberScrollState()), content = left)
                Spacer(Modifier.width(Space.xxl))
                Column(Modifier.weight(0.45f).verticalScroll(rememberScrollState()), content = right)
            }
        },
        narrow = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                left()
                Spacer(Modifier.height(Space.xl))
                right()
            }
        },
    )
}

@Composable
internal fun SectionHeader(title: String, trailing: String?) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(bottom = Space.xs), verticalAlignment = Alignment.Bottom) {
        Text(title, Type.titleSmall, palette.text, Modifier.weight(1f))
        if (trailing != null) Text(trailing, Type.body, palette.textSub)
    }
    Hairline()
}

@Composable
internal fun SwitchRow(index: Int, title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Pressable(
        onClick = { onChange(!checked) },
        modifier = Modifier.fillMaxWidth().enter(index).semantics { toggleableState = ToggleableState(checked) },
        shape = RoundedCornerShape(20.dp),
        role = Role.Switch,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(horizontal = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, Type.bodyStrong, palette.text)
                Text(detail, Type.label, palette.textSub)
            }
            Toggle(checked)
        }
    }
}
