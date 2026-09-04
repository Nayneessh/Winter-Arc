package com.winterarc.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.kit.ArcCard
import com.winterarc.app.ui.kit.EmptyState
import com.winterarc.app.ui.kit.GhostButton
import com.winterarc.app.ui.kit.HairLine
import com.winterarc.app.ui.kit.Label
import com.winterarc.app.ui.kit.Metric
import com.winterarc.app.ui.kit.OverlayScreen
import com.winterarc.app.ui.kit.Pill
import com.winterarc.app.ui.kit.RoundIcon
import com.winterarc.app.ui.kit.SectionHeader
import com.winterarc.app.ui.kit.SheetAction
import com.winterarc.app.ui.kit.SheetTitle
import com.winterarc.app.ui.kit.WinterSheet
import com.winterarc.app.ui.theme.W
import com.winterarc.core.Actions
import com.winterarc.core.Analytics
import com.winterarc.core.AppData
import com.winterarc.core.Fmt
import java.time.format.DateTimeFormatter

private val fullDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")

/** One session, exactly as it was performed. */
@Composable
fun SessionDetailScreen(
    data: AppData,
    sessionId: String,
    onUpdate: ((AppData) -> AppData) -> Unit,
    onBack: () -> Unit,
) {
    val session = data.sessions.firstOrNull { it.id == sessionId }
    var confirming by remember { mutableStateOf(false) }
    val unit = data.prefs.unit

    if (session == null) {
        OverlayScreen(title = "Session", onBack = onBack) {
            EmptyState("Not found", "This session is no longer in your history.")
        }
        return
    }

    val records = remember(session.id, data.sessions) {
        Analytics.recordsFor(session, data.sessions.filter { it.finished }, data.exerciseById)
    }

    OverlayScreen(
        title = session.title,
        subtitle = session.date.format(fullDate),
        onBack = onBack,
        actions = {
            RoundIcon(Icons.Filled.DeleteOutline, "Delete session", { confirming = true }, tint = W.Bad)
        },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 30.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                ArcCard(Modifier.weight(1f), padding = PaddingValues(14.dp)) {
                    Metric(
                        "Volume",
                        Fmt.volume(session.volumeKg, unit),
                        unit = unit.suffix,
                        valueColor = W.GoldBright,
                    )
                }
                ArcCard(Modifier.weight(1f), padding = PaddingValues(14.dp)) {
                    Metric("Sets", session.workingSetCount.toString())
                }
                ArcCard(Modifier.weight(1f), padding = PaddingValues(14.dp)) {
                    Metric("Reps", session.totalReps.toString())
                }
            }

            session.durationMinutes?.let {
                Spacer(Modifier.height(11.dp))
                ArcCard(padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Duration",
                            style = MaterialTheme.typography.bodyMedium,
                            color = W.Faint,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Fmt.duration(it),
                            style = MaterialTheme.typography.titleMedium,
                            color = W.Ink,
                        )
                    }
                }
            }

            if (records.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Records set")
                ArcCard {
                    records.forEachIndexed { index, pr ->
                        if (index > 0) Spacer(Modifier.height(9.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    pr.exerciseName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = W.Ink,
                                )
                                Text(
                                    pr.type.display,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = W.Faint,
                                )
                            }
                            Text(
                                Fmt.trim(pr.value),
                                style = MaterialTheme.typography.titleMedium,
                                color = W.GoldBright,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Performed")
            session.exercises.forEach { exercise ->
                ArcCard(Modifier.padding(bottom = 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                data.exerciseName(exercise.exerciseId),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (exercise.skipped) W.Faint else W.Ink,
                            )
                            Text(
                                "${exercise.plannedSets} × ${exercise.repRange} prescribed",
                                style = MaterialTheme.typography.labelSmall,
                                color = W.Ghost,
                            )
                        }
                        if (exercise.extraSets > 0) {
                            Pill("+${exercise.extraSets} extra", color = W.Good)
                        }
                    }

                    if (exercise.skipped) {
                        Spacer(Modifier.height(8.dp))
                        Text("Skipped", style = MaterialTheme.typography.bodySmall, color = W.Warn)
                    } else {
                        Spacer(Modifier.height(11.dp))
                        HairLine()
                        Spacer(Modifier.height(8.dp))
                        exercise.sets.forEachIndexed { index, set ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (set.warmup) "W" else "${index + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (set.warmup) W.Warn else W.Ghost,
                                    modifier = Modifier.width(24.dp),
                                )
                                Text(
                                    "${Fmt.weight(set.weightKg, unit)} ${unit.suffix}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = W.Ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "× ${set.reps}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = W.Ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (set.warmup) "warm-up" else "${Fmt.trim(set.volumeKg)} ${unit.suffix}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = W.Faint,
                                )
                            }
                        }
                    }
                }
            }

            if (session.note.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Note")
                ArcCard {
                    Text(session.note, style = MaterialTheme.typography.bodyMedium, color = W.Muted)
                }
            }
        }
    }

    if (confirming) {
        WinterSheet(onDismiss = { confirming = false }) {
            SheetTitle(
                "Delete this session?",
                "It is removed from your history permanently, and every figure computed from it " +
                    "changes. This cannot be undone.",
            )
            SheetAction(Icons.Filled.DeleteOutline, "Delete permanently", tint = W.Bad) {
                onUpdate { Actions.deleteSession(it, sessionId) }
                confirming = false
                onBack()
            }
            Spacer(Modifier.height(8.dp))
            GhostButton("Keep it", { confirming = false }, Modifier.fillMaxWidth())
        }
    }
}
