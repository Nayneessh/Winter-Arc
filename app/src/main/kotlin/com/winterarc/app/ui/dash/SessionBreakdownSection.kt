package com.winterarc.app.ui.dash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.kit.ArcCard
import com.winterarc.app.ui.kit.ChangePill
import com.winterarc.app.ui.kit.EmptyState
import com.winterarc.app.ui.kit.Pill
import com.winterarc.app.ui.kit.SectionHeader
import com.winterarc.app.ui.kit.SetLadder
import com.winterarc.app.ui.kit.SetLadderLegend
import com.winterarc.app.ui.theme.W
import com.winterarc.core.Analytics
import com.winterarc.core.AppData
import com.winterarc.core.ExerciseBreakdown
import com.winterarc.core.Fmt
import com.winterarc.core.WeightUnit
import java.time.format.DateTimeFormatter

private val chipDate = DateTimeFormatter.ofPattern("d MMM")
private val fullDate = DateTimeFormatter.ofPattern("EEEE d MMMM")

/**
 * A training day, set by set.
 *
 * Averages hide the thing a lifter actually wants to know: which set was the top one, and where
 * the session started to fade. Every set of every movement is drawn individually, coloured by
 * what it did relative to the set before it, so the shape of the day is legible without reading
 * a single number.
 */
@Composable
fun SessionBreakdownSection(
    data: AppData,
    onOpenExercise: (String) -> Unit,
) {
    val sessions = remember(data.sessions) { data.history.take(10) }
    if (sessions.isEmpty()) {
        SectionHeader("Set by set")
        EmptyState(
            title = "No sessions to break down",
            message = "Finish a workout and every set of it is charted here — what climbed, " +
                "what held, and where you dropped the load.",
        )
        return
    }

    var selectedId by remember(sessions.firstOrNull()?.id) { mutableStateOf(sessions.first().id) }
    val session = sessions.firstOrNull { it.id == selectedId } ?: sessions.first()
    val breakdown = remember(session.id, data.sessions) {
        Analytics.sessionBreakdown(session, data.sessions, data.exerciseById)
    }
    val unit = data.prefs.unit

    SectionHeader("Set by set")

    if (sessions.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(sessions.size) { index ->
                val option = sessions[index]
                val on = option.id == session.id
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) W.GoldFilm else W.Void.copy(alpha = 0.5f))
                        .clickable { selectedId = option.id }
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                ) {
                    Text(
                        option.date.format(chipDate),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (on) W.GoldBright else W.Muted,
                    )
                    Text(
                        option.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = W.Ghost,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }

    ArcCard(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    breakdown.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = W.Ink,
                )
                Text(
                    breakdown.date.format(fullDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = W.Faint,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${Fmt.volume(breakdown.volumeKg, unit)} ${unit.suffix}",
                    style = MaterialTheme.typography.titleMedium,
                    color = W.GoldBright,
                )
                Text(
                    "${breakdown.setCount} sets · ${breakdown.repCount} reps",
                    style = MaterialTheme.typography.labelSmall,
                    color = W.Ghost,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SetLadderLegend()
    }

    Spacer(Modifier.height(11.dp))

    breakdown.exercises.forEach { exercise ->
        ExerciseLadderCard(
            exercise = exercise,
            unit = unit,
            onClick = { onOpenExercise(exercise.exerciseId) },
        )
    }
}

@Composable
private fun ExerciseLadderCard(
    exercise: ExerciseBreakdown,
    unit: WeightUnit,
    onClick: () -> Unit,
) {
    ArcCard(
        Modifier.padding(bottom = 11.dp),
        onClick = onClick,
        padding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (exercise.group.isNotBlank()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(W.Void.copy(alpha = 0.55f))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                ) {
                    Text(
                        exercise.group,
                        style = MaterialTheme.typography.labelSmall,
                        color = W.Faint,
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = W.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (exercise.skipped) {
                        "Skipped"
                    } else {
                        "Top set ${Fmt.weightWithUnit(exercise.topWeightKg, unit)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = W.Faint,
                )
            }
            ChangePill(exercise.vsPreviousKg, unit)
        }

        if (!exercise.skipped && exercise.sets.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SetLadder(sets = exercise.sets, unit = unit)
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("${exercise.sets.size} sets", color = W.Muted)
                Spacer(Modifier.width(6.dp))
                Pill("${exercise.totalReps} reps", color = W.Muted)
                Spacer(Modifier.width(6.dp))
                Pill(
                    "${Fmt.volume(exercise.volumeKg, unit)} ${unit.suffix}",
                    color = W.Muted,
                )
                if (exercise.extraSets > 0) {
                    Spacer(Modifier.width(6.dp))
                    Pill("+${exercise.extraSets} extra", color = W.Good)
                }
            }

            // The one sentence a lifter would say about the movement out loud.
            if (exercise.droppedBy > 0.05) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Peaked at ${Fmt.weightWithUnit(exercise.topWeightKg, unit)} and finished " +
                        "${Fmt.weightWithUnit(exercise.droppedBy, unit)} lighter.",
                    style = MaterialTheme.typography.labelSmall,
                    color = W.Ghost,
                )
            } else if (exercise.sets.size > 1) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Held the load across every set.",
                    style = MaterialTheme.typography.labelSmall,
                    color = W.Ghost,
                )
            }
        }
    }
}
