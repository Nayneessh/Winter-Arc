package com.winterarc.app.ui.dash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.kit.ArcCard
import com.winterarc.app.ui.kit.AreaChart
import com.winterarc.app.ui.kit.BarChart
import com.winterarc.app.ui.kit.ChartAxis
import com.winterarc.app.ui.kit.ChartPalette
import com.winterarc.app.ui.kit.DonutChart
import com.winterarc.app.ui.kit.EmptyState
import com.winterarc.app.ui.kit.GoalRing
import com.winterarc.app.ui.kit.Label
import com.winterarc.app.ui.kit.LegendRow
import com.winterarc.app.ui.kit.Metric
import com.winterarc.app.ui.kit.SectionHeader
import com.winterarc.app.ui.kit.SegmentedControl
import com.winterarc.app.ui.kit.Sparkline
import com.winterarc.app.ui.theme.W
import com.winterarc.core.Analytics
import com.winterarc.core.AppData
import com.winterarc.core.Fmt
import com.winterarc.core.GoalProgress
import com.winterarc.core.PrType
import com.winterarc.core.RangeFilter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val axisDate = DateTimeFormatter.ofPattern("d MMM")

/**
 * Everything, in one place.
 *
 * Ordered by the question being asked, not by what is easy to compute: am I hitting my targets,
 * am I getting stronger, am I doing the work, where is the work going, and what is my body
 * doing about it.
 */
@Composable
fun DashboardScreen(
    data: AppData,
    onOpenExercise: (String) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var rangeIndex by remember { mutableStateOf(1) }
    val range = RangeFilter.entries[rangeIndex]
    val dash = remember(data, range) { Analytics.dashboard(data, range, today) }
    val unit = data.prefs.unit

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, bottom = 28.dp, top = 0.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Spacer(Modifier.height(14.dp))
                Text("Progress", style = MaterialTheme.typography.headlineLarge, color = W.Ink)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${dash.sessionCount} sessions in the last ${range.label.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = W.Faint,
                )
                Spacer(Modifier.height(16.dp))
                SegmentedControl(
                    options = RangeFilter.entries.map { it.label },
                    selectedIndex = rangeIndex,
                    onSelect = { rangeIndex = it },
                )
                Spacer(Modifier.height(22.dp))
            }
        }

        // -- the arc: distance travelled toward each target --------------------------------
        if (dash.goalProgress.isNotEmpty()) {
            item {
                SectionHeader("The arc")
                ArcCard(
                    brush = Brush.verticalGradient(listOf(W.NightTop, W.NightHi, W.Night)),
                    padding = PaddingValues(vertical = 20.dp, horizontal = 14.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        dash.goalProgress.take(3).forEachIndexed { index, goal ->
                            GoalRing(
                                label = goal.label,
                                value = goalValue(goal, data),
                                unit = goal.unit,
                                progress = goal.fraction.toFloat(),
                                color = ChartPalette[index % ChartPalette.size],
                                milestone = goal.milestoneFraction?.toFloat(),
                                footnote = "→ ${Fmt.trim(goal.target)} ${goal.unit}",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Each ring measures how far you have come against how far there is to go. " +
                            "The notch is the milestone on the way.",
                        style = MaterialTheme.typography.labelSmall,
                        color = W.Ghost,
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        // -- strength ------------------------------------------------------------------------
        item {
            SectionHeader("Strength")
            ArcCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Label("Strength index")
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                dash.strengthIndexSeries.lastOrNull()?.value?.roundToInt()?.toString() ?: "—",
                                style = MaterialTheme.typography.displaySmall,
                                color = W.GoldBright,
                            )
                            dash.strengthIndexSeries.lastOrNull()?.let {
                                Spacer(Modifier.width(8.dp))
                                DeltaTag(it.value - 100.0, suffix = " vs start", decimals = 0)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                AreaChart(
                    values = dash.strengthIndexSeries.map { it.value },
                    color = W.Gold,
                    height = 140.dp,
                )
                ChartAxis(dash.strengthIndexSeries.map { it.date.format(axisDate) })
                Spacer(Modifier.height(10.dp))
                Text(
                    "The summed best estimated 1RM across everything trained each week, indexed " +
                        "to 100 at your first week. A trend, never a load — it moves when your " +
                        "exercise selection does.",
                    style = MaterialTheme.typography.labelSmall,
                    color = W.Ghost,
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        // -- the work done --------------------------------------------------------------------
        item {
            SectionHeader("The work · ${range.label}")
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                    Metric(
                        label = "Volume",
                        value = Fmt.volume(dash.volumeKg, unit),
                        unit = unit.suffix,
                        valueColor = W.GoldBright,
                    )
                    dash.volumeTrend.previous?.let {
                        Spacer(Modifier.height(6.dp))
                        DeltaTag(dash.volumeTrend.pctChange ?: 0.0, suffix = "%", decimals = 0)
                    }
                }
                ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                    Metric("Sets", dash.totalSets.toString(), caption = "working sets")
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                    Metric("Reps", dash.totalReps.toString(), caption = "total repetitions")
                }
                ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                    Metric(
                        "Heaviest set",
                        Fmt.weight(dash.heaviestSetKg, unit),
                        unit = unit.suffix,
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        // -- weekly volume ---------------------------------------------------------------------
        item {
            SectionHeader("Volume by week")
            ArcCard {
                BarChart(
                    values = dash.weekBuckets.map { it.volumeKg },
                    color = W.Gold,
                    height = 130.dp,
                )
                ChartAxis(dash.weekBuckets.map { it.weekStart.format(axisDate) })
                Spacer(Modifier.height(10.dp))
                Text(
                    "Weight × reps over working sets. Warm-ups contribute nothing.",
                    style = MaterialTheme.typography.labelSmall,
                    color = W.Ghost,
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        // -- muscle split ------------------------------------------------------------------------
        if (dash.muscleVolume.isNotEmpty()) {
            item {
                SectionHeader("Where the volume goes")
                ArcCard {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 46.dp)
                            .aspectRatio(1f),
                    ) {
                        DonutChart(
                            slices = dash.muscleVolume.take(8).mapIndexed { index, mv ->
                                ChartPalette[index % ChartPalette.size] to mv.volumeKg
                            },
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            strokeWidth = 26.dp,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    Fmt.volume(dash.volumeKg, unit),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = W.Ink,
                                )
                                Text(
                                    unit.suffix + " total",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = W.Faint,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    dash.muscleVolume.take(8).forEachIndexed { index, mv ->
                        LegendRow(
                            color = ChartPalette[index % ChartPalette.size],
                            name = mv.muscle.display,
                            value = "${Fmt.volume(mv.volumeKg, unit)} ${unit.suffix}",
                            share = Fmt.percent(mv.share * 100),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Every slice is kilograms, and they sum to the total — which is the only " +
                            "thing that makes a ring honest.",
                        style = MaterialTheme.typography.labelSmall,
                        color = W.Ghost,
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        // -- lift by lift ---------------------------------------------------------------------
        if (dash.topLifts.isNotEmpty()) {
            item {
                SectionHeader("Lift by lift")
            }
            items(dash.topLifts.size) { index ->
                val lift = dash.topLifts[index]
                ArcCard(
                    Modifier.padding(bottom = 9.dp),
                    onClick = { onOpenExercise(lift.exerciseId) },
                    padding = PaddingValues(15.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                lift.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = W.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${lift.timesPerformed} sessions · best " +
                                    Fmt.weightWithUnit(lift.heaviestKg, unit),
                                style = MaterialTheme.typography.bodySmall,
                                color = W.Faint,
                            )
                        }
                        Sparkline(
                            values = lift.topWeightSeries.map { it.value },
                            color = if (lift.weightGainKg >= 0) W.Good else W.Warn,
                            modifier = Modifier.width(62.dp).height(28.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                Fmt.weight(lift.latestTopWeightKg, unit),
                                style = MaterialTheme.typography.titleLarge,
                                color = W.Ink,
                            )
                            DeltaTag(
                                Fmt.toDisplayWeight(lift.weightGainKg, unit),
                                suffix = " ${unit.suffix}",
                                decimals = 1,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(13.dp)) }
        }

        // -- records ----------------------------------------------------------------------------
        if (dash.recentPrs.isNotEmpty()) {
            item {
                SectionHeader("Recent records")
                ArcCard {
                    dash.recentPrs.take(6).forEachIndexed { index, pr ->
                        if (index > 0) Spacer(Modifier.height(11.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(W.GoldFilm),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.EmojiEvents, null,
                                    tint = W.Gold, modifier = Modifier.size(15.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    pr.exerciseName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = W.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${pr.type.display} · ${pr.date.format(axisDate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = W.Faint,
                                )
                            }
                            Text(
                                when (pr.type) {
                                    PrType.REPS -> "${pr.value.toInt()} reps"
                                    PrType.VOLUME -> "${Fmt.volume(pr.value, unit)} ${unit.suffix}"
                                    else -> Fmt.weightWithUnit(pr.value, unit)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = W.GoldBright,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        // -- body -------------------------------------------------------------------------------
        item {
            SectionHeader("Body")
            ArcCard {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f)) {
                        Metric(
                            label = "Weight",
                            value = dash.body.latestWeightKg?.let { Fmt.weight(it, unit) } ?: "—",
                            unit = unit.suffix,
                        )
                        dash.body.weightTrend?.delta?.let {
                            Spacer(Modifier.height(5.dp))
                            DeltaTag(Fmt.toDisplayWeight(it, unit), " ${unit.suffix}", 1, invert = true)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Metric(
                            label = "Body fat",
                            value = dash.body.latestBodyFatPct?.let { Fmt.trim(it) } ?: "—",
                            unit = "%",
                        )
                        dash.body.bodyFatTrend?.delta?.let {
                            Spacer(Modifier.height(5.dp))
                            DeltaTag(it, "%", 1, invert = true)
                        }
                    }
                }

                if (dash.body.weightSeries.size >= 2) {
                    Spacer(Modifier.height(16.dp))
                    AreaChart(
                        values = dash.body.weightSeries.map { it.value },
                        color = W.Cyan,
                        height = 120.dp,
                    )
                    ChartAxis(dash.body.weightSeries.map { it.date.format(axisDate) })
                }

                if (dash.body.fatMassChangeKg != null && dash.body.leanMassChangeKg != null) {
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        CompositionTile(
                            "Fat mass",
                            dash.body.fatMassKg,
                            dash.body.fatMassChangeKg,
                            unit.suffix,
                            W.Warn,
                            Modifier.weight(1f),
                        )
                        CompositionTile(
                            "Lean mass",
                            dash.body.leanMassKg,
                            dash.body.leanMassChangeKg,
                            unit.suffix,
                            W.Good,
                            Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Estimated from the weight and body-fat readings you entered, and only " +
                            "from check-ins that carried both. Never inferred.",
                        style = MaterialTheme.typography.labelSmall,
                        color = W.Ghost,
                    )
                }

                if (dash.body.measurementDeltas.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Label("Measurements")
                    Spacer(Modifier.height(8.dp))
                    dash.body.measurementDeltas.forEach { delta ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                delta.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = W.Ink,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${Fmt.trim(delta.latestCm)} cm",
                                style = MaterialTheme.typography.bodyMedium,
                                color = W.Muted,
                            )
                            Spacer(Modifier.width(11.dp))
                            DeltaTag(delta.deltaCm, " cm", 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        // -- consistency -------------------------------------------------------------------------
        item {
            SectionHeader("Consistency")
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                    Metric(
                        "Week streak",
                        dash.consistency.weekStreak.toString(),
                        caption = "best ${dash.consistency.longestWeekStreak}",
                        valueColor = W.GoldBright,
                    )
                }
                ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                    Metric(
                        "Per week",
                        Fmt.trim((dash.consistency.avgSessionsPerWeek * 10).roundToInt() / 10.0),
                        caption = "sessions, average",
                    )
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                    Metric("All sessions", dash.consistency.totalSessions.toString())
                }
                ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                    Metric(
                        "Time trained",
                        Fmt.duration(dash.consistency.totalMinutes),
                        caption = "logged, all time",
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Streaks count weeks, not days. Sunday's rest is prescribed — a day-based streak " +
                    "would reset every week for following the plan correctly.",
                style = MaterialTheme.typography.labelSmall,
                color = W.Ghost,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (dash.consistency.totalSessions == 0) {
            item {
                Spacer(Modifier.height(16.dp))
                EmptyState(
                    title = "Nothing logged yet",
                    message = "Finish one session and every figure on this screen fills in from " +
                        "what you actually did — nothing here is estimated from the plan.",
                )
            }
        }
    }
}

private fun goalValue(goal: GoalProgress, data: AppData): String =
    if (goal.unit == "%") Fmt.trim(goal.current) else Fmt.weight(goal.current, data.prefs.unit)

/**
 * A signed change.
 *
 * [invert] is for figures where down is the win -- bodyweight on a cut, body fat -- so the colour
 * tracks whether the number is going the right way rather than merely whether it rose.
 */
@Composable
private fun DeltaTag(
    value: Double,
    suffix: String = "",
    decimals: Int = 1,
    invert: Boolean = false,
) {
    if (abs(value) < 0.05) {
        Text("no change", style = MaterialTheme.typography.labelSmall, color = W.Ghost)
        return
    }
    val good = if (invert) value < 0 else value > 0
    val color = if (good) W.Good else W.Warn
    Row(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (value > 0) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            null,
            tint = color,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            Fmt.signed(value, decimals).removePrefix("+") + suffix,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun CompositionTile(
    label: String,
    value: Double?,
    change: Double?,
    unitSuffix: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.09f))
            .padding(13.dp),
    ) {
        Label(label, color = color.copy(alpha = 0.85f))
        Spacer(Modifier.height(5.dp))
        Text(
            value?.let { Fmt.trim(it) } ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            color = W.Ink,
        )
        Text(unitSuffix, style = MaterialTheme.typography.labelSmall, color = W.Faint)
        if (change != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${Fmt.signed(change, 1)} $unitSuffix since first",
                style = MaterialTheme.typography.labelSmall,
                color = W.Faint,
            )
        }
    }
}
