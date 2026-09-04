package com.winterarc.app.ui.train

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.kit.GhostButton
import com.winterarc.app.ui.kit.GoldButton
import com.winterarc.app.ui.kit.Label
import com.winterarc.app.ui.kit.ProgressRing
import com.winterarc.app.ui.theme.W
import com.winterarc.core.Fmt
import kotlinx.coroutines.delay

/**
 * Rest between sets.
 *
 * Hoisted above the screen it is used on so that leaving the exercise, or opening a sheet, does
 * not silently reset the clock the user is relying on.
 */
class RestTimerController {
    var total by mutableIntStateOf(0)
        private set
    var remaining by mutableIntStateOf(0)
        private set
    var running by mutableStateOf(false)
        private set
    var visible by mutableStateOf(false)
        private set
    var expanded by mutableStateOf(false)

    /** The movement the rest belongs to, so the bar can say what is coming next. */
    var caption by mutableStateOf("")

    fun start(seconds: Int, label: String = "") {
        if (seconds <= 0) return
        total = seconds
        remaining = seconds
        caption = label
        running = true
        visible = true
        expanded = false
    }

    fun toggle() { running = !running }

    fun adjust(seconds: Int) {
        remaining = (remaining + seconds).coerceAtLeast(0)
        if (remaining > total) total = remaining
        if (remaining > 0) running = true
    }

    fun dismiss() {
        running = false
        visible = false
        expanded = false
        remaining = 0
    }

    fun tick() {
        if (!running) return
        remaining -= 1
        if (remaining <= 0) {
            remaining = 0
            running = false
        }
    }

    val progress: Float get() = if (total <= 0) 0f else remaining.toFloat() / total.toFloat()
    val finished: Boolean get() = visible && remaining == 0
}

/** Drives the countdown. One second at a time, only while something is actually running. */
@Composable
fun RestTimerTicker(controller: RestTimerController) {
    LaunchedEffect(controller.running) {
        while (controller.running) {
            delay(1000)
            controller.tick()
        }
    }
}

/**
 * The collapsed bar.
 *
 * It sits above the finish button rather than over the sets, because the next thing the user
 * needs to see while resting is the set they are about to do.
 */
@Composable
fun RestBar(controller: RestTimerController, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = controller.visible && !controller.expanded,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val done = controller.finished
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        if (done) listOf(W.Good.copy(alpha = 0.30f), W.NightHi)
                        else listOf(W.GoldFilm, W.NightHi),
                    ),
                )
                .border(1.dp, if (done) W.Good.copy(alpha = 0.5f) else W.GoldEdge, RoundedCornerShape(18.dp))
                .clickable { controller.expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(
                progress = controller.progress,
                modifier = Modifier.size(40.dp),
                color = if (done) W.Good else W.Gold,
                strokeWidth = 3.dp,
                animate = false,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (done) "Rest complete" else Fmt.clock(controller.remaining),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (done) W.Good else W.GoldBright,
                )
                if (controller.caption.isNotBlank()) {
                    Text(
                        controller.caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = W.Faint,
                    )
                }
            }
            if (!done) {
                Text(
                    "+30s",
                    style = MaterialTheme.typography.titleSmall,
                    color = W.Muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { controller.adjust(30) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Icon(
                Icons.Filled.Close,
                "Dismiss rest timer",
                tint = W.Faint,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { controller.dismiss() }
                    .padding(7.dp),
            )
        }
    }
}

/**
 * The full-screen countdown.
 *
 * Deliberately enormous. It is read across a gym floor, from a bench, at a glance, and there is
 * nothing else that matters on the screen while it is running.
 */
@Composable
fun RestOverlay(controller: RestTimerController) {
    AnimatedVisibility(
        visible = controller.visible && controller.expanded,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val done = controller.finished
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(W.Night, W.Void, W.Blue)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Label(if (done) "Go" else "Rest", color = if (done) W.Good else W.Faint)
                Spacer(Modifier.height(26.dp))

                ProgressRing(
                    progress = controller.progress,
                    modifier = Modifier.size(268.dp),
                    color = if (done) W.Good else W.Gold,
                    strokeWidth = 12.dp,
                    animate = false,
                ) {
                    Text(
                        Fmt.clock(controller.remaining),
                        style = MaterialTheme.typography.displayLarge,
                        color = if (done) W.Good else W.GoldBright,
                    )
                }

                if (controller.caption.isNotBlank()) {
                    Spacer(Modifier.height(22.dp))
                    Text(
                        controller.caption,
                        style = MaterialTheme.typography.titleMedium,
                        color = W.Muted,
                    )
                }

                Spacer(Modifier.height(34.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    GhostButton("−15s", { controller.adjust(-15) }, Modifier.weight(1f))
                    GhostButton(
                        text = if (controller.running) "Pause" else "Resume",
                        onClick = { controller.toggle() },
                        modifier = Modifier.weight(1f),
                        icon = if (controller.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    )
                    GhostButton("+15s", { controller.adjust(15) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(11.dp))
                GoldButton(
                    text = if (done) "BACK TO THE SET" else "SKIP REST",
                    onClick = { controller.dismiss() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    "Collapse",
                    style = MaterialTheme.typography.titleSmall,
                    color = W.Faint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { controller.expanded = false }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
