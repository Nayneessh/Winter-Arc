package com.winterarc.app.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.components.*
import com.winterarc.app.ui.theme.WinterArcColors
import com.winterarc.domain.analytics.PersonalRecord
import com.winterarc.domain.analytics.PrType
import com.winterarc.domain.model.WorkoutSession
import kotlin.math.roundToInt

/** End-of-session summary: what was planned, what was actually done, and what was a record. */
@Composable
fun WorkoutCompleteScreen(
    session: WorkoutSession,
    records: List<PersonalRecord>,
    names: Map<String, String>,
    onDone: () -> Unit,
) {
    Scaffold(containerColor = WinterArcColors.NightDeep) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "WORKOUT COMPLETE",
                style = MaterialTheme.typography.headlineMedium,
                color = WinterArcColors.Gold,
                fontWeight = FontWeight.Bold,
            )
            Text(
                session.name,
                style = MaterialTheme.typography.titleLarge,
                color = WinterArcColors.White,
            )
            session.durationSeconds?.let {
                Text(
                    "${it / 60} min ${it % 60} s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WinterArcColors.Muted,
                )
            }

            Spacer(Modifier.height(24.dp))

            WinterCard(accent = true, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatTile("Exercises", "${session.performedExercises.size}")
                    StatTile("Sets", "${session.actualSetTotal}", caption = "of ${session.plannedSetTotal} planned")
                    StatTile(
                        "Extra",
                        "${session.extraSetTotal}",
                        valueColor = if (session.extraSetTotal > 0) WinterArcColors.Success else WinterArcColors.White,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatTile("Reps", "${session.totalReps}")
                    StatTile("Volume", "${session.totalVolumeKg.roundToInt()}", caption = "kg")
                    StatTile(
                        "Records",
                        "${records.size}",
                        valueColor = if (records.isNotEmpty()) WinterArcColors.GoldBright else WinterArcColors.White,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (records.isNotEmpty()) {
                SectionLabel("Personal records")
                records.forEach { pr ->
                    WinterCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏆", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    names[pr.exerciseId] ?: "Exercise",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    pr.type.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WinterArcColors.Gold,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    when (pr.type) {
                                        PrType.REPS -> "${pr.value.roundToInt()} reps"
                                        PrType.SESSION_VOLUME -> "${pr.value.roundToInt()} kg"
                                        else -> "${(pr.value * 10).roundToInt() / 10.0} kg"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = WinterArcColors.GoldBright,
                                )
                                Text(
                                    pr.previousValue?.let { "was ${(it * 10).roundToInt() / 10.0}" }
                                        ?: "first recorded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WinterArcColors.Faint,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            SectionLabel("What you did")
            session.performedExercises.sortedBy { it.position }.forEach { pe ->
                WinterCard(modifier = Modifier.fillMaxWidth()) {
                    Text(names[pe.exerciseId] ?: "Exercise", style = MaterialTheme.typography.titleMedium)
                    Text(
                        pe.workingSets.joinToString("   ") { "${it.weightKg}×${it.reps}" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = WinterArcColors.White,
                    )
                    Text(
                        buildString {
                            append("${pe.actualSetCount} of ${pe.plannedSets} planned sets")
                            if (pe.extraSetCount > 0) append("  ·  +${pe.extraSetCount} extra")
                            if (pe.missedSetCount > 0) append("  ·  ${pe.missedSetCount} not performed")
                            append("  ·  ${pe.volumeKg.roundToInt()} kg")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = WinterArcColors.Muted,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            session.notes?.takeIf { it.isNotBlank() }?.let {
                WinterCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Notes", style = MaterialTheme.typography.labelSmall, color = WinterArcColors.Muted)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))
            GoldButton("DONE", onDone, Modifier.fillMaxWidth())
            Spacer(Modifier.height(32.dp))
        }
    }
}
