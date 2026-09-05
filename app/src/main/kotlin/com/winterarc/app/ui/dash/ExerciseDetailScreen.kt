package com.winterarc.app.ui.dash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.kit.ArcCard
import com.winterarc.app.ui.kit.AreaChart
import com.winterarc.app.ui.kit.BarChart
import com.winterarc.app.ui.kit.ChartAxis
import com.winterarc.app.ui.kit.EmptyState
import com.winterarc.app.ui.kit.Metric
import com.winterarc.app.ui.kit.MetricRow
import com.winterarc.app.ui.kit.OverlayScreen
import com.winterarc.app.ui.kit.Pill
import com.winterarc.app.ui.kit.SectionHeader
import com.winterarc.app.ui.theme.W
import com.winterarc.core.Analytics
import com.winterarc.core.AppData
import com.winterarc.core.Fmt
import java.time.format.DateTimeFormatter

private val dateShort = DateTimeFormatter.ofPattern("d MMM")
private val dateFull = DateTimeFormatter.ofPattern("d MMM yyyy")

/** One movement, from the first time it was performed to the last. */
@Composable
fun ExerciseDetailScreen(
    data: AppData,
    exerciseId: String,
    onBack: () -> Unit,
) {
    val exercise = data.exercise(exerciseId)
    val stats = remember(data.sessions, exerciseId) {
        Analytics.exerciseStats(exerciseId, data.sessions.filter { it.finished }, data.exerciseById)
    }
    val unit = data.prefs.unit

    OverlayScreen(
        title = exercise?.name ?: "Movement",
        subtitle = listOfNotNull(
            exercise?.muscle?.display,
            exercise?.detail?.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        onBack = onBack,
    ) {
        if (stats == null) {
            EmptyState(
                title = "Never performed",
                message = "Once you log a set of this movement, its whole history and every " +
                    "record appear here.",
            )
            return@OverlayScreen
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 30.dp),
        ) {
            MetricRow {
                ArcCard(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(15.dp)) {
                    Metric(
                        "Heaviest",
                        Fmt.weight(stats.heaviestKg, unit),
                        unit = unit.suffix,
                        valueColor = W.GoldBright,
                    )
                }
                ArcCard(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(15.dp)) {
                    Metric(
                        "Best est. 1RM",
                        Fmt.weight(stats.bestE1rmKg, unit),
                        unit = unit.suffix,
                    )
                }
            }
            Spacer(Modifier.height(11.dp))
            MetricRow {
                ArcCard(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(15.dp)) {
                    Metric("Most reps", stats.bestReps.toString(), caption = "in one set")
                }
                ArcCard(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(15.dp)) {
                    Metric("Sessions", stats.timesPerformed.toString(), caption = "times performed")
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Load over time")
            ArcCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Metric(
                        "Top set now",
                        Fmt.weight(stats.latestTopWeightKg, unit),
                        unit = unit.suffix,
                        caption = "started at ${Fmt.weightWithUnit(stats.firstTopWeightKg, unit)}",
                    )
                    Spacer(Modifier.weight(1f))
                    Pill(
                        "${Fmt.signed(Fmt.toDisplayWeight(stats.weightGainKg, unit), 1)} ${unit.suffix}",
                        color = if (stats.weightGainKg >= 0) W.Good else W.Warn,
                    )
                }
                Spacer(Modifier.height(14.dp))
                AreaChart(
                    values = stats.topWeightSeries.map { it.value },
                    color = W.Gold,
                    height = 140.dp,
                )
                ChartAxis(stats.topWeightSeries.map { it.date.format(dateShort) })
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Estimated 1RM")
            ArcCard {
                AreaChart(
                    values = stats.e1rmSeries.map { it.value },
                    color = W.Cyan,
                    height = 130.dp,
                )
                ChartAxis(stats.e1rmSeries.map { it.date.format(dateShort) })
                Spacer(Modifier.height(10.dp))
                Text(
                    "Epley: weight × (1 + reps ÷ 30). Always an estimate, never a tested max, " +
                        "and unreliable above about twelve reps.",
                    style = MaterialTheme.typography.labelSmall,
                    color = W.Ghost,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Volume per session")
            ArcCard {
                BarChart(
                    values = stats.volumeSeries.map { it.value },
                    color = W.Good,
                    height = 120.dp,
                )
                ChartAxis(stats.volumeSeries.map { it.date.format(dateShort) })
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Every performance")
            val performances = remember(data.sessions, exerciseId) {
                data.sessions
                    .filter { it.finished }
                    .sortedByDescending { it.date }
                    .mapNotNull { session ->
                        session.exercises
                            .firstOrNull { it.exerciseId == exerciseId && it.workingSets.isNotEmpty() }
                            ?.let { session to it }
                    }
            }
            performances.forEach { (session, entry) ->
                ArcCard(Modifier.padding(bottom = 8.dp), padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                session.date.format(dateFull),
                                style = MaterialTheme.typography.titleSmall,
                                color = W.Muted,
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                entry.workingSets.joinToString("   ") {
                                    "${Fmt.weight(it.weightKg, unit)}×${it.reps}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = W.Ink,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${Fmt.volume(entry.volumeKg, unit)} ${unit.suffix}",
                                style = MaterialTheme.typography.titleSmall,
                                color = W.Muted,
                            )
                            if (entry.extraSets > 0) {
                                Spacer(Modifier.height(4.dp))
                                Pill("+${entry.extraSets}", color = W.Good)
                            }
                        }
                    }
                }
            }
        }
    }
}
