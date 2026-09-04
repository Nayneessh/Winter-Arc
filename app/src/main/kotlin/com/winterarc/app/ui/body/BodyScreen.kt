package com.winterarc.app.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.winterarc.app.AppContainer
import com.winterarc.app.ui.components.*
import com.winterarc.app.ui.theme.WinterArcColors
import com.winterarc.domain.analytics.BodyAnalytics
import com.winterarc.domain.analytics.FatMassEstimate
import com.winterarc.domain.analytics.round1
import com.winterarc.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy")

data class BodyUiState(
    val entries: List<BodyMetric> = emptyList(),
    val deltas: List<MetricDelta> = emptyList(),
    val fatEstimate: FatMassEstimate? = null,
    val lengthUnit: LengthUnit = LengthUnit.IN,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val loading: Boolean = true,
)

class BodyViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val _state = MutableStateFlow(BodyUiState())
    val state: StateFlow<BodyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settings.settings.collect { s ->
                _state.value = _state.value.copy(lengthUnit = s.lengthUnit, weightUnit = s.weightUnit)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val entries = repo.allBodyMetrics().sortedByDescending { it.date }
            _state.value = _state.value.copy(
                entries = entries,
                deltas = BodyAnalytics.allDeltas(entries),
                fatEstimate = BodyAnalytics.fatMassEstimate(entries),
                loading = false,
            )
        }
    }

    fun save(metric: BodyMetric) {
        viewModelScope.launch { repo.saveBodyMetric(metric); refresh() }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.deleteBodyMetric(id); refresh() }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BodyViewModel(container) as T
    }
}

/**
 * Body tracking.
 *
 * The wording throughout comes from the domain layer's statement helpers rather than being
 * written inline, so the rule that a weight change is never described as fat loss is enforced
 * in one tested place instead of relied upon in each label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScreen(
    state: BodyUiState,
    onSave: (BodyMetric) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showEntry by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = WinterArcColors.NightDeep,
        topBar = {
            TopAppBar(
                title = { Text("Body") },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showEntry = true },
                containerColor = WinterArcColors.Gold,
                contentColor = WinterArcColors.NightDeep,
            ) { Icon(Icons.Default.Add, "New check-in") }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val weightDelta = state.deltas.firstOrNull { it.field == MetricField.WEIGHT }

            WinterCard(accent = true) {
                SectionLabel("Weight")
                Text(
                    BodyAnalytics.weightChangeStatement(weightDelta),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (weightDelta != null && weightDelta.change < 0) {
                        WinterArcColors.Success
                    } else {
                        WinterArcColors.White
                    },
                )
                if (weightDelta != null) {
                    Text(
                        "${weightDelta.startValue.round1()} kg on " +
                            "${weightDelta.startDate.format(shortDate)} → " +
                            "${weightDelta.currentValue.round1()} kg on " +
                            weightDelta.currentDate.format(shortDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = WinterArcColors.Muted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This is a change in scale weight. It is not a measure of fat lost or " +
                            "muscle gained — add body-fat readings if you want an estimated split.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WinterArcColors.Faint,
                    )
                }
            }

            state.fatEstimate?.let { est ->
                WinterCard {
                    SectionLabel("Estimated composition change")
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        StatTile(
                            "Fat mass",
                            "${est.fatMassChangeKg.round1()} kg",
                            valueColor = if (est.fatMassChangeKg < 0) WinterArcColors.Success else WinterArcColors.White,
                        )
                        StatTile(
                            "Lean mass",
                            "${est.leanMassChangeKg.round1()} kg",
                            valueColor = if (est.leanMassChangeKg >= 0) WinterArcColors.Success else WinterArcColors.Warning,
                        )
                        StatTile("Body fat", "${est.bodyFatPercentChange.round1()}%")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        est.caveat,
                        style = MaterialTheme.typography.bodySmall,
                        color = WinterArcColors.Faint,
                    )
                }
            }

            val measurementFields = listOf(
                MetricField.CHEST, MetricField.WAIST, MetricField.BICEPS_LEFT,
                MetricField.BICEPS_RIGHT, MetricField.SHOULDERS, MetricField.THIGH,
                MetricField.CALF, MetricField.NECK, MetricField.FOREARM,
            )
            val shown = state.deltas.filter { it.field in measurementFields }
            if (shown.isNotEmpty()) {
                SectionLabel("Measurements")
                shown.forEach { d ->
                    val display = { v: Double -> UnitConversion.lengthFromCm(v, state.lengthUnit).round1() }
                    WinterCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(d.field.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    BodyAnalytics.measurementChangeStatement(d, state.lengthUnit.label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WinterArcColors.Muted,
                                )
                            }
                            Text(
                                "${display(d.startValue)} → ${display(d.currentValue)} ${state.lengthUnit.label}",
                                style = MaterialTheme.typography.titleMedium,
                                color = WinterArcColors.GoldBright,
                            )
                        }
                    }
                }
            }

            val weightSeries = state.entries
                .filter { it.weightKg != null }
                .sortedBy { it.date }
                .map { ChartPoint(it.date.format(shortDate), it.weightKg!!) }
            if (weightSeries.size >= 2) {
                SectionLabel("Weight trend")
                WinterCard { LineChart(weightSeries, Modifier.fillMaxWidth()) }
            }

            SectionLabel("Check-ins")
            if (state.entries.isEmpty()) {
                EmptyState("No check-ins yet", "Record your first measurements to start tracking.")
            }
            state.entries.forEach { m ->
                WinterCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.date.format(shortDate), style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    m.weightKg?.let { append("${it.round1()} kg  ") }
                                    m.waistCm?.let {
                                        append("waist ${UnitConversion.lengthFromCm(it, state.lengthUnit).round1()}${state.lengthUnit.label}  ")
                                    }
                                    m.bodyFatPercent?.let { append("${it.round1()}% bf") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = WinterArcColors.Muted,
                            )
                            m.notes?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = WinterArcColors.Faint)
                            }
                        }
                        TextButton(onClick = { onDelete(m.id) }) {
                            Text("Delete", color = WinterArcColors.Danger)
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showEntry) {
        CheckInSheet(
            lengthUnit = state.lengthUnit,
            onDismiss = { showEntry = false },
            onSave = { onSave(it); showEntry = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckInSheet(
    lengthUnit: LengthUnit,
    onDismiss: () -> Unit,
    onSave: (BodyMetric) -> Unit,
) {
    var weight by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var bicepsL by remember { mutableStateOf("") }
    var bicepsR by remember { mutableStateOf("") }
    var shoulders by remember { mutableStateOf("") }
    var thigh by remember { mutableStateOf("") }
    var calf by remember { mutableStateOf("") }
    var neck by remember { mutableStateOf("") }
    var forearm by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    fun len(v: String): Double? =
        v.toDoubleOrNull()?.let { UnitConversion.lengthToCm(it, lengthUnit) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WinterArcColors.NightSurface,
        contentColor = WinterArcColors.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Text("New check-in", style = MaterialTheme.typography.headlineSmall)
            Text(
                LocalDate.now().format(shortDate),
                style = MaterialTheme.typography.bodySmall,
                color = WinterArcColors.Muted,
            )
            Spacer(Modifier.height(14.dp))

            NumInput("Weight (kg)", weight) { weight = it }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumInput("Chest (${lengthUnit.label})", chest, Modifier.weight(1f)) { chest = it }
                NumInput("Waist (${lengthUnit.label})", waist, Modifier.weight(1f)) { waist = it }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumInput("Biceps L", bicepsL, Modifier.weight(1f)) { bicepsL = it }
                NumInput("Biceps R", bicepsR, Modifier.weight(1f)) { bicepsR = it }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumInput("Shoulders", shoulders, Modifier.weight(1f)) { shoulders = it }
                NumInput("Thigh", thigh, Modifier.weight(1f)) { thigh = it }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumInput("Calf", calf, Modifier.weight(1f)) { calf = it }
                NumInput("Neck", neck, Modifier.weight(1f)) { neck = it }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumInput("Forearm", forearm, Modifier.weight(1f)) { forearm = it }
                NumInput("Body fat %", bodyFat, Modifier.weight(1f)) { bodyFat = it }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Leave anything blank that you did not measure. Blank is recorded as " +
                    "not measured, never as zero.",
                style = MaterialTheme.typography.bodySmall,
                color = WinterArcColors.Faint,
            )

            Spacer(Modifier.height(16.dp))
            GoldButton(
                text = "Save check-in",
                onClick = {
                    onSave(
                        BodyMetric(
                            id = UUID.randomUUID().toString(),
                            date = LocalDate.now(),
                            weightKg = weight.toDoubleOrNull(),
                            chestCm = len(chest),
                            waistCm = len(waist),
                            bicepsLeftCm = len(bicepsL),
                            bicepsRightCm = len(bicepsR),
                            shouldersCm = len(shoulders),
                            thighCm = len(thigh),
                            calfCm = len(calf),
                            neckCm = len(neck),
                            forearmCm = len(forearm),
                            bodyFatPercent = bodyFat.toDoubleOrNull(),
                            notes = notes.takeIf { it.isNotBlank() },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NumInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() || it == '.' }) },
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
