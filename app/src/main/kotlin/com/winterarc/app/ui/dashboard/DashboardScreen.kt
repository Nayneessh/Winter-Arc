package com.winterarc.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.components.*
import com.winterarc.app.ui.theme.WinterArcColors
import com.winterarc.domain.analytics.PrType
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val shortDate = DateTimeFormatter.ofPattern("d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRangeChange: (DateRange) -> Unit,
    onTrackExercise: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = WinterArcColors.NightDeep,
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = WinterArcColors.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WinterArcColors.NightDeep,
                    titleContentColor = WinterArcColors.White,
                ),
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WinterArcColors.Gold)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.totalWorkouts == 0) {
                EmptyState(
                    title = "Nothing to show yet",
                    message = "Complete your first workout and every figure here fills in from " +
                        "what you actually did.",
                )
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateRange.entries.forEach { r ->
                    RangeChip(r.label, r == state.range) { onRangeChange(r) }
                }
            }

            SectionLabel("Workouts")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WinterCard(Modifier.weight(1f)) {
                    StatTile("Total", state.totalWorkouts.toString(), caption = "all time")
                }
                WinterCard(Modifier.weight(1f)) {
                    StatTile("This month", state.workoutsThisMonth.toString(), caption = "sessions")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WinterCard(Modifier.weight(1f)) {
                    StatTile(
                        "Streak",
                        state.weekStreak.toString(),
                        caption = "consecutive weeks (best ${state.longestStreak})",
                    )
                }
                WinterCard(Modifier.weight(1f)) {
                    StatTile(
                        "Frequency",
                        "${(state.avgPerWeek * 10).roundToInt() / 10.0}",
                        caption = "sessions/week, last 4",
                    )
                }
            }

            SectionLabel("Training — ${state.range.label}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WinterCard(Modifier.weight(1f)) {
                    StatTile("Sets", state.totalSets.toString())
                }
                WinterCard(Modifier.weight(1f)) {
                    StatTile("Reps", state.totalReps.toString())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WinterCard(Modifier.weight(1f)) {
                    StatTile(
                        "Volume",
                        "${state.totalVolumeKg.roundToInt()}",
                        caption = "kg (weight × reps)",
                    )
                }
                WinterCard(Modifier.weight(1f)) {
                    StatTile(
                        "Exercises",
                        state.exercisesPerformed.toString(),
                        caption = "${state.customExercisesAdded} added by you",
                    )
                }
            }

            SectionLabel("Weekly volume")
            WinterCard {
                BarChart(
                    points = state.volumeByWeek.map {
                        ChartPoint(it.first.format(shortDate), it.second)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Volume is weight × reps across working sets. Warm-ups are excluded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WinterArcColors.Faint,
                )
            }

            SectionLabel("Where the work went")
            WinterCard {
                DonutChart(
                    slices = state.muscleShares.mapIndexed { i, s ->
                        DonutSlice(s.muscle.label, s.volumeKg, chartPalette[i % chartPalette.size])
                    },
                    centerValue = "${state.totalVolumeKg.roundToInt()}",
                    centerLabel = "kg total",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Each slice is that muscle group's share of total training volume over the " +
                        "selected period. Slices are in the same unit and sum to the whole, which " +
                        "is what makes this breakdown meaningful as a proportion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WinterArcColors.Faint,
                )
            }

            if (state.goals.isNotEmpty()) {
                SectionLabel("Progress toward targets")
                WinterCard {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(state.goals) { g ->
                            ProgressRing(
                                label = g.label,
                                fraction = g.fraction.toFloat(),
                                caption = "${(g.currentValue * 10).roundToInt() / 10.0} → " +
                                    "${(g.targetValue * 10).roundToInt() / 10.0} ${g.unit}",
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Each ring is one target measured on its own: distance travelled from " +
                            "your starting figure divided by the distance required. They are " +
                            "shown separately rather than as slices of one chart, because " +
                            "unrelated goals do not add up to a single whole.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WinterArcColors.Faint,
                    )
                }
            }

            SectionLabel("Strength progression")
            WinterCard {
                if (state.exerciseOptions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.exerciseOptions) { (id, name) ->
                            RangeChip(name, id == state.trackedExerciseId) { onTrackExercise(id) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    state.trackedExerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = WinterArcColors.White,
                )
                Spacer(Modifier.height(8.dp))
                LineChart(
                    points = state.progression.map {
                        ChartPoint(it.date.format(shortDate), it.estimated1Rm)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Estimated 1RM (Epley: weight × (1 + reps ÷ 30)), taken from your best set " +
                        "each session. This is an estimate from submaximal work — not a tested " +
                        "one-rep max — and it becomes less reliable above 12 reps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WinterArcColors.Faint,
                )
            }

            if (state.bodyWeightSeries.isNotEmpty()) {
                SectionLabel("Bodyweight")
                WinterCard {
                    LineChart(
                        points = state.bodyWeightSeries.map {
                            ChartPoint(it.first.format(shortDate), it.second)
                        },
                        lineColor = WinterArcColors.NightBlueBright,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.recentPrs.isNotEmpty()) {
                SectionLabel("Recent records")
                state.recentPrs.forEach { (pr, name) ->
                    WinterCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${pr.type.label} · ${pr.date.format(shortDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WinterArcColors.Muted,
                                )
                            }
                            Text(
                                when (pr.type) {
                                    PrType.REPS -> "${pr.value.roundToInt()} reps"
                                    PrType.SESSION_VOLUME -> "${pr.value.roundToInt()} kg"
                                    else -> "${(pr.value * 10).roundToInt() / 10.0} kg"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = WinterArcColors.GoldBright,
                            )
                        }
                        pr.previousValue?.let { prev ->
                            Text(
                                "Previous best ${(prev * 10).roundToInt() / 10.0}",
                                style = MaterialTheme.typography.bodySmall,
                                color = WinterArcColors.Faint,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun RangeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) WinterArcColors.NightDeep else WinterArcColors.White,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) WinterArcColors.Gold else WinterArcColors.NightElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
