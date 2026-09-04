package com.winterarc.app.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.kit.ArcCard
import com.winterarc.app.ui.kit.AreaChart
import com.winterarc.app.ui.kit.ChartAxis
import com.winterarc.app.ui.kit.EmptyState
import com.winterarc.app.ui.kit.GhostButton
import com.winterarc.app.ui.kit.GoalRing
import com.winterarc.app.ui.kit.GoldButton
import com.winterarc.app.ui.kit.Label
import com.winterarc.app.ui.kit.Metric
import com.winterarc.app.ui.kit.Pill
import com.winterarc.app.ui.kit.SectionHeader
import com.winterarc.app.ui.kit.SheetTitle
import com.winterarc.app.ui.kit.WinterField
import com.winterarc.app.ui.kit.WinterSheet
import com.winterarc.app.ui.theme.W
import com.winterarc.core.Actions
import com.winterarc.core.Analytics
import com.winterarc.core.AppData
import com.winterarc.core.BodyEntry
import com.winterarc.core.Fmt
import com.winterarc.core.MeasurementSites
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val entryDate = DateTimeFormatter.ofPattern("d MMM yyyy")
private val axisDate = DateTimeFormatter.ofPattern("d MMM")

/**
 * Body composition and measurements.
 *
 * Weight is called weight. It is never described as fat lost or muscle gained, because a scale
 * cannot tell the difference -- only a check-in carrying both a weight and a body-fat reading
 * can, and those are the only ones the composition figures are computed from.
 */
@Composable
fun BodyScreen(
    data: AppData,
    onUpdate: ((AppData) -> AppData) -> Unit,
) {
    val stats = remember(data.body) { Analytics.bodyStats(data.body) }
    val goals = remember(data) { Analytics.goalProgress(data) }
        .filter { it.label == "Bodyweight" || it.label == "Body fat" }
    val unit = data.prefs.unit
    var editing by remember { mutableStateOf<BodyEntry?>(null) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
    ) {
        item {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Spacer(Modifier.height(14.dp))
                Text("Body", style = MaterialTheme.typography.headlineLarge, color = W.Ink)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${stats.entryCount} ${if (stats.entryCount == 1) "check-in" else "check-ins"} recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = W.Faint,
                )
                Spacer(Modifier.height(18.dp))
            }
        }

        item {
            GoldButton(
                "LOG A CHECK-IN",
                { creating = true },
                Modifier.fillMaxWidth(),
                icon = Icons.Filled.Add,
            )
            Spacer(Modifier.height(20.dp))
        }

        if (goals.isNotEmpty()) {
            item {
                SectionHeader("Targets")
                ArcCard(padding = PaddingValues(vertical = 20.dp, horizontal = 26.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        goals.forEachIndexed { index, goal ->
                            GoalRing(
                                label = goal.label,
                                value = if (goal.unit == "%") Fmt.trim(goal.current)
                                else Fmt.weight(goal.current, unit),
                                unit = goal.unit,
                                progress = goal.fraction.toFloat(),
                                color = if (index == 0) W.Cyan else W.Warn,
                                footnote = "→ ${Fmt.trim(goal.target)} ${goal.unit}",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        item {
            SectionHeader("Now")
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                    Metric(
                        "Weight",
                        stats.latestWeightKg?.let { Fmt.weight(it, unit) } ?: "—",
                        unit = unit.suffix,
                        valueColor = W.Ink,
                        caption = stats.weightTrend?.delta?.let {
                            "${Fmt.signed(Fmt.toDisplayWeight(it, unit), 1)} since last"
                        },
                    )
                }
                ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                    Metric(
                        "Body fat",
                        stats.latestBodyFatPct?.let { Fmt.trim(it) } ?: "—",
                        unit = "%",
                        caption = stats.bodyFatTrend?.delta?.let { "${Fmt.signed(it, 1)} since last" },
                    )
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                    Metric(
                        "Lean mass",
                        stats.leanMassKg?.let { Fmt.weight(it, unit) } ?: "—",
                        unit = unit.suffix,
                        valueColor = W.Good,
                        caption = stats.leanMassChangeKg?.let {
                            "${Fmt.signed(Fmt.toDisplayWeight(it, unit), 1)} overall"
                        } ?: "needs weight + body fat",
                    )
                }
                ArcCard(Modifier.weight(1f), padding = PaddingValues(15.dp)) {
                    Metric(
                        "Fat mass",
                        stats.fatMassKg?.let { Fmt.weight(it, unit) } ?: "—",
                        unit = unit.suffix,
                        valueColor = W.Warn,
                        caption = stats.fatMassChangeKg?.let {
                            "${Fmt.signed(Fmt.toDisplayWeight(it, unit), 1)} overall"
                        } ?: "needs weight + body fat",
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (stats.weightSeries.size >= 2) {
            item {
                SectionHeader("Weight")
                ArcCard {
                    AreaChart(
                        values = stats.weightSeries.map { it.value },
                        color = W.Cyan,
                        height = 150.dp,
                    )
                    ChartAxis(stats.weightSeries.map { it.date.format(axisDate) })
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        if (stats.bodyFatSeries.size >= 2) {
            item {
                SectionHeader("Body fat")
                ArcCard {
                    AreaChart(
                        values = stats.bodyFatSeries.map { it.value },
                        color = W.Warn,
                        height = 130.dp,
                    )
                    ChartAxis(stats.bodyFatSeries.map { it.date.format(axisDate) })
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        if (stats.measurementDeltas.isNotEmpty()) {
            item {
                SectionHeader("Measurements")
                ArcCard {
                    stats.measurementDeltas.forEachIndexed { index, delta ->
                        if (index > 0) Spacer(Modifier.height(11.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    delta.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = W.Ink,
                                )
                                Text(
                                    "from ${Fmt.trim(delta.firstCm)} cm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = W.Ghost,
                                )
                            }
                            Text(
                                "${Fmt.trim(delta.latestCm)} cm",
                                style = MaterialTheme.typography.titleMedium,
                                color = W.Ink,
                            )
                            Spacer(Modifier.width(11.dp))
                            Pill(
                                "${Fmt.signed(delta.deltaCm, 1)} cm",
                                color = if (delta.deltaCm >= 0) W.Good else W.Warn,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        if (data.body.isEmpty()) {
            item {
                EmptyState(
                    title = "No check-ins yet",
                    message = "Log a weight, a body-fat estimate and a few tape measurements. " +
                        "Two readings are enough to draw a trend.",
                )
            }
        } else {
            item { SectionHeader("Check-ins") }
            val entries = data.bodyByDate.reversed()
            items(entries.size) { index ->
                val entry = entries[index]
                ArcCard(
                    Modifier.padding(bottom = 9.dp),
                    onClick = { editing = entry },
                    padding = PaddingValues(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.date.format(entryDate),
                                style = MaterialTheme.typography.titleSmall,
                                color = W.Muted,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                listOfNotNull(
                                    entry.weightKg?.let { "${Fmt.weight(it, unit)} ${unit.suffix}" },
                                    entry.bodyFatPct?.let { "${Fmt.trim(it)}% fat" },
                                    entry.measurementsCm.size.takeIf { it > 0 }
                                        ?.let { "$it measurements" },
                                ).joinToString(" · ").ifBlank { "No values recorded" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = W.Ink,
                            )
                            if (entry.note.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    entry.note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = W.Ghost,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        CheckInSheet(
            data = data,
            existing = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { entry ->
                onUpdate { Actions.saveBodyEntry(it, entry) }
                creating = false
                editing = null
            },
            onDelete = { id ->
                onUpdate { Actions.deleteBodyEntry(it, id) }
                creating = false
                editing = null
            },
        )
    }
}

/**
 * Recording a check-in.
 *
 * Every field is optional. A weight on its own is a perfectly good entry, and demanding a full
 * tape measurement to log one is how a tracker stops being used by week three.
 */
@Composable
private fun CheckInSheet(
    data: AppData,
    existing: BodyEntry?,
    onDismiss: () -> Unit,
    onSave: (BodyEntry) -> Unit,
    onDelete: (String) -> Unit,
) {
    val unit = data.prefs.unit
    var weight by remember {
        mutableStateOf(existing?.weightKg?.let { Fmt.weight(it, unit) } ?: "")
    }
    var fat by remember { mutableStateOf(existing?.bodyFatPct?.let { Fmt.trim(it) } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val measurements = remember {
        mutableStateMapOf<String, String>().apply {
            MeasurementSites.ordered.forEach { (key, _) ->
                put(key, existing?.measurementsCm?.get(key)?.let { Fmt.trim(it) } ?: "")
            }
        }
    }

    WinterSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 560.dp)) {
            SheetTitle(
                if (existing == null) "New check-in" else "Edit check-in",
                existing?.date?.format(entryDate) ?: LocalDate.now().format(entryDate),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                WinterField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = "Weight (${unit.suffix})",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                WinterField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = "Body fat (%)",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(18.dp))
            Label("Measurements — centimetres")
            Spacer(Modifier.height(10.dp))

            MeasurementSites.ordered.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    pair.forEach { (key, label) ->
                        WinterField(
                            value = measurements[key].orEmpty(),
                            onValueChange = { measurements[key] = it },
                            label = label,
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            WinterField(
                value = note,
                onValueChange = { note = it },
                label = "Note",
                singleLine = false,
            )

            Spacer(Modifier.height(18.dp))
            GoldButton(
                "SAVE CHECK-IN",
                {
                    val entry = BodyEntry(
                        id = existing?.id ?: com.winterarc.core.newId(),
                        date = existing?.date ?: LocalDate.now(),
                        weightKg = weight.toDoubleOrNull()
                            ?.let { Fmt.fromDisplayWeight(it, unit) },
                        bodyFatPct = fat.toDoubleOrNull(),
                        measurementsCm = measurements
                            .mapNotNull { (key, value) ->
                                value.toDoubleOrNull()?.let { key to it }
                            }
                            .toMap(),
                        note = note,
                    )
                    onSave(entry)
                },
                Modifier.fillMaxWidth(),
            )

            if (existing != null) {
                Spacer(Modifier.height(10.dp))
                GhostButton(
                    "Delete this check-in",
                    { onDelete(existing.id) },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.DeleteOutline,
                    color = W.Bad,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
