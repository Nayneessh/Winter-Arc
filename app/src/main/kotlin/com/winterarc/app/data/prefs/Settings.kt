package com.winterarc.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.winterarc.domain.model.LengthUnit
import com.winterarc.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "winter_arc_settings")

data class UserSettings(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val lengthUnit: LengthUnit = LengthUnit.IN,
    val heightCm: Double = 170.18,
    val timerSoundEnabled: Boolean = true,
    val timerVibrationEnabled: Boolean = true,
    val autoStartRestTimer: Boolean = true,
    val keepScreenOnDuringWorkout: Boolean = true,
    val syncEnabled: Boolean = false,
    val supabaseEmail: String? = null,
)

/**
 * Display preferences only.
 *
 * Changing a unit here never rewrites stored data: weights stay in kilograms and lengths in
 * centimetres on disk, and conversion happens at the edge. That is what lets the user switch
 * units mid-block without corrupting a single historical figure.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val LENGTH_UNIT = stringPreferencesKey("length_unit")
        val HEIGHT_CM = doublePreferencesKey("height_cm")
        val TIMER_SOUND = booleanPreferencesKey("timer_sound")
        val TIMER_VIBRATION = booleanPreferencesKey("timer_vibration")
        val AUTO_START_TIMER = booleanPreferencesKey("auto_start_timer")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val SUPABASE_EMAIL = stringPreferencesKey("supabase_email")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            weightUnit = runCatching { WeightUnit.valueOf(p[Keys.WEIGHT_UNIT] ?: "KG") }
                .getOrDefault(WeightUnit.KG),
            lengthUnit = runCatching { LengthUnit.valueOf(p[Keys.LENGTH_UNIT] ?: "IN") }
                .getOrDefault(LengthUnit.IN),
            heightCm = p[Keys.HEIGHT_CM] ?: 170.18,
            timerSoundEnabled = p[Keys.TIMER_SOUND] ?: true,
            timerVibrationEnabled = p[Keys.TIMER_VIBRATION] ?: true,
            autoStartRestTimer = p[Keys.AUTO_START_TIMER] ?: true,
            keepScreenOnDuringWorkout = p[Keys.KEEP_SCREEN_ON] ?: true,
            syncEnabled = p[Keys.SYNC_ENABLED] ?: false,
            supabaseEmail = p[Keys.SUPABASE_EMAIL],
        )
    }

    suspend fun setWeightUnit(u: WeightUnit) = context.dataStore.edit { it[Keys.WEIGHT_UNIT] = u.name }
    suspend fun setLengthUnit(u: LengthUnit) = context.dataStore.edit { it[Keys.LENGTH_UNIT] = u.name }
    suspend fun setHeightCm(v: Double) = context.dataStore.edit { it[Keys.HEIGHT_CM] = v }
    suspend fun setTimerSound(v: Boolean) = context.dataStore.edit { it[Keys.TIMER_SOUND] = v }
    suspend fun setTimerVibration(v: Boolean) = context.dataStore.edit { it[Keys.TIMER_VIBRATION] = v }
    suspend fun setAutoStartTimer(v: Boolean) = context.dataStore.edit { it[Keys.AUTO_START_TIMER] = v }
    suspend fun setKeepScreenOn(v: Boolean) = context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = v }
    suspend fun setSyncEnabled(v: Boolean) = context.dataStore.edit { it[Keys.SYNC_ENABLED] = v }
    suspend fun setSupabaseEmail(v: String?) = context.dataStore.edit { p ->
        if (v == null) p.remove(Keys.SUPABASE_EMAIL) else p[Keys.SUPABASE_EMAIL] = v
    }
}
