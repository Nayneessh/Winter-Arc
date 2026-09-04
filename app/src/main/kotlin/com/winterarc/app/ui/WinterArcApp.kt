package com.winterarc.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.winterarc.app.AppContainer
import com.winterarc.app.ui.body.BodyScreen
import com.winterarc.app.ui.body.BodyViewModel
import com.winterarc.app.ui.dashboard.DashboardScreen
import com.winterarc.app.ui.dashboard.DashboardViewModel
import com.winterarc.app.ui.history.ExerciseHistoryScreen
import com.winterarc.app.ui.history.HistoryScreen
import com.winterarc.app.ui.history.HistoryViewModel
import com.winterarc.app.ui.history.SessionDetailScreen
import com.winterarc.app.ui.home.HomeScreen
import com.winterarc.app.ui.home.HomeViewModel
import com.winterarc.app.ui.library.ExerciseLibraryScreen
import com.winterarc.app.ui.library.ProgrammeViewModel
import com.winterarc.app.ui.library.TemplateEditorScreen
import com.winterarc.app.ui.library.TemplateListScreen
import com.winterarc.app.ui.settings.SettingsScreen
import com.winterarc.app.ui.settings.SettingsViewModel
import com.winterarc.app.ui.workout.WorkoutCompleteScreen
import com.winterarc.app.ui.workout.WorkoutScreen
import com.winterarc.app.ui.workout.WorkoutViewModel

private object Routes {
    const val HOME = "home"
    const val WORKOUT = "workout"
    const val HISTORY = "history"
    const val DASHBOARD = "dashboard"
    const val BODY = "body"
    const val SETTINGS = "settings"
    const val LIBRARY = "library"
    const val TEMPLATES = "templates"
}

/**
 * Navigation.
 *
 * Uses the platform back stack throughout, so the Android system back gesture and button
 * behave exactly as the user expects on every screen. The one place back is intercepted is
 * the active workout, where leaving accidentally would be costly — there it navigates home
 * while leaving the session in progress, rather than discarding anything.
 */
@Composable
fun WinterArcApp(container: AppContainer) {
    val nav = rememberNavController()
    val view = LocalView.current

    val settingsFlow by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = com.winterarc.app.data.prefs.UserSettings(),
    )

    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container))
            val state by vm.state.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) { vm.refresh() }

            HomeScreen(
                state = state,
                onStartWorkout = { nav.navigate("${Routes.WORKOUT}?template=${state.todayTemplate?.id ?: ""}") },
                onResumeWorkout = { nav.navigate("${Routes.WORKOUT}?template=") },
                onTrainSomethingElse = { nav.navigate("${Routes.WORKOUT}?template=&custom=true") },
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                onOpenDashboard = { nav.navigate(Routes.DASHBOARD) },
                onOpenBody = { nav.navigate(Routes.BODY) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = "${Routes.WORKOUT}?template={template}&custom={custom}",
            arguments = listOf(
                navArgument("template") { type = NavType.StringType; defaultValue = "" },
                navArgument("custom") { type = NavType.StringType; defaultValue = "false" },
            ),
        ) { entry ->
            val templateId = entry.arguments?.getString("template")?.takeIf { it.isNotBlank() }
            val custom = entry.arguments?.getString("custom") == "true"

            val vm: WorkoutViewModel = viewModel(factory = WorkoutViewModel.Factory(container))
            val state by vm.state.collectAsStateWithLifecycle()
            val timer by vm.timer.collectAsStateWithLifecycle()

            LaunchedEffect(templateId, custom) {
                if (custom) vm.startCustom("Custom workout") else vm.loadOrStart(templateId)
            }

            // Holds the screen awake while a workout is open, if the user wants that.
            DisposableEffect(settingsFlow.keepScreenOnDuringWorkout) {
                view.keepScreenOn = settingsFlow.keepScreenOnDuringWorkout
                onDispose { view.keepScreenOn = false }
            }

            // Back leaves the workout running rather than discarding it.
            BackHandler(enabled = !state.finished) { nav.popBackStack() }

            if (state.finished && state.session != null) {
                WorkoutCompleteScreen(
                    session = state.session!!,
                    records = state.newRecords,
                    names = state.exerciseNames,
                    onDone = { nav.popBackStack(Routes.HOME, inclusive = false) },
                )
            } else {
                WorkoutScreen(
                    state = state,
                    timer = timer,
                    soundEnabled = settingsFlow.timerSoundEnabled,
                    vibrationEnabled = settingsFlow.timerVibrationEnabled,
                    onLogSet = { id, w, r, warm -> vm.logSet(id, w, r, warm) },
                    onUpdateSet = { id, w, r -> vm.updateSet(id, w, r) },
                    onRemoveSet = vm::removeSet,
                    onSelectExercise = vm::selectExercise,
                    onReplaceExercise = vm::replaceExercise,
                    onAddExercise = { id, s, lo, hi, rest -> vm.addExercise(id, s, lo, hi, rest) },
                    onCreateExercise = { n, m, e, rest, notes -> vm.createAndAddExercise(n, m, e, rest, notes) },
                    onRemoveExercise = vm::removeExercise,
                    onSkipExercise = { id, skip -> vm.skipExercise(id, skip) },
                    onMoveExercise = vm::moveExercise,
                    onAdjustPlan = { id, s, lo, hi, w, rest -> vm.adjustPlan(id, s, lo, hi, w, rest) },
                    onStartTimer = vm::startTimer,
                    onPauseTimer = vm::pauseTimer,
                    onResumeTimer = vm::resumeTimer,
                    onSkipTimer = vm::skipTimer,
                    onAdjustTimer = vm::adjustTimer,
                    onTimerFinishedAck = vm::acknowledgeTimerFinished,
                    onFinish = { vm.finishWorkout() },
                    onAbandon = vm::abandonWorkout,
                    onBack = { nav.popBackStack() },
                )
            }
        }

        composable(Routes.HISTORY) {
            val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(container))
            val state by vm.state.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) { vm.refresh() }

            when {
                state.exerciseHistory != null -> {
                    BackHandler { vm.closeExercise() }
                    ExerciseHistoryScreen(
                        name = state.exerciseName,
                        history = state.exerciseHistory!!,
                        progression = state.exerciseProgression,
                        onBack = { vm.closeExercise() },
                    )
                }
                state.selected != null -> {
                    BackHandler { vm.closeSession() }
                    SessionDetailScreen(
                        session = state.selected!!,
                        names = state.names,
                        onOpenExercise = vm::openExercise,
                        onBack = { vm.closeSession() },
                    )
                }
                else -> HistoryScreen(
                    state = state,
                    onOpen = vm::open,
                    onBack = { nav.popBackStack() },
                )
            }
        }

        composable(Routes.DASHBOARD) {
            val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(container))
            val state by vm.state.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) { vm.refresh() }

            DashboardScreen(
                state = state,
                onRangeChange = vm::setRange,
                onTrackExercise = vm::trackExercise,
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.BODY) {
            val vm: BodyViewModel = viewModel(factory = BodyViewModel.Factory(container))
            val state by vm.state.collectAsStateWithLifecycle()

            BodyScreen(
                state = state,
                onSave = vm::save,
                onDelete = vm::delete,
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
            val state by vm.state.collectAsStateWithLifecycle()

            SettingsScreen(
                state = state,
                vm = vm,
                onOpenLibrary = { nav.navigate(Routes.LIBRARY) },
                onOpenTemplates = { nav.navigate(Routes.TEMPLATES) },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.LIBRARY) {
            val vm: ProgrammeViewModel = viewModel(factory = ProgrammeViewModel.Factory(container))
            val state by vm.state.collectAsStateWithLifecycle()

            ExerciseLibraryScreen(
                state = state,
                onSave = vm::saveExercise,
                onArchive = vm::archiveExercise,
                onBack = { nav.popBackStack() },
                newId = { container.ids.next() },
            )
        }

        composable(Routes.TEMPLATES) {
            val vm: ProgrammeViewModel = viewModel(factory = ProgrammeViewModel.Factory(container))
            val state by vm.state.collectAsStateWithLifecycle()

            if (state.editing != null) {
                BackHandler { vm.closeEditor() }
                TemplateEditorScreen(
                    template = state.editing!!,
                    names = state.names,
                    library = state.exercises,
                    onSave = vm::saveTemplate,
                    onBack = { vm.closeEditor() },
                    newId = { container.ids.next() },
                )
            } else {
                TemplateListScreen(
                    state = state,
                    onEdit = vm::edit,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
