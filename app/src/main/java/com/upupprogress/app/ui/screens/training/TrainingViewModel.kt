package com.upupprogress.app.ui.screens.training

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upupprogress.app.data.model.*
import com.upupprogress.app.data.repository.TrainingGateway
import com.upupprogress.app.data.repository.TrainingRepository
import io.github.jan.supabase.exceptions.RestException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class TrainingPage { LIBRARY, CREATE, ROUTINE_DETAILS, WORKOUT }

@Serializable
data class TrainingSeriesInput(
    val weight: String = "", val reps: String = "", val dirty: Boolean = false,
    val saving: Boolean = false, val error: String? = null, val uncertain: Boolean = false,
)

@Serializable
data class RoutineSelectionRequest(
    val requestId: String, val routineId: String, val date: String, val replaceId: String?,
)

data class TrainingUiState(
    val page: TrainingPage = TrainingPage.LIBRARY,
    val routines: List<RoutinePlan> = emptyList(),
    val catalog: List<ExerciseCatalogItem> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList(),
    val todaySessions: List<WorkoutSession> = emptyList(),
    val activeSessionId: String? = null,
    val viewedRoutineId: String? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val draft: RoutineDraft = RoutineDraft(),
    val inputs: Map<String, TrainingSeriesInput> = emptyMap(),
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val draftLocked: Boolean = false,
    val error: String? = null,
    val changingRoutine: Boolean = false,
    val confirmRoutineId: String? = null,
    val selectionRequest: RoutineSelectionRequest? = null,
    val confirmDiscard: Boolean = false,
    val isManagingRoutines: Boolean = false,
    val editingRoutineId: String? = null,
    val confirmArchiveId: String? = null,
    val pendingArchiveId: String? = null,
) {
    val activeSession: WorkoutSession? get() = sessions.firstOrNull { it.id == activeSessionId }
    val viewedRoutine: RoutinePlan? get() = routines.firstOrNull { it.id == viewedRoutineId }
    val pendingSeries: Boolean get() = inputs.values.any { it.dirty || it.saving }
    val canChooseRoutine: Boolean get() = selectedDate == today && !isBusy && !isLoading &&
        !pendingSeries && selectionRequest == null && pendingArchiveId == null && !isManagingRoutines && error == null
    val canManageRoutines: Boolean get() = selectedDate == today && !isBusy && !isLoading &&
        !pendingSeries && selectionRequest == null && pendingArchiveId == null
}

class TrainingViewModel internal constructor(
    private val saved: SavedStateHandle,
    private val repository: TrainingGateway,
    private val todayDate: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    constructor(savedStateHandle: SavedStateHandle) : this(savedStateHandle, TrainingRepository())

    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow(TrainingUiState(selectedDate = todayDate(), today = todayDate()))
    val uiState = mutableState.asStateFlow()
    private var owner: String? = null
    private var loadJob: Job? = null
    private var operationJob: Job? = null
    private val seriesJobs = mutableMapOf<String, Job>()

    private inline fun <reified T> restored(key: String): T? =
        saved.get<String>(key)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    fun activate(userId: String) {
        if (owner == userId) return
        cancelJobs()
        val sameOwner = saved.get<String>("trainingOwner") == userId
        if (!sameOwner) clearSaved()
        owner = userId
        saved["trainingOwner"] = userId
        val date = if (sameOwner) saved.get<String>("trainingDate")?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()?.takeIf { it <= todayDate() }
        } else null
        val inputs = if (sameOwner) restored<Map<String, TrainingSeriesInput>>("trainingInputs").orEmpty().mapValues { (_, v) ->
            v.copy(saving = false, error = if (v.dirty) "Hay una edición pendiente de confirmar." else null)
        } else emptyMap()
        mutableState.value = TrainingUiState(
            selectedDate = date ?: todayDate(), today = todayDate(),
            draft = if (sameOwner) restored<RoutineDraft>("trainingDraft") ?: RoutineDraft() else RoutineDraft(),
            draftLocked = sameOwner && saved.get<Boolean>("trainingDraftLocked") == true,
            inputs = inputs,
            activeSessionId = if (sameOwner) saved["trainingSession"] else null,
            viewedRoutineId = if (sameOwner) saved["trainingViewedRoutine"] else null,
            isManagingRoutines = sameOwner && saved.get<Boolean>("trainingManaging") == true,
            editingRoutineId = if (sameOwner) saved["trainingEditing"] else null,
            pendingArchiveId = if (sameOwner) saved["trainingArchive"] else null,
            selectionRequest = if (sameOwner) restored("trainingSelection") else null,
            page = if (sameOwner) runCatching { TrainingPage.valueOf(saved.get<String>("trainingPage").orEmpty()) }.getOrDefault(TrainingPage.LIBRARY)
                else TrainingPage.LIBRARY,
        )
        refresh()
    }

    fun clear() {
        cancelJobs(); owner = null; clearSaved()
        mutableState.value = TrainingUiState(selectedDate = todayDate(), today = todayDate())
    }

    private fun cancelJobs() {
        loadJob?.cancel(); operationJob?.cancel()
        seriesJobs.values.forEach { it.cancel() }; seriesJobs.clear()
    }

    private fun clearSaved() {
        listOf("trainingOwner", "trainingDate", "trainingInputs", "trainingDraft", "trainingDraftLocked",
            "trainingSession", "trainingPage", "trainingSelection", "trainingViewedRoutine", "trainingManaging", "trainingEditing", "trainingArchive").forEach { saved.remove<Any>(it) }
    }

    private fun persist() {
        var state = mutableState.value
        if (state.selectedDate == state.today) {
            state = state.copy(todaySessions = state.sessions)
            mutableState.value = state
        }
        saved["trainingPage"] = state.page.name
        saved["trainingDate"] = state.selectedDate.toString()
        saved["trainingSession"] = state.activeSessionId
        saved["trainingViewedRoutine"] = state.viewedRoutineId
        saved["trainingManaging"] = state.isManagingRoutines
        saved["trainingEditing"] = state.editingRoutineId
        saved["trainingArchive"] = state.pendingArchiveId
        saved["trainingDraft"] = json.encodeToString(state.draft)
        saved["trainingDraftLocked"] = state.draftLocked
        saved["trainingInputs"] = json.encodeToString(state.inputs)
        saved["trainingSelection"] = state.selectionRequest?.let { json.encodeToString(it) }
    }

    private suspend fun ensureOwner(expected: String) {
        currentCoroutineContext().ensureActive()
        check(owner == expected && repository.currentUserId() == expected) { "La sesión cambió." }
    }

    fun refresh() {
        val user = owner ?: return
        if (mutableState.value.isBusy || (mutableState.value.sessions.isNotEmpty() && mutableState.value.pendingSeries)) return
        loadJob?.cancel()
        val date = mutableState.value.selectedDate
        mutableState.value = mutableState.value.copy(isLoading = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                val routines = withTimeout(30_000L) { repository.loadRoutines() }
                ensureOwner(user)
                mutableState.value = mutableState.value.copy(routines = routines)
                val catalog = withTimeout(30_000L) { repository.loadCatalog() }
                ensureOwner(user)
                mutableState.value = mutableState.value.copy(catalog = catalog)
                val sessions = withTimeout(30_000L) { repository.loadSessions(date, routines) }
                val currentDate = todayDate()
                val todaySessions = if (date == currentDate) sessions else withTimeout(30_000L) { repository.loadSessions(currentDate, routines) }
                ensureOwner(user)
                if (mutableState.value.selectedDate != date) return@launch
                val current = mutableState.value
                val active = sessions.firstOrNull { it.id == current.activeSessionId } ?: sessions.firstOrNull()
                val validIds = sessions.flatMap { it.exercises }.map { it.id }.toSet()
                val viewedRoutineId = current.viewedRoutineId?.takeIf { id -> routines.any { it.id == id } }
                val hasVisibleSession = active != null && (date == currentDate || active.hasRecordedData)
                val page = when {
                    current.page == TrainingPage.LIBRARY && !current.changingRoutine && !current.isManagingRoutines &&
                        current.selectionRequest == null && hasVisibleSession &&
                        (date == currentDate || sessions.count { it.hasRecordedData } == 1) -> TrainingPage.WORKOUT
                    current.page == TrainingPage.WORKOUT && !hasVisibleSession -> TrainingPage.LIBRARY
                    current.page == TrainingPage.ROUTINE_DETAILS && viewedRoutineId == null -> TrainingPage.LIBRARY
                    else -> current.page
                }
                mutableState.value = current.copy(
                    sessions = sessions, todaySessions = todaySessions, activeSessionId = active?.id, isLoading = false,
                    today = currentDate, page = page, viewedRoutineId = viewedRoutineId,
                    inputs = current.inputs.filterKeys { it.substringBefore(':') in validIds },
                )
                persist()
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) fail("No se pudo actualizar el entrenamiento.") else throw error
            } catch (error: Exception) { if (owner == user) fail(trainingMessage(error)) }
        }
    }

    fun onClockChanged() {
        val current = todayDate()
        if (owner == null) return
        val state = mutableState.value
        if (current != state.today) {
            mutableState.value = state.copy(today = current, todaySessions = emptyList())
            if ((state.selectedDate == state.today || state.selectedDate > current) && state.page != TrainingPage.WORKOUT && !state.pendingSeries) selectDate(current)
        }
    }

    fun selectDate(date: LocalDate) {
        val state = mutableState.value
        if (date > todayDate() || state.isBusy || state.pendingSeries || state.selectionRequest != null || state.pendingArchiveId != null) return
        mutableState.value = state.copy(selectedDate = date, today = todayDate(), sessions = emptyList(),
            activeSessionId = null, inputs = emptyMap(), page = TrainingPage.LIBRARY, changingRoutine = false)
        persist(); refresh()
    }

    fun openToday() {
        val state = mutableState.value
        if (state.isBusy || state.selectionRequest != null || state.pendingArchiveId != null) return
        if (state.pendingSeries) {
            mutableState.value = state.copy(page = TrainingPage.WORKOUT)
            persist()
            return
        }
        val session = state.todaySessions.firstOrNull()
        mutableState.value = state.copy(
            selectedDate = todayDate(), today = todayDate(), sessions = state.todaySessions,
            activeSessionId = session?.id, inputs = emptyMap(),
            page = if (session == null) TrainingPage.LIBRARY else TrainingPage.WORKOUT,
            changingRoutine = false, viewedRoutineId = null, isManagingRoutines = false,
        )
        persist(); refresh()
    }

    fun enterRoutines() {
        val state = mutableState.value
        if (state.isBusy || state.page == TrainingPage.CREATE || state.selectionRequest != null || state.pendingArchiveId != null) return
        val session = state.activeSession ?: return
        if (state.selectedDate != state.today &&
            (!session.hasRecordedData || state.page != TrainingPage.WORKOUT)) return
        mutableState.value = state.copy(page = TrainingPage.WORKOUT, changingRoutine = false, viewedRoutineId = null, isManagingRoutines = false)
        persist()
    }

    fun viewRoutine(id: String) {
        val state = mutableState.value
        if (state.isBusy || state.isLoading || state.routines.none { it.id == id }) return
        mutableState.value = state.copy(page = TrainingPage.ROUTINE_DETAILS, viewedRoutineId = id, error = null)
        persist()
    }

    fun startCreate() {
        if (mutableState.value.isBusy || mutableState.value.pendingArchiveId != null || owner == null) return
        mutableState.value = mutableState.value.copy(page = TrainingPage.CREATE, error = null)
        persist()
    }

    fun toggleRoutineManagement() {
        val state = mutableState.value
        if (!state.canManageRoutines) return
        mutableState.value = state.copy(isManagingRoutines = !state.isManagingRoutines)
        persist()
    }

    fun startEditRoutine(id: String) {
        val state = mutableState.value
        if (!state.isManagingRoutines || !state.canManageRoutines) return
        val plan = state.routines.firstOrNull { it.id == id } ?: return
        mutableState.value = state.copy(
            page = TrainingPage.CREATE, editingRoutineId = id, draftLocked = false, error = null,
            draft = RoutineDraft(name = plan.name, description = plan.description.orEmpty(),
                exercises = plan.exercises.map { it.copy(id = UUID.randomUUID().toString()) }),
        )
        persist()
    }

    fun requestArchiveRoutine(id: String) {
        val state = mutableState.value
        if (!state.isManagingRoutines || !state.canManageRoutines || state.routines.none { it.id == id }) return
        mutableState.value = state.copy(confirmArchiveId = id)
    }

    fun cancelArchiveRoutine() { mutableState.value = mutableState.value.copy(confirmArchiveId = null) }

    fun confirmArchiveRoutine() {
        val state = mutableState.value
        val id = state.confirmArchiveId ?: return
        if (!state.canManageRoutines) return
        mutableState.value = state.copy(confirmArchiveId = null, pendingArchiveId = id)
        persist()
        retryArchiveRoutine()
    }

    fun retryArchiveRoutine() {
        val user = owner ?: return
        val state = mutableState.value
        val id = state.pendingArchiveId ?: return
        if (state.isBusy || state.pendingSeries) return
        loadJob?.cancel()
        mutableState.value = state.copy(isBusy = true, error = null)
        operationJob = viewModelScope.launch {
            try {
                withTimeout(30_000L) { repository.archiveRoutine(id) }
                ensureOwner(user)
                mutableState.value = mutableState.value.copy(isBusy = false, pendingArchiveId = null,
                    routines = mutableState.value.routines.filterNot { it.id == id })
                persist()
                refresh()
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) fail("Eliminación sin confirmar. Pulsa Comprobar eliminación.") else throw error
            } catch (error: Exception) {
                if (owner == user) fail(trainingMessage(error) + " Pulsa Comprobar eliminación.")
            }
        }
    }

    private fun updateDraft(transform: (RoutineDraft) -> RoutineDraft) {
        val state = mutableState.value
        if (state.isBusy || state.draftLocked) return
        mutableState.value = state.copy(draft = transform(state.draft), error = null); persist()
    }
    fun changeName(value: String) { if (value.length <= TrainingInputLimits.MAX_NAME) updateDraft { it.copy(name = value) } }
    fun changeDescription(value: String) { if (value.length <= TrainingInputLimits.MAX_DESCRIPTION) updateDraft { it.copy(description = value) } }
    fun putExercise(exercise: PlannedExercise) = updateDraft { draft ->
        val existing = draft.exercises.any { it.id == exercise.id }
        draft.copy(exercises = if (existing) draft.exercises.map { if (it.id == exercise.id) exercise else it }
            else if (draft.exercises.size < TrainingInputLimits.MAX_EXERCISES) draft.exercises + exercise else draft.exercises)
    }
    fun removeExercise(id: String) = updateDraft { it.copy(exercises = it.exercises.filterNot { item -> item.id == id }) }
    fun moveExercise(from: String, to: String) = updateDraft { it.copy(exercises = reorderedExercises(it.exercises, from, to)) }

    fun saveRoutine() {
        val user = owner ?: return
        val state = mutableState.value
        if (state.isBusy || !state.draft.isValid) return
        loadJob?.cancel()
        mutableState.value = state.copy(isBusy = true, draftLocked = true, error = null)
        persist()
        operationJob = viewModelScope.launch {
            try {
                val routine = withTimeout(45_000L) {
                    state.editingRoutineId?.let { repository.replaceRoutine(it, state.draft) }
                        ?: repository.saveRoutine(state.draft)
                }
                ensureOwner(user)
                mutableState.value = mutableState.value.copy(
                    isBusy = false, draftLocked = false, draft = RoutineDraft(), editingRoutineId = null,
                    page = if (state.activeSession != null && !state.changingRoutine && !state.isManagingRoutines) TrainingPage.WORKOUT else TrainingPage.LIBRARY,
                    routines = (mutableState.value.routines.filterNot { it.id == routine.id || it.id == state.editingRoutineId } + routine),
                )
                persist(); refresh()
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) fail("No se pudo confirmar el guardado. Reintenta con el mismo borrador.") else throw error
            } catch (error: Exception) { if (owner == user) fail(trainingMessage(error) + " Reintenta para comprobar el guardado.") }
        }
    }

    fun back(): Boolean {
        val state = mutableState.value
        if (state.isBusy) return true
        if (state.page == TrainingPage.CREATE) {
            if (state.draft.name.isNotBlank() || state.draft.description.isNotBlank() || state.draft.exercises.isNotEmpty()) {
                mutableState.value = state.copy(confirmDiscard = true)
            } else leaveEditor()
            return true
        }
        if (state.page == TrainingPage.ROUTINE_DETAILS) {
            mutableState.value = state.copy(page = TrainingPage.LIBRARY, viewedRoutineId = null)
            persist(); return true
        }
        if (state.changingRoutine || state.isManagingRoutines) {
            mutableState.value = state.copy(
                page = if (state.activeSession != null) TrainingPage.WORKOUT else TrainingPage.LIBRARY,
                changingRoutine = false, isManagingRoutines = false,
            )
            persist(); return true
        }
        return false
    }

    fun cancelDiscard() { mutableState.value = mutableState.value.copy(confirmDiscard = false) }
    fun leaveEditor() {
        if (mutableState.value.isBusy) return
        mutableState.value = mutableState.value.copy(draft = RoutineDraft(), draftLocked = false, editingRoutineId = null,
            page = if (mutableState.value.activeSession != null && !mutableState.value.changingRoutine && !mutableState.value.isManagingRoutines)
                TrainingPage.WORKOUT else TrainingPage.LIBRARY, confirmDiscard = false)
        persist(); refresh()
    }

    fun openSession(id: String) {
        if (mutableState.value.isBusy) return
        if (mutableState.value.pendingSeries && mutableState.value.activeSessionId != id) return
        if (mutableState.value.sessions.none { it.id == id }) return
        mutableState.value = mutableState.value.copy(activeSessionId = id, page = TrainingPage.WORKOUT,
            changingRoutine = false, viewedRoutineId = null, isManagingRoutines = false, error = null)
        persist()
    }

    private var acknowledgedChangeSession: WorkoutSession? = null

    fun changeRoutine(progressConfirmed: Boolean = false) {
        if (mutableState.value.isBusy || mutableState.value.pendingSeries || mutableState.value.selectionRequest != null) return
        acknowledgedChangeSession = if (progressConfirmed) mutableState.value.activeSession else null
        mutableState.value = mutableState.value.copy(page = TrainingPage.LIBRARY, changingRoutine = true)
        persist()
    }

    fun requestRoutine(id: String) {
        val state = mutableState.value
        if (!state.canChooseRoutine || state.selectedDate != todayDate()) return
        val routine = state.routines.firstOrNull { it.id == id } ?: return
        if (routine.exercises.isEmpty()) return
        val previous = state.activeSession
        if (previous?.routineId == id) { openSession(previous.id); return }
        if (previous != null && previous.hasRecordedData &&
            !(state.changingRoutine && previous == acknowledgedChangeSession)) {
            mutableState.value = state.copy(confirmRoutineId = id)
        } else choose(id, previous?.id)
    }

    fun cancelRoutineChange() { mutableState.value = mutableState.value.copy(confirmRoutineId = null) }
    fun confirmRoutineChange() {
        val state = mutableState.value
        val id = state.confirmRoutineId ?: return
        if (state.isBusy || state.pendingSeries) return
        choose(id, state.activeSession?.id)
    }

    private fun choose(routineId: String, replaceId: String?) {
        val state = mutableState.value
        val request = RoutineSelectionRequest(UUID.randomUUID().toString(), routineId, state.selectedDate.toString(), replaceId)
        mutableState.value = state.copy(selectionRequest = request, confirmRoutineId = null)
        persist(); retrySelection()
    }

    fun retrySelection() {
        val user = owner ?: return
        val state = mutableState.value
        val request = state.selectionRequest ?: return
        val routine = state.routines.firstOrNull { it.id == request.routineId } ?: return
        if (state.isBusy || state.pendingSeries) return
        loadJob?.cancel()
        mutableState.value = state.copy(isBusy = true, error = null)
        operationJob = viewModelScope.launch {
            try {
                val session = withTimeout(45_000L) {
                    repository.chooseRoutine(routine, LocalDate.parse(request.date), request.requestId, request.replaceId)
                }
                ensureOwner(user)
                mutableState.value = mutableState.value.copy(
                    sessions = mutableState.value.sessions.filterNot { it.id == request.replaceId || it.id == session.id } + session,
                    activeSessionId = session.id, inputs = emptyMap(), selectionRequest = null,
                    page = TrainingPage.WORKOUT, isBusy = false, changingRoutine = false, viewedRoutineId = null,
                )
                persist()
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) fail("No se pudo confirmar el cambio. Pulsa Comprobar cambio; no se enviará otro identificador.") else throw error
            } catch (error: Exception) { if (owner == user) fail(trainingMessage(error)) }
        }
    }

    fun editSeries(exerciseId: String, number: Int, weight: String? = null, reps: String? = null) {
        val state = mutableState.value
        if (state.isBusy || state.selectionRequest != null || state.selectedDate != todayDate()) return
        val exercise = state.activeSession?.exercises?.firstOrNull { it.id == exerciseId } ?: return
        if (number !in 1..exercise.plannedSets) return
        val key = "$exerciseId:$number"
        val prior = seriesInput(exercise, number)
        if (prior.saving || prior.uncertain) return
        val next = prior.copy(weight = weight ?: prior.weight, reps = reps ?: prior.reps, dirty = true, saving = false, error = null)
        if (next.weight.length > 9 || next.reps.length > 4) return
        seriesJobs[key]?.cancel()
        mutableState.value = state.copy(inputs = state.inputs + (key to next))
        persist(); saveSeries(exerciseId, number, true)
    }

    fun seriesInput(exercise: WorkoutExercise, number: Int): TrainingSeriesInput {
        val recorded = exercise.sets.firstOrNull { it.number == number }
        return mutableState.value.inputs["${exercise.id}:$number"]
            ?: TrainingSeriesInput(weight = recorded?.weightKg.orEmpty(), reps = recorded?.reps?.toString().orEmpty())
    }

    fun saveSeries(exerciseId: String, number: Int, debounce: Boolean = false) {
        val user = owner ?: return
        val key = "$exerciseId:$number"
        val input = mutableState.value.inputs[key] ?: return
        if (!input.dirty || input.saving) return
        seriesJobs[key]?.cancel()
        seriesJobs[key] = viewModelScope.launch {
            if (debounce) delay(700)
            val weight = parseTrainingWeight(input.weight)
            val reps = input.reps.toIntOrNull()?.takeIf { it in 0..TrainingInputLimits.MAX_REPS }
            if (weight == null || reps == null) {
                updateInput(key, input.copy(error = when {
                    weight == null -> "Peso inválido: usa hasta dos decimales."
                    input.reps.isNotBlank() -> "Repeticiones inválidas."
                    else -> null
                }))
                return@launch
            }
            updateInput(key, input.copy(saving = true))
            try {
                val result = withTimeout(30_000L) { repository.saveSet(exerciseId, number, weight, reps) }
                ensureOwner(user)
                val state = mutableState.value
                val sessions = state.sessions.map { session -> session.copy(exercises = session.exercises.map { exercise ->
                    if (exercise.id == exerciseId) exercise.copy(
                        sets = exercise.sets.filterNot { it.number == number } + result, completionStatus = "pending",
                    ) else exercise
                }) }
                mutableState.value = state.copy(sessions = sessions,
                    inputs = state.inputs + (key to input.copy(weight = weight.toPlainString(), reps = reps.toString(), dirty = false, saving = false, error = null, uncertain = false)))
                persist()
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) updateInput(key, input.copy(error = "Guardado sin confirmar. Pulsa Reintentar.", uncertain = true))
                else throw error
            } catch (_: Exception) {
                if (owner == user) updateInput(key, input.copy(error = "No se pudo guardar. Pulsa Reintentar.", uncertain = true))
            }
        }
    }

    fun discardSeriesEdit(exerciseId: String, number: Int) {
        val key = "$exerciseId:$number"
        val input = mutableState.value.inputs[key] ?: return
        if (input.saving || input.uncertain) return
        seriesJobs.remove(key)?.cancel()
        mutableState.value = mutableState.value.copy(inputs = mutableState.value.inputs - key)
        persist()
    }

    private fun updateInput(key: String, input: TrainingSeriesInput) {
        mutableState.value = mutableState.value.copy(inputs = mutableState.value.inputs + (key to input))
        persist()
    }

    fun markExercise(exerciseId: String, status: String) {
        val user = owner ?: return
        val state = mutableState.value
        if (state.isBusy || state.pendingSeries || state.selectedDate != todayDate() || state.selectionRequest != null) return
        val exercise = state.activeSession?.exercises?.firstOrNull { it.id == exerciseId } ?: return
        if (status !in setOf("pending", "completed", "skipped")) return
        val nextStatus = if (status == exercise.completionStatus) "pending" else status
        if (nextStatus == "completed" && exercise.plannedSets == 0) return
        loadJob?.cancel()
        mutableState.value = state.copy(isBusy = true, error = null)
        operationJob = viewModelScope.launch {
            try {
                val completedSets = withTimeout(30_000L) {
                    if (nextStatus == "completed") repository.completeExercise(exerciseId)
                    else { repository.setStatus(exerciseId, nextStatus); null }
                }
                ensureOwner(user)
                mutableState.value = mutableState.value.copy(isBusy = false,
                    sessions = mutableState.value.sessions.map { session -> session.copy(exercises = session.exercises.map {
                        if (it.id == exerciseId) it.copy(completionStatus = nextStatus, sets = completedSets ?: it.sets) else it
                    }) })
                persist()
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) fail("No se pudo confirmar el estado. Actualiza el entrenamiento.") else throw error
            } catch (error: Exception) { if (owner == user) fail(trainingMessage(error)) }
        }
    }

    private fun fail(message: String) {
        mutableState.value = mutableState.value.copy(isBusy = false, isLoading = false, error = message)
        persist()
    }
}

private fun trainingMessage(error: Exception): String =
    if (error is RestException && (error.error.contains("PGRST202") || error.error.contains("Could not find the function") ||
                error.error.contains("completion_status") || error.error.contains("does not exist"))) {
        "Falta aplicar la migración del registro de entrenamiento en Supabase."
    } else "No se pudo completar la operación. Revisa la conexión y vuelve a intentarlo."
