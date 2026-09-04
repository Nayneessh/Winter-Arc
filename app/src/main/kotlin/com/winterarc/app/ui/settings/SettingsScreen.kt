package com.winterarc.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.winterarc.app.AppContainer
import com.winterarc.app.data.prefs.UserSettings
import com.winterarc.app.data.repo.DataExporter
import com.winterarc.app.data.sync.SyncEngine
import com.winterarc.app.ui.components.*
import com.winterarc.app.ui.theme.WinterArcColors
import com.winterarc.domain.model.LengthUnit
import com.winterarc.domain.model.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val signedInUserId: String? = null,
    val syncConfigured: Boolean = false,
    val syncStatus: String? = null,
    val busy: Boolean = false,
    val exerciseCount: Int = 0,
    val sessionCount: Int = 0,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settings.settings.collect { s -> _state.value = _state.value.copy(settings = s) }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                signedInUserId = container.syncEngine.signedInUserId(),
                syncConfigured = container.syncEngine.isConfigured,
                exerciseCount = container.repository.allExercises().size,
                sessionCount = container.repository.completedHistory().size,
            )
        }
    }

    fun setWeightUnit(u: WeightUnit) = viewModelScope.launch { container.settings.setWeightUnit(u) }
    fun setLengthUnit(u: LengthUnit) = viewModelScope.launch { container.settings.setLengthUnit(u) }
    fun setTimerSound(v: Boolean) = viewModelScope.launch { container.settings.setTimerSound(v) }
    fun setTimerVibration(v: Boolean) = viewModelScope.launch { container.settings.setTimerVibration(v) }
    fun setAutoTimer(v: Boolean) = viewModelScope.launch { container.settings.setAutoStartTimer(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { container.settings.setKeepScreenOn(v) }

    fun signIn(email: String, password: String, createAccount: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, syncStatus = null)
            val result = if (createAccount) {
                container.syncEngine.signUp(email, password)
            } else {
                container.syncEngine.signIn(email, password)
            }
            _state.value = _state.value.copy(
                busy = false,
                signedInUserId = container.syncEngine.signedInUserId(),
                syncStatus = result.fold(
                    onSuccess = { if (createAccount) "Account created and signed in." else "Signed in." },
                    onFailure = { it.message },
                ),
            )
            if (result.isSuccess) {
                container.settings.setSyncEnabled(true)
                container.settings.setSupabaseEmail(email)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            container.syncEngine.signOut()
            container.settings.setSyncEnabled(false)
            _state.value = _state.value.copy(signedInUserId = null, syncStatus = "Signed out.")
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, syncStatus = "Syncing…")
            val outcome = container.syncEngine.sync()
            _state.value = _state.value.copy(
                busy = false,
                syncStatus = when (outcome) {
                    is SyncEngine.Outcome.Success -> "Backed up ${outcome.pushed} records."
                    is SyncEngine.Outcome.Skipped -> outcome.reason
                    is SyncEngine.Outcome.Failed -> "Sync failed: ${outcome.reason}"
                },
            )
        }
    }

    fun export(context: Context, csv: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching {
                val exporter = DataExporter(context, container.repository)
                val file = if (csv) exporter.exportCsv() else exporter.exportJson()
                context.startActivity(
                    Intent.createChooser(exporter.shareIntent(file), "Export Winter Arc data"),
                )
            }.onFailure {
                _state.value = _state.value.copy(syncStatus = "Export failed: ${it.message}")
            }
            _state.value = _state.value.copy(busy = false)
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            container.repository.deleteAllData()
            container.seeder.seedIfEmpty()
            _state.value = _state.value.copy(syncStatus = "All local data deleted and the programme re-seeded.")
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(container) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    vm: SettingsViewModel,
    onOpenLibrary: () -> Unit,
    onOpenTemplates: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showDelete by remember { mutableStateOf(false) }
    var showSignIn by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = WinterArcColors.NightDeep,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.syncStatus?.let {
                WinterCard(accent = true) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = WinterArcColors.Gold)
                }
            }

            SectionLabel("Programme")
            WinterCard {
                SettingRow("Exercise library", "${state.exerciseCount} exercises", onOpenLibrary)
                Divider(color = WinterArcColors.NightBorder)
                SettingRow("Workout templates", "Edit days, exercises, sets and pairings", onOpenTemplates)
            }

            SectionLabel("Units")
            WinterCard {
                Text("Weight", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeightUnit.entries.forEach { u ->
                        ChoiceChip(u.label.uppercase(), u == state.settings.weightUnit) { vm.setWeightUnit(u) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Measurements", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LengthUnit.entries.forEach { u ->
                        ChoiceChip(u.label.uppercase(), u == state.settings.lengthUnit) { vm.setLengthUnit(u) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Units affect display only. Everything is stored in kilograms and centimetres, " +
                        "so switching never alters your history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WinterArcColors.Faint,
                )
            }

            SectionLabel("Rest timer")
            WinterCard {
                ToggleRow("Start automatically after a set", state.settings.autoStartRestTimer, vm::setAutoTimer)
                ToggleRow("Sound when the timer ends", state.settings.timerSoundEnabled, vm::setTimerSound)
                ToggleRow("Vibrate when the timer ends", state.settings.timerVibrationEnabled, vm::setTimerVibration)
                ToggleRow("Keep the screen on while training", state.settings.keepScreenOnDuringWorkout, vm::setKeepScreenOn)
            }

            SectionLabel("Cloud backup")
            WinterCard {
                if (!state.syncConfigured) {
                    Text(
                        "This build has no Supabase credentials compiled in, so cloud backup is " +
                            "switched off. Everything still works — your data lives on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WinterArcColors.Muted,
                    )
                } else if (state.signedInUserId == null) {
                    Text(
                        "Sign in to back your training history up. This is optional; the app is " +
                            "fully functional offline and never requires an account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WinterArcColors.Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    GoldButton("Sign in / create account", { showSignIn = true }, Modifier.fillMaxWidth())
                } else {
                    Text(
                        state.settings.supabaseEmail ?: "Signed in",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoldButton("Back up now", vm::syncNow, Modifier.weight(1f), enabled = !state.busy)
                        OutlineButton("Sign out", vm::signOut, Modifier.weight(1f))
                    }
                }
            }

            SectionLabel("Your data")
            WinterCard {
                Text(
                    "${state.sessionCount} completed workouts. Export takes a full copy that you " +
                        "can keep independently of this app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WinterArcColors.Muted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton("Export JSON", { vm.export(context, csv = false) }, Modifier.weight(1f))
                    OutlineButton("Export CSV", { vm.export(context, csv = true) }, Modifier.weight(1f))
                }
            }

            SectionLabel("Danger zone")
            WinterCard {
                Text(
                    "Delete every workout, measurement and custom exercise on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WinterArcColors.Muted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WinterArcColors.Danger),
                ) { Text("Delete all data") }
            }

            SectionLabel("About")
            WinterCard {
                Text("Winter Arc", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A personal training log. Volume means weight × reps across working sets. " +
                        "Estimated 1RM uses the Epley formula and is never a tested maximum.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WinterArcColors.Muted,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = WinterArcColors.NightElevated,
            title = { Text("Delete everything?", color = WinterArcColors.White) },
            text = {
                Text(
                    "Every workout, set, measurement and custom exercise on this device will be " +
                        "erased. This cannot be undone. Export your data first if you want to keep it.",
                    color = WinterArcColors.Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDelete = false; vm.deleteAllData() }) {
                    Text("Delete everything", color = WinterArcColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("Cancel", color = WinterArcColors.Muted)
                }
            },
        )
    }

    if (showSignIn) {
        SignInDialog(
            busy = state.busy,
            onDismiss = { showSignIn = false },
            onSubmit = { email, password, create ->
                vm.signIn(email, password, create); showSignIn = false
            },
        )
    }
}

@Composable
private fun SignInDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Boolean) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WinterArcColors.NightElevated,
        title = { Text(if (create) "Create account" else "Sign in", color = WinterArcColors.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = create,
                        onCheckedChange = { create = it },
                        colors = CheckboxDefaults.colors(checkedColor = WinterArcColors.Gold),
                    )
                    Text("I need a new account", color = WinterArcColors.Muted)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(email.trim(), password, create) },
                enabled = !busy && email.isNotBlank() && password.length >= 6,
            ) { Text(if (create) "Create" else "Sign in", color = WinterArcColors.Gold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = WinterArcColors.Muted) }
        },
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WinterArcColors.Muted)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = WinterArcColors.NightDeep,
                checkedTrackColor = WinterArcColors.Gold,
            ),
        )
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) WinterArcColors.NightDeep else WinterArcColors.White,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) WinterArcColors.Gold else WinterArcColors.NightElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}
