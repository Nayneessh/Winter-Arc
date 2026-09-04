package com.winterarc.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.winterarc.app.Repository
import com.winterarc.app.ui.body.BodyScreen
import com.winterarc.app.ui.dash.DashboardScreen
import com.winterarc.app.ui.dash.ExerciseDetailScreen
import com.winterarc.app.ui.history.HistoryScreen
import com.winterarc.app.ui.history.SessionDetailScreen
import com.winterarc.app.ui.kit.WinterBackground
import com.winterarc.app.ui.plan.ExerciseLibraryScreen
import com.winterarc.app.ui.plan.PlanScreen
import com.winterarc.app.ui.plan.RoutineEditorScreen
import com.winterarc.app.ui.settings.SettingsScreen
import com.winterarc.app.ui.theme.W
import com.winterarc.app.ui.today.TodayScreen
import com.winterarc.app.ui.train.RestTimerController
import com.winterarc.app.ui.train.TrainScreen

/** The four places the app has. Anything else is pushed on top and dismissed with back. */
enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Bolt),
    PROGRESS("Progress", Icons.Filled.Insights),
    HISTORY("History", Icons.Filled.History),
    BODY("Body", Icons.Filled.MonitorWeight),
}

/** A screen stacked above the tabs. */
sealed interface Overlay {
    data object Train : Overlay
    data object Plan : Overlay
    data object Library : Overlay
    data object Settings : Overlay
    data class Routine(val routineId: String) : Overlay
    data class Exercise(val exerciseId: String) : Overlay
    data class SessionDetail(val sessionId: String) : Overlay
}

@Composable
fun RootScreen(
    repository: Repository,
    onKeepScreenOn: (Boolean) -> Unit,
) {
    val data by repository.data.collectAsStateWithLifecycle()
    val ready by repository.ready.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(Tab.TODAY) }
    val stack = remember { mutableStateListOf<Overlay>() }
    val restTimer = remember { RestTimerController() }

    fun push(overlay: Overlay) {
        if (stack.lastOrNull() != overlay) stack.add(overlay)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    // The screen is held awake only while a workout is actually in progress -- never for the
    // dashboard, and never longer than the session it belongs to.
    val training = stack.lastOrNull() == Overlay.Train && data.activeSession != null
    androidx.compose.runtime.LaunchedEffect(training) { onKeepScreenOn(training) }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    WinterBackground {
        if (!ready) {
            SplashPanel()
            return@WinterBackground
        }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            (fadeIn(tween(220)) togetherWith fadeOut(tween(140)))
                        },
                        label = "tab",
                    ) { current ->
                        when (current) {
                            Tab.TODAY -> TodayScreen(
                                data = data,
                                onUpdate = repository::update,
                                onTrain = { push(Overlay.Train) },
                                onOpenPlan = { push(Overlay.Plan) },
                                onOpenSettings = { push(Overlay.Settings) },
                                onOpenLibrary = { push(Overlay.Library) },
                            )

                            Tab.PROGRESS -> DashboardScreen(
                                data = data,
                                onOpenExercise = { push(Overlay.Exercise(it)) },
                            )

                            Tab.HISTORY -> HistoryScreen(
                                data = data,
                                onOpenSession = { push(Overlay.SessionDetail(it)) },
                                onOpenExercise = { push(Overlay.Exercise(it)) },
                            )

                            Tab.BODY -> BodyScreen(data = data, onUpdate = repository::update)
                        }
                    }
                }

                TabBar(selected = tab, onSelect = { tab = it })
            }

            // Overlays slide up over the tabs, which is what makes starting a workout feel like
            // entering somewhere rather than swapping a page.
            AnimatedContent(
                targetState = stack.lastOrNull(),
                transitionSpec = {
                    if (targetState != null) {
                        slideInVertically(tween(260)) { it / 6 } + fadeIn(tween(200)) togetherWith
                            fadeOut(tween(160))
                    } else {
                        fadeIn(tween(160)) togetherWith
                            slideOutVertically(tween(240)) { it / 6 } + fadeOut(tween(200))
                    }
                },
                label = "overlay",
            ) { overlay ->
                when (overlay) {
                    null -> Box(Modifier)

                    Overlay.Train -> TrainScreen(
                        data = data,
                        restTimer = restTimer,
                        onUpdate = repository::update,
                        onClose = { pop() },
                        onOpenLibrary = { push(Overlay.Library) },
                    )

                    Overlay.Plan -> PlanScreen(
                        data = data,
                        onUpdate = repository::update,
                        onBack = { pop() },
                        onOpenRoutine = { push(Overlay.Routine(it)) },
                        onOpenLibrary = { push(Overlay.Library) },
                    )

                    Overlay.Library -> ExerciseLibraryScreen(
                        data = data,
                        onUpdate = repository::update,
                        onBack = { pop() },
                        onOpenExercise = { push(Overlay.Exercise(it)) },
                    )

                    Overlay.Settings -> SettingsScreen(
                        data = data,
                        repository = repository,
                        onBack = { pop() },
                    )

                    is Overlay.Routine -> RoutineEditorScreen(
                        data = data,
                        routineId = overlay.routineId,
                        onUpdate = repository::update,
                        onBack = { pop() },
                    )

                    is Overlay.Exercise -> ExerciseDetailScreen(
                        data = data,
                        exerciseId = overlay.exerciseId,
                        onBack = { pop() },
                    )

                    is Overlay.SessionDetail -> SessionDetailScreen(
                        data = data,
                        sessionId = overlay.sessionId,
                        onUpdate = repository::update,
                        onBack = { pop() },
                    )
                }
            }
        }
    }
}

/**
 * The tab bar.
 *
 * A floating pill rather than a full-width Material bar: it keeps the gradient ground visible
 * underneath, which is what stops the app looking like a stack of unrelated pages.
 */
@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(W.NightHi.copy(alpha = 0.96f), W.Night.copy(alpha = 0.99f)),
                    ),
                )
                .border(1.dp, W.Line, RoundedCornerShape(22.dp))
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { entry ->
                val active = entry == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(entry) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        entry.icon,
                        entry.label,
                        tint = if (active) W.Gold else W.Faint,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) W.GoldBright else W.Faint,
                    )
                    Spacer(Modifier.height(5.dp))
                    // The indicator is a short gold rule under the active tab -- the same mark
                    // used by every section heading, so the language stays consistent.
                    Box(
                        Modifier
                            .width(if (active) 16.dp else 0.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(if (active) W.Gold else Color.Transparent),
                    )
                }
            }
        }
    }
}

/** Shown only for the moment it takes to read the file. Never a spinner on a white field. */
@Composable
private fun SplashPanel() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "WINTER ARC",
                style = MaterialTheme.typography.headlineMedium,
                color = W.Gold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Loading your training",
                style = MaterialTheme.typography.bodySmall,
                color = W.Faint,
            )
        }
    }
}
