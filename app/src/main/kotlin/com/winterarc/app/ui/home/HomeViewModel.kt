package com.winterarc.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.winterarc.app.AppContainer
import com.winterarc.domain.analytics.BodyAnalytics
import com.winterarc.domain.analytics.Consistency
import com.winterarc.domain.analytics.PrDetector
import com.winterarc.domain.model.MetricField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val template = repo.templateForDate(today)
            val names = repo.allExercises().associate { it.id to it.name }
            val history = repo.completedHistory()
            val body = repo.allBodyMetrics()

            // The most recent record across all history, for the home summary.
            val latest = history
                .sortedBy { it.date }
                .flatMap { s -> PrDetector.detectAll(s, history.filter { it.date < s.date }) }
                .maxByOrNull { it.date }

            val weightDelta = BodyAnalytics.delta(MetricField.WEIGHT, body)

            _state.value = HomeUiState(
                today = today,
                todayTemplate = template,
                exerciseNames = names,
                hasSessionInProgress = repo.inProgressSessionId() != null,
                weekStreak = Consistency.currentWeekStreak(history, today),
                sessionsThisWeek = Consistency.sessionsInWeekOf(history, today),
                totalWorkouts = history.size,
                latestPr = latest,
                latestPrExercise = latest?.let { names[it.exerciseId] },
                currentWeightKg = BodyAnalytics.latest(MetricField.WEIGHT, body),
                weightChangeKg = weightDelta?.change,
                loading = false,
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
    }
}
