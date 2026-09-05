package com.winterarc.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.kit.ArcCard
import com.winterarc.app.ui.kit.EmptyState
import com.winterarc.app.ui.kit.Label
import com.winterarc.app.ui.kit.Metric
import com.winterarc.app.ui.kit.MetricRow
import com.winterarc.app.ui.kit.Pill
import com.winterarc.app.ui.theme.W
import com.winterarc.core.AppData
import com.winterarc.core.Fmt
import com.winterarc.core.Session
import java.time.format.DateTimeFormatter

private val monthLabel = DateTimeFormatter.ofPattern("MMMM yyyy")
private val dayNumber = DateTimeFormatter.ofPattern("d")
private val dayName = DateTimeFormatter.ofPattern("EEE")

/**
 * The permanent record.
 *
 * Every finished session, exactly as performed. Nothing here is recomputed from the programme,
 * so editing the plan tomorrow leaves January untouched.
 */
@Composable
fun HistoryScreen(
    data: AppData,
    onOpenSession: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val history = remember(data.sessions) { data.history }
    val unit = data.prefs.unit
    val grouped = remember(history) { history.groupBy { it.date.withDayOfMonth(1) } }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
    ) {
        item {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Spacer(Modifier.height(14.dp))
                Text("History", style = MaterialTheme.typography.headlineLarge, color = W.Ink)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${history.size} ${if (history.size == 1) "session" else "sessions"} recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = W.Faint,
                )
                Spacer(Modifier.height(18.dp))
            }
        }

        if (history.isEmpty()) {
            item {
                EmptyState(
                    title = "No sessions yet",
                    message = "Finish a workout and it is kept here permanently — every set, " +
                        "every weight, exactly as you performed it.",
                )
            }
        } else {
            item {
                MetricRow {
                    ArcCard(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(15.dp)) {
                        Metric(
                            "Lifetime volume",
                            Fmt.volume(history.sumOf { it.volumeKg }, unit),
                            unit = unit.suffix,
                            valueColor = W.GoldBright,
                        )
                    }
                    ArcCard(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(15.dp)) {
                        Metric("Total sets", history.sumOf { it.workingSetCount }.toString())
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            grouped.forEach { (month, sessions) ->
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Label(month.format(monthLabel), color = W.Muted)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${sessions.size} · ${Fmt.volume(sessions.sumOf { it.volumeKg }, unit)} ${unit.suffix}",
                            style = MaterialTheme.typography.labelSmall,
                            color = W.Ghost,
                        )
                    }
                }
                items(sessions.size) { index ->
                    SessionRow(
                        session = sessions[index],
                        data = data,
                        onClick = { onOpenSession(sessions[index].id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: Session, data: AppData, onClick: () -> Unit) {
    val unit = data.prefs.unit
    val accent = W.accent(session.accent)

    ArcCard(
        Modifier.padding(bottom = 9.dp),
        onClick = onClick,
        padding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    session.date.format(dayNumber),
                    style = MaterialTheme.typography.headlineSmall,
                    color = W.Ink,
                )
                Text(
                    session.date.format(dayName).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = W.Ghost,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(width = 2.dp, height = 34.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(13.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = W.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${session.exercises.count { !it.skipped }} movements · " +
                        "${session.workingSetCount} sets · ${session.totalReps} reps",
                    style = MaterialTheme.typography.bodySmall,
                    color = W.Faint,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${Fmt.volume(session.volumeKg, unit)} ${unit.suffix}",
                    style = MaterialTheme.typography.titleSmall,
                    color = W.Muted,
                )
                session.durationMinutes?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        Fmt.duration(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = W.Ghost,
                    )
                }
                if (session.extraSets > 0) {
                    Spacer(Modifier.height(4.dp))
                    Pill("+${session.extraSets}", color = W.Good)
                }
            }
        }
    }
}
