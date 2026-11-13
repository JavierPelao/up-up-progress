package com.upupprogress.app.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upupprogress.app.data.model.*
import com.upupprogress.app.data.repository.TrainingProgressGateway
import com.upupprogress.app.data.repository.TrainingRepository
import com.upupprogress.app.domain.training.TrainingProgressCalculator
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StrengthMetric(val label: String) {
    MAXIMUM_LOAD("Carga máxima real"),
    ESTIMATED_ONE_REP_MAX("1RM estimado"),
}

data class StrengthChartPoint(
    val periodStart: LocalDate,
    val metric: StrengthMetric,
    val measurement: TrainingSetMetric<BigDecimal>,
)

fun strengthChartPoints(progress: TrainingProgress): List<StrengthChartPoint> = progress.points.flatMap { point ->
    listOfNotNull(
        point.maximumLoad?.let { StrengthChartPoint(point.periodStart, StrengthMetric.MAXIMUM_LOAD, it) },
        point.estimatedOneRepMax?.let { StrengthChartPoint(point.periodStart, StrengthMetric.ESTIMATED_ONE_REP_MAX, it) },
    )
}

data class RepetitionsChartPoint(
    val periodStart: LocalDate,
    val selectedWeightKg: BigDecimal,
    val measurement: TrainingSetMetric<Int>,
)

fun repetitionsChartPoints(progress: TrainingProgress): List<RepetitionsChartPoint> {
    val weight = progress.selectedWeightKg ?: return emptyList()
    return progress.points.mapNotNull { point ->
        point.repetitionsAtWeight?.let { RepetitionsChartPoint(point.periodStart, weight, it) }
    }
}

data class StrengthProgressUiState(
    val exercises: List<RecordedExercise> = emptyList(),
    val selectedExerciseId: String? = null,
    val period: TrainingPeriod = TrainingPeriod.WEEK,
    val isLoading: Boolean = false,
    val error: String? = null,
    val progress: TrainingProgress? = null,
    val selectedPoint: StrengthChartPoint? = null,
    val selectedWeightKg: BigDecimal? = null,
    val isRepetitionsLoading: Boolean = false,
    val selectedRepetitionsPoint: RepetitionsChartPoint? = null,
)

class StrengthProgressViewModel internal constructor(
    private val repository: TrainingProgressGateway,
    private val todayDate: () -> LocalDate = { LocalDate.now() },
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    constructor() : this(TrainingRepository())

    private val mutableState = MutableStateFlow(StrengthProgressUiState())
    val uiState = mutableState.asStateFlow()
    private var owner: String? = null
    private var requestVersion = 0L
    private var optionsJob: Job? = null
    private var historyJob: Job? = null
    private var calculationJob: Job? = null
    private var cachedExerciseId: String? = null
    private var cachedHistory: List<ExerciseSetRecord> = emptyList()
    private var loadedThrough: LocalDate? = null

    fun activate(userId: String) {
        if (owner == userId) return
        clear()
        owner = userId
    }

    fun clear() {
        optionsJob?.cancel(); historyJob?.cancel(); calculationJob?.cancel()
        requestVersion++
        owner = null
        cachedExerciseId = null
        cachedHistory = emptyList()
        loadedThrough = null
        mutableState.value = StrengthProgressUiState()
    }

    fun refresh() {
        val user = owner ?: return
        optionsJob?.cancel(); historyJob?.cancel(); calculationJob?.cancel()
        val version = ++requestVersion
        val priorSelection = mutableState.value.selectedExerciseId
        val through = todayDate()
        cachedExerciseId = null
        cachedHistory = emptyList()
        mutableState.value = mutableState.value.copy(
            exercises = emptyList(), selectedExerciseId = null, progress = null,
            selectedPoint = null, selectedRepetitionsPoint = null,
            isLoading = true, isRepetitionsLoading = false, error = null,
        )
        optionsJob = viewModelScope.launch {
            try {
                val exercises = withTimeout(30_000) { repository.loadRecordedExercises(through) }
                ensureOwner(user)
                if (version != requestVersion) return@launch
                val selected = exercises.firstOrNull { it.id == priorSelection } ?: exercises.firstOrNull()
                mutableState.value = mutableState.value.copy(
                    exercises = exercises, selectedExerciseId = selected?.id, isLoading = selected != null,
                    selectedWeightKg = if (selected == null) null else mutableState.value.selectedWeightKg,
                )
                loadedThrough = through
                if (selected != null) loadSelectedHistory()
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) fail(user, version) else throw error
            } catch (_: Exception) { fail(user, version) }
        }
    }

    fun selectExercise(id: String) {
        val state = mutableState.value
        if (state.exercises.none { it.id == id } || state.selectedExerciseId == id) return
        mutableState.value = state.copy(selectedExerciseId = id, selectedPoint = null,
            selectedRepetitionsPoint = null, progress = null, error = null)
        loadSelectedHistory()
    }

    fun selectPeriod(period: TrainingPeriod) {
        if (mutableState.value.period == period) return
        mutableState.value = mutableState.value.copy(period = period, selectedPoint = null,
            selectedRepetitionsPoint = null, progress = null, isRepetitionsLoading = false)
        if (cachedExerciseId == mutableState.value.selectedExerciseId && cachedExerciseId != null) recalculate()
    }

    fun selectWeight(weightKg: BigDecimal) {
        val state = mutableState.value
        if (state.isLoading) return
        val progress = state.progress ?: return
        val weight = progress.availableWeightsKg.firstOrNull { it.compareTo(weightKg) == 0 } ?: return
        if (state.selectedWeightKg?.compareTo(weight) == 0) return
        val user = owner ?: return
        val version = requestVersion
        calculationJob?.cancel()
        mutableState.value = state.copy(selectedWeightKg = weight, selectedPoint = null,
            selectedRepetitionsPoint = null, isRepetitionsLoading = true)
        calculationJob = viewModelScope.launch {
            try {
                val result = withContext(calculationDispatcher) {
                    TrainingProgressCalculator.withSelectedWeight(progress, weight)
                }
                ensureOwner(user)
                if (version != requestVersion || mutableState.value.selectedWeightKg?.compareTo(weight) != 0) return@launch
                mutableState.value = mutableState.value.copy(progress = result, isRepetitionsLoading = false)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { fail(user, version) }
        }
    }

    fun selectRepetitionsPoint(point: RepetitionsChartPoint) {
        val state = mutableState.value
        if (state.isRepetitionsLoading || state.isLoading) return
        val progress = state.progress ?: return
        val current = repetitionsChartPoints(progress).firstOrNull { it == point } ?: return
        mutableState.value = state.copy(selectedPoint = null, selectedRepetitionsPoint = current)
    }

    fun dismissRepetitionsPoint() { mutableState.value = mutableState.value.copy(selectedRepetitionsPoint = null) }

    fun selectPoint(point: StrengthChartPoint) {
        val progress = mutableState.value.progress ?: return
        val current = strengthChartPoints(progress).firstOrNull { it == point } ?: return
        mutableState.value = mutableState.value.copy(selectedPoint = current, selectedRepetitionsPoint = null)
    }

    fun dismissPoint() { mutableState.value = mutableState.value.copy(selectedPoint = null) }

    fun onClockChanged() {
        if (owner != null && loadedThrough != null && loadedThrough != todayDate()) refresh()
    }

    private fun loadSelectedHistory() {
        val user = owner ?: return
        val selected = mutableState.value.exercises.firstOrNull { it.id == mutableState.value.selectedExerciseId } ?: return
        historyJob?.cancel(); calculationJob?.cancel()
        val version = ++requestVersion
        val through = loadedThrough ?: todayDate()
        cachedExerciseId = null
        cachedHistory = emptyList()
        mutableState.value = mutableState.value.copy(isLoading = true, isRepetitionsLoading = false,
            progress = null, selectedPoint = null, selectedRepetitionsPoint = null, error = null)
        historyJob = viewModelScope.launch {
            try {
                val records = withTimeout(45_000) {
                    repository.loadExerciseHistory(selected.id, selected.firstPerformedOn, through)
                }
                ensureOwner(user)
                if (version != requestVersion || mutableState.value.selectedExerciseId != selected.id) return@launch
                cachedExerciseId = selected.id
                cachedHistory = records
                loadedThrough = through
                recalculate()
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) fail(user, version) else throw error
            } catch (_: Exception) { fail(user, version) }
        }
    }

    private fun recalculate() {
        val user = owner ?: return
        val state = mutableState.value
        val selected = state.exercises.firstOrNull { it.id == state.selectedExerciseId } ?: return
        val through = loadedThrough ?: return
        val records = cachedHistory
        val version = requestVersion
        calculationJob?.cancel()
        mutableState.value = state.copy(isLoading = true, isRepetitionsLoading = false, error = null)
        calculationJob = viewModelScope.launch {
            try {
                val result = withContext(calculationDispatcher) {
                    val base = TrainingProgressCalculator.calculate(
                        records, user, selected.id, selected.firstPerformedOn, through, state.period,
                    )
                    val weight = base.availableWeightsKg.firstOrNull { state.selectedWeightKg?.compareTo(it) == 0 }
                        ?: base.availableWeightsKg.firstOrNull()
                    TrainingProgressCalculator.withSelectedWeight(base, weight)
                }
                ensureOwner(user)
                if (version != requestVersion || mutableState.value.period != state.period) return@launch
                mutableState.value = mutableState.value.copy(isLoading = false, isRepetitionsLoading = false,
                    progress = result, selectedWeightKg = result.selectedWeightKg)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { fail(user, version) }
        }
    }

    private suspend fun ensureOwner(user: String) {
        currentCoroutineContext().ensureActive()
        if (owner != user || repository.currentUserId() != user) throw CancellationException("La sesión cambió")
    }

    private fun fail(user: String, version: Long) {
        if (owner == user && requestVersion == version) {
            mutableState.value = mutableState.value.copy(
                isLoading = false, isRepetitionsLoading = false, progress = null, selectedPoint = null,
                selectedRepetitionsPoint = null, selectedWeightKg = null,
                error = "No se pudo cargar tu progreso. Revisa la conexión y vuelve a intentarlo.",
            )
        }
    }
}
