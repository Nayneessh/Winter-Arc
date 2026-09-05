package com.winterarc.app.ui.train

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.kit.ArcCard
import com.winterarc.app.ui.kit.GoldButton
import com.winterarc.app.ui.kit.Label
import com.winterarc.app.ui.kit.Metric
import com.winterarc.app.ui.kit.MetricRow
import com.winterarc.app.ui.kit.SectionHeader
import com.winterarc.app.ui.theme.Grad
import com.winterarc.app.ui.theme.W
import com.winterarc.core.Analytics
import com.winterarc.core.AppData
import com.winterarc.core.Fmt
import com.winterarc.core.PrType
import com.winterarc.core.Session

/**
 * What the session amounted to.
 *
 * Records are computed against real prior performance, so this screen only celebrates something
 * that was actually beaten. A first-ever performance says nothing here -- if everything is a
 * record, the word stops meaning anything.
 */
@Composable
fun SessionSummary(
    data: AppData,
    session: Session,
    onDone: () -> Unit,
) {
    val records = remember(session.id, data.sessions) {
        Analytics.recordsFor(session, data.sessions.filter { it.finished }, data.exerciseById)
    }
    val unit = data.prefs.unit

    Box(Modifier.fillMaxSize().background(Grad.screen)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(30.dp))
            Label("Session complete", color = W.Good)
            Spacer(Modifier.height(8.dp))
            Text(session.title, style = MaterialTheme.typography.displaySmall, color = W.Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                session.durationMinutes?.let { "Trained for ${Fmt.duration(it)}" } ?: "Logged",
                style = MaterialTheme.typography.bodyMedium,
                color = W.Faint,
            )

            Spacer(Modifier.height(22.dp))
            MetricRow {
                ArcCard(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(15.dp)) {
                    Metric(
                        "Volume",
                        Fmt.volume(session.volumeKg, unit),
                        unit = unit.suffix,
                        valueColor = W.GoldBright,
                    )
                }
                ArcCard(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(15.dp)) {
                    Metric("Sets", session.workingSetCount.toString())
                }
                ArcCard(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(15.dp)) {
                    Metric("Reps", session.totalReps.toString())
                }
            }

            if (session.extraSets > 0) {
                Spacer(Modifier.height(11.dp))
                ArcCard(
                    brush = Brush.verticalGradient(listOf(W.Good.copy(alpha = 0.14f), W.Night)),
                    borderColor = W.Good.copy(alpha = 0.3f),
                ) {
                    Text(
                        "${session.extraSets} ${if (session.extraSets == 1) "set" else "sets"} beyond the plan",
                        style = MaterialTheme.typography.titleMedium,
                        color = W.Good,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Recorded as extra. The prescription is left as it was written.",
                        style = MaterialTheme.typography.bodySmall,
                        color = W.Faint,
                    )
                }
            }

            if (records.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionHeader("Personal records")
                records.take(6).forEach { record ->
                    ArcCard(
                        Modifier.padding(bottom = 9.dp),
                        brush = Brush.verticalGradient(listOf(W.GoldFilm, W.Night)),
                        borderColor = W.GoldEdge,
                        padding = PaddingValues(15.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).background(W.GoldFilm),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.EmojiEvents, null,
                                    tint = W.Gold, modifier = Modifier.size(19.dp),
                                )
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    record.exerciseName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = W.Ink,
                                )
                                Text(
                                    record.type.display,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = W.Faint,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    formatRecord(record.type, record.value, data),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = W.GoldBright,
                                )
                                record.previous?.let {
                                    Text(
                                        "was ${formatRecord(record.type, it, data)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = W.Ghost,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("What you did")
            session.exercises.forEach { exercise ->
                ArcCard(Modifier.padding(bottom = 8.dp), padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                data.exerciseName(exercise.exerciseId),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (exercise.skipped) W.Faint else W.Ink,
                            )
                            Text(
                                if (exercise.skipped) {
                                    "Skipped"
                                } else {
                                    exercise.workingSets.joinToString("   ") {
                                        "${Fmt.weight(it.weightKg, unit)}×${it.reps}"
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = W.Faint,
                            )
                        }
                        if (!exercise.skipped) {
                            Text(
                                "${Fmt.volume(exercise.volumeKg, unit)} ${unit.suffix}",
                                style = MaterialTheme.typography.titleSmall,
                                color = W.Muted,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            GoldButton("DONE", onDone, Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

private fun formatRecord(type: PrType, value: Double, data: AppData): String = when (type) {
    PrType.REPS -> "${value.toInt()}"
    PrType.WEIGHT, PrType.E1RM -> Fmt.weightWithUnit(value, data.prefs.unit)
    PrType.VOLUME -> "${Fmt.volume(value, data.prefs.unit)} ${data.prefs.unit.suffix}"
}
