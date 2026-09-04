package com.winterarc.app.ui.workout

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import com.winterarc.app.ui.components.GoldButton
import com.winterarc.app.ui.theme.WinterArcColors

/**
 * The rest countdown.
 *
 * When rest begins the entire screen becomes night green and the remaining time is shown in
 * gold at display size — readable at arm's length, across a gym, without picking the phone up.
 * The ring drains as the rest period elapses so the state is legible even before the digits
 * are read.
 *
 * Runs entirely on-device. No part of it touches the network.
 */
@Composable
fun RestTimerOverlay(
    state: TimerState,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onAdjust: (Int) -> Unit,
    onFinishedAcknowledged: () -> Unit,
) {
    val context = LocalContext.current

    // Fires once when the countdown reaches zero.
    LaunchedEffect(state.justFinished) {
        if (state.justFinished) {
            if (vibrationEnabled) vibrateComplete(context)
            if (soundEnabled) playCompletionTone()
            onFinishedAcknowledged()
        }
    }

    val progress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(durationMillis = 250),
        label = "restProgress",
    )

    val isFinalCountdown = state.remainingSeconds in 1..5

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(WinterArcColors.NightTimer, WinterArcColors.NightDeep),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        ) {
            Text(
                text = "REST",
                style = MaterialTheme.typography.labelMedium,
                color = WinterArcColors.Muted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.exerciseName.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = WinterArcColors.White,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(36.dp))

            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(292.dp)) {
                    val stroke = 14.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = WinterArcColors.NightBorder,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = if (isFinalCountdown) WinterArcColors.GoldBright else WinterArcColors.Gold,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.remainingSeconds <= 0) "DONE" else state.display,
                        style = MaterialTheme.typography.displayLarge,
                        color = if (isFinalCountdown) WinterArcColors.GoldBright else WinterArcColors.Gold,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.isPaused) {
                        Text(
                            text = "PAUSED",
                            style = MaterialTheme.typography.labelMedium,
                            color = WinterArcColors.Warning,
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TimerChip("−15s", Modifier.weight(1f)) { onAdjust(-15) }
                TimerChip(
                    text = if (state.isPaused) "Resume" else "Pause",
                    modifier = Modifier.weight(1f),
                ) { if (state.isPaused) onResume() else onPause() }
                TimerChip("+15s", Modifier.weight(1f)) { onAdjust(15) }
            }

            Spacer(Modifier.height(14.dp))

            GoldButton(
                text = "SKIP REST",
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TimerChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(WinterArcColors.NightElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = WinterArcColors.White,
        )
    }
}

/** Two short pulses — distinguishable from a notification without looking at the phone. */
private fun vibrateComplete(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 220, 130, 220)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}

/**
 * A short tone through the notification stream, so it is audible over music but still
 * respects the device's silent mode.
 */
private fun playCompletionTone() {
    runCatching {
        val tone = ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 85)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 600)
    }
}
