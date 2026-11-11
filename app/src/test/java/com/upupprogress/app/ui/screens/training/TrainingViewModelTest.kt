package com.upupprogress.app.ui.screens.training

import androidx.lifecycle.SavedStateHandle
import com.upupprogress.app.data.model.*
import com.upupprogress.app.data.repository.TrainingGateway
import java.math.BigDecimal
import java.time.LocalDate
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class TrainingViewModelTest {
    private val day = LocalDate.of(2026, 11, 10)
    private val first = RoutinePlan("one", "Rutina A", "Ejemplo", listOf(
        PlannedExercise("p1", "e1", "Sentadilla", 2, 10),
        PlannedExercise("p2", "e2", "Flexiones", 1, 12),
    ))
    private val second = first.copy(id = "two", name = "Rutina B")
    @Before fun setMain() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }

    private class Fake(val plans: MutableList<RoutinePlan>) : TrainingGateway {
        var user = "owner"
        val sessions = linkedMapOf<String, WorkoutSession>()
        val selections = mutableListOf<Pair<String,String?>>()
        val weights = mutableListOf<BigDecimal>()
        var loseSelectionResponse = false
        var failSessionLoads = false
        var failStatus = false
        var routineCalls = 0
        var loseEditResponse = false
        var loseArchiveResponse = false
        val edits = mutableMapOf<String, RoutinePlan>()
        val archiveCalls = mutableListOf<String>()
        override fun currentUserId() = user
        override suspend fun loadRoutines() = plans.toList()
        override suspend fun loadCatalog() = emptyList<ExerciseCatalogItem>()
        override suspend fun loadSessions(date: LocalDate, routines: List<RoutinePlan>): List<WorkoutSession> {
            if (failSessionLoads) throw IOException("synthetic failed session load")
            return sessions.values.filter { it.performedOn == date.toString() }
        }
        override suspend fun saveRoutine(draft: RoutineDraft): RoutinePlan {
            routineCalls++
            return RoutinePlan(draft.id,draft.name,draft.description,draft.exercises).also {
                if (plans.none { row -> row.id == it.id }) plans.add(it)
            }
        }
        override suspend fun replaceRoutine(originalId: String, draft: RoutineDraft): RoutinePlan {
            val result = edits[draft.id] ?: RoutinePlan(draft.id, draft.name, draft.description, draft.exercises).also {
                plans.removeAll { row -> row.id == originalId }
                plans.add(it)
                edits[draft.id] = it
            }
            if (loseEditResponse) throw IOException("synthetic lost edit response")
            return result
        }
        override suspend fun archiveRoutine(id: String) {
            archiveCalls.add(id)
            plans.removeAll { it.id == id }
            if (loseArchiveResponse) throw IOException("synthetic lost archive response")
        }
        override suspend fun chooseRoutine(routine: RoutinePlan, date: LocalDate, requestId: String, replaceId: String?): WorkoutSession {
            selections.add(requestId to replaceId)
            val result = sessions[requestId] ?: WorkoutSession(
                requestId, date.toString(), routine.id, routine.name,
                routine.exercises.mapIndexed { index, item ->
                    WorkoutExercise("$requestId-$index", item.exerciseId!!, item.name, index+1, item.plannedSets, item.plannedReps)
                },
            ).also {
                sessions.remove(replaceId)
                sessions[requestId] = it
            }
            if (loseSelectionResponse) throw IOException("synthetic lost response")
            return result
        }
        override suspend fun saveSet(exerciseId: String, number: Int, weight: BigDecimal, reps: Int): RecordedTrainingSet {
            weights.add(weight)
            val item = RecordedTrainingSet("$exerciseId:$number",number,weight.toPlainString(),reps)
            sessions.replaceAll { _, session -> session.copy(exercises = session.exercises.map {
                if (it.id == exerciseId) it.copy(sets = it.sets.filterNot { row -> row.number == number } + item,
                    completionStatus = "pending") else it
            }) }
            return item
        }
        override suspend fun completeExercise(exerciseId: String): List<RecordedTrainingSet> {
            if (failStatus) throw IOException("synthetic failed completion")
            val exercise = sessions.values.flatMap { it.exercises }.first { it.id == exerciseId }
            val sets = (1..exercise.plannedSets).map { number ->
                exercise.sets.firstOrNull { it.number == number }
                    ?: RecordedTrainingSet("$exerciseId:$number", number, "0", 0)
            }
            sessions.replaceAll { _, session -> session.copy(exercises = session.exercises.map {
                if (it.id == exerciseId) it.copy(completionStatus = "completed", sets = sets) else it
            }) }
            return sets
        }
        override suspend fun setStatus(exerciseId: String, status: String) {
            if (failStatus) throw IOException("synthetic failed status update")
            sessions.replaceAll { _, session -> session.copy(exercises = session.exercises.map {
                if (it.id == exerciseId) it.copy(completionStatus = status) else it
            }) }
        }
    }

    @Test fun createWithoutWeightsEditReorderRemoveAndSaveOnlyOnce() = runTest {
        val repo = Fake(mutableListOf())
        val vm = TrainingViewModel(SavedStateHandle(),repo,{day})
        vm.activate("owner"); advanceUntilIdle()
        vm.startCreate(); vm.changeName("Mi rutina")
        first.exercises.forEach(vm::putExercise)
        vm.moveExercise("p1","p2")
        assertEquals(listOf("p2","p1"),vm.uiState.value.draft.exercises.map { it.id })
        vm.putExercise(first.exercises[0].copy(plannedReps=8))
        vm.removeExercise("p2")
        assertTrue(vm.uiState.value.draft.isValid)
        vm.saveRoutine(); vm.saveRoutine(); advanceUntilIdle()
        assertEquals(1,repo.routineCalls)
        assertEquals(8,repo.plans.single().exercises.single().plannedReps)
        assertEquals(TrainingPage.LIBRARY,vm.uiState.value.page)
        vm.clear()
    }

    @Test fun eachValidSeriesSavesAutomaticallyAndBlankWeightIsZero() = runTest {
        val repo = Fake(mutableListOf(first))
        val vm = TrainingViewModel(SavedStateHandle(),repo,{day})
        vm.activate("owner"); advanceUntilIdle()
        vm.requestRoutine(first.id); advanceUntilIdle()
        val exercise = vm.uiState.value.activeSession!!.exercises[0]
        vm.editSeries(exercise.id,1,reps="10")
        advanceTimeBy(699)
        assertTrue(repo.weights.isEmpty())
        advanceUntilIdle()
        assertEquals(BigDecimal.ZERO,repo.weights.single())
        assertEquals("0",vm.uiState.value.inputs["${exercise.id}:1"]!!.weight)
        vm.editSeries(exercise.id,2,weight="22,50",reps="8"); advanceUntilIdle()
        vm.markExercise(exercise.id,"completed"); advanceUntilIdle()
        assertEquals("completed",vm.uiState.value.activeSession!!.exercises[0].completionStatus)
        val skipped = vm.uiState.value.activeSession!!.exercises[1]
        vm.markExercise(skipped.id,"skipped"); advanceUntilIdle()
        assertEquals("skipped",vm.uiState.value.activeSession!!.exercises[1].completionStatus)
        assertTrue(vm.uiState.value.activeSession!!.exercises[1].sets.isEmpty())
        vm.clear()
    }

    @Test fun exerciseStatusCanBeDeselectedAndSwitchedWithoutLosingRecordedSets() = runTest {
        val repo = Fake(mutableListOf(first))
        val vm = TrainingViewModel(SavedStateHandle(), repo, { day })
        vm.activate("owner"); advanceUntilIdle()
        vm.requestRoutine(first.id); advanceUntilIdle()
        val id = vm.uiState.value.activeSession!!.exercises.first().id
        vm.markExercise(id, "skipped"); advanceUntilIdle()
        vm.markExercise(id, "skipped"); advanceUntilIdle()
        assertEquals("pending", repo.sessions.values.single().exercises.first().completionStatus)
        vm.editSeries(id, 1, reps = "10"); advanceUntilIdle()
        vm.editSeries(id, 2, reps = "8"); advanceUntilIdle()
        val recorded = vm.uiState.value.activeSession!!.exercises.first().sets
        vm.markExercise(id, "skipped"); advanceUntilIdle()
        vm.markExercise(id, "completed"); advanceUntilIdle()
        assertEquals("completed", vm.uiState.value.activeSession!!.exercises.first().completionStatus)
        vm.markExercise(id, "completed"); advanceUntilIdle()
        assertEquals("pending", repo.sessions.values.single().exercises.first().completionStatus)
        assertEquals("pending", vm.uiState.value.activeSession!!.exercises.first().completionStatus)
        vm.markExercise(id, "completed"); advanceUntilIdle()
        vm.markExercise(id, "skipped"); advanceUntilIdle()
        assertEquals("skipped", vm.uiState.value.activeSession!!.exercises.first().completionStatus)
        assertEquals(recorded, repo.sessions.values.single().exercises.first().sets)
        vm.clear()
    }

    @Test fun confirmedCompletionFillsOnlyMissingSetsAndZeroRepsSaveNormally() = runTest {
        val repo = Fake(mutableListOf(first))
        val vm = TrainingViewModel(SavedStateHandle(), repo, { day })
        vm.activate("owner"); advanceUntilIdle()
        vm.requestRoutine(first.id); advanceUntilIdle()
        val id = vm.uiState.value.activeSession!!.exercises.first().id
        vm.editSeries(id, 1, weight = "12.5", reps = "8"); advanceUntilIdle()
        repo.failStatus = true
        vm.markExercise(id, "completed"); advanceUntilIdle()
        assertEquals("pending", vm.uiState.value.activeSession!!.exercises.first().completionStatus)
        assertEquals(1, vm.uiState.value.activeSession!!.exercises.first().sets.size)
        repo.failStatus = false
        vm.markExercise(id, "completed"); advanceUntilIdle()
        val completed = vm.uiState.value.activeSession!!.exercises.first()
        assertEquals("completed", completed.completionStatus)
        assertEquals(listOf(8, 0), completed.sets.map { it.reps })
        assertEquals(listOf("12.5", "0"), completed.sets.map { it.weightKg })
        vm.markExercise(id, "completed"); advanceUntilIdle()
        assertEquals(completed.sets, vm.uiState.value.activeSession!!.exercises.first().sets)
        vm.editSeries(id, 2, reps = "0"); advanceUntilIdle()
        assertFalse(vm.uiState.value.pendingSeries)
        assertEquals(0, repo.sessions.values.single().exercises.first().sets.first { it.number == 2 }.reps)
        vm.editSeries(id, 2, reps = "-1"); advanceUntilIdle()
        assertNotNull(vm.uiState.value.inputs["$id:2"]!!.error)
        vm.clear()
    }

    @Test fun failedDeselectionKeepsConfirmedStatusUntilRetrySucceeds() = runTest {
        val repo = Fake(mutableListOf(first))
        val vm = TrainingViewModel(SavedStateHandle(), repo, { day })
        vm.activate("owner"); advanceUntilIdle()
        vm.requestRoutine(first.id); advanceUntilIdle()
        val id = vm.uiState.value.activeSession!!.exercises.first().id
        vm.markExercise(id, "skipped"); advanceUntilIdle()
        repo.failStatus = true
        vm.markExercise(id, "skipped"); advanceUntilIdle()
        assertEquals("skipped", vm.uiState.value.activeSession!!.exercises.first().completionStatus)
        assertNotNull(vm.uiState.value.error)
        repo.failStatus = false
        vm.markExercise(id, "skipped"); advanceUntilIdle()
        assertEquals("pending", vm.uiState.value.activeSession!!.exercises.first().completionStatus)
        assertNull(vm.uiState.value.error)
        vm.clear()
    }

    @Test fun replacingRecordedRoutineRequiresConfirmationAndRetriesTheSameRequestId() = runTest {
        val repo = Fake(mutableListOf(first,second))
        val vm = TrainingViewModel(SavedStateHandle(),repo,{day})
        vm.activate("owner"); advanceUntilIdle()
        vm.requestRoutine(first.id); advanceUntilIdle()
        val old = vm.uiState.value.activeSession!!
        vm.editSeries(old.exercises[0].id,1,reps="10"); advanceUntilIdle()
        vm.changeRoutine(); vm.requestRoutine(second.id)
        assertEquals(second.id,vm.uiState.value.confirmRoutineId)
        assertEquals(1,repo.selections.size)
        vm.cancelRoutineChange()
        assertNotNull(repo.sessions[old.id])
        vm.requestRoutine(second.id)
        repo.loseSelectionResponse = true
        vm.confirmRoutineChange(); advanceUntilIdle()
        val request = vm.uiState.value.selectionRequest!!
        assertEquals(old.id,request.replaceId)
        assertNotNull(vm.uiState.value.error)
        repo.loseSelectionResponse = false
        vm.retrySelection(); advanceUntilIdle()
        assertEquals(request.requestId,repo.selections.last().first)
        assertEquals(request.requestId,vm.uiState.value.activeSessionId)
        assertNull(repo.sessions[old.id])
        assertEquals(1,repo.sessions.size)
        assertFalse(vm.uiState.value.activeSession!!.hasRecordedData)
        vm.clear()
    }

    @Test fun historyRestorationAndSignOutDoNotMixDatesOrAccounts() = runTest {
        val repo = Fake(mutableListOf(first))
        val handle = SavedStateHandle()
        val vm = TrainingViewModel(handle,repo,{day})
        vm.activate("owner"); advanceUntilIdle()
        vm.startCreate(); vm.changeName("Borrador")
        vm.putExercise(first.exercises[0])
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = TrainingViewModel(restoredHandle,repo,{day})
        restored.activate("owner"); advanceUntilIdle()
        assertEquals("Borrador",restored.uiState.value.draft.name)
        assertEquals(TrainingPage.CREATE,restored.uiState.value.page)
        restored.leaveEditor(); advanceUntilIdle()
        restored.selectDate(day.minusDays(1)); advanceUntilIdle()
        restored.requestRoutine(first.id); advanceUntilIdle()
        assertTrue(repo.selections.isEmpty())
        restored.selectDate(day); advanceUntilIdle()
        restored.requestRoutine(first.id); advanceUntilIdle()
        assertEquals(day.toString(),restored.uiState.value.activeSession!!.performedOn)
        restored.selectDate(day.minusDays(1)); advanceUntilIdle()
        assertTrue(restored.uiState.value.sessions.isEmpty())
        restored.clear()
        assertTrue(restored.uiState.value.sessions.isEmpty())
        assertTrue(restored.uiState.value.inputs.isEmpty())
        assertNull(restoredHandle.get<String>("trainingOwner"))
        vm.clear()
    }

    @Test fun editingUsesANewVersionAndPreservesTheActiveWorkout() = runTest {
        val repo = Fake(mutableListOf(first, second))
        val handle = SavedStateHandle()
        val vm = TrainingViewModel(handle, repo, { day })
        vm.activate("owner"); advanceUntilIdle()
        vm.requestRoutine(first.id); advanceUntilIdle()
        val originalSession = vm.uiState.value.activeSession!!
        vm.editSeries(originalSession.exercises.first().id, 1, reps = "10"); advanceUntilIdle()
        val recorded = vm.uiState.value.activeSession
        vm.changeRoutine(); vm.toggleRoutineManagement()
        assertTrue(vm.uiState.value.isManagingRoutines)
        assertFalse(vm.uiState.value.canChooseRoutine)
        vm.startEditRoutine(first.id)
        val draftId = vm.uiState.value.draft.id
        assertNotEquals(first.id, draftId)
        assertTrue(vm.uiState.value.draft.exercises.none { item -> first.exercises.any { it.id == item.id } })
        vm.changeName("Nombre modificado")
        val editedExercise = vm.uiState.value.draft.exercises.first().copy(plannedReps = 15)
        vm.putExercise(editedExercise)
        repo.loseEditResponse = true
        vm.saveRoutine(); advanceUntilIdle()
        assertTrue(vm.uiState.value.draftLocked)
        assertEquals(first.id, vm.uiState.value.editingRoutineId)
        assertEquals(draftId, vm.uiState.value.draft.id)
        repo.loseEditResponse = false
        vm.saveRoutine(); advanceUntilIdle()
        assertEquals(1, repo.edits.size)
        assertEquals(recorded, vm.uiState.value.activeSession)
        assertEquals("Nombre modificado", vm.uiState.value.routines.first { it.id == draftId }.name)
        assertNull(vm.uiState.value.editingRoutineId)
        assertTrue(vm.uiState.value.isManagingRoutines)
        vm.openSession(recorded!!.id)
        assertFalse(vm.uiState.value.isManagingRoutines)
        assertFalse(vm.back())
        vm.clear()
    }

    @Test fun deletionRequiresConfirmationAndRecoversAnUncertainResponseAfterRestoration() = runTest {
        val repo = Fake(mutableListOf(first))
        val handle = SavedStateHandle()
        val vm = TrainingViewModel(handle, repo, { day })
        vm.activate("owner"); advanceUntilIdle()
        vm.requestRoutine(first.id); advanceUntilIdle()
        val originalSession = vm.uiState.value.activeSession!!
        vm.editSeries(originalSession.exercises.first().id, 1, reps = "10"); advanceUntilIdle()
        val recorded = vm.uiState.value.activeSession
        vm.changeRoutine(); vm.toggleRoutineManagement()
        vm.requestArchiveRoutine(first.id)
        vm.cancelArchiveRoutine()
        assertTrue(repo.archiveCalls.isEmpty())
        assertEquals(1, repo.plans.size)
        vm.requestArchiveRoutine(first.id)
        repo.loseArchiveResponse = true
        vm.confirmArchiveRoutine(); advanceUntilIdle()
        assertEquals(first.id, vm.uiState.value.pendingArchiveId)
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        vm.clear()
        val restored = TrainingViewModel(restoredHandle, repo, { day })
        restored.activate("owner"); advanceUntilIdle()
        assertEquals(first.id, restored.uiState.value.pendingArchiveId)
        assertFalse(restored.uiState.value.canManageRoutines)
        repo.loseArchiveResponse = false
        restored.retryArchiveRoutine(); advanceUntilIdle()
        assertNull(restored.uiState.value.pendingArchiveId)
        assertTrue(restored.uiState.value.routines.isEmpty())
        assertEquals(recorded, restored.uiState.value.activeSession)
        assertEquals(listOf(first.id, first.id), repo.archiveCalls)
        restored.clear()
        assertNull(restoredHandle.get<String>("trainingArchive"))
    }

    @Test fun cancellingAnEditDoesNotModifyTheSavedRoutine() = runTest {
        val repo = Fake(mutableListOf(first))
        val handle = SavedStateHandle()
        val vm = TrainingViewModel(handle, repo, { day })
        vm.activate("owner"); advanceUntilIdle()
        vm.toggleRoutineManagement(); vm.startEditRoutine(first.id)
        vm.changeName("Cambio sin guardar")
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        vm.clear()
        val restored = TrainingViewModel(restoredHandle, repo, { day })
        restored.activate("owner"); advanceUntilIdle()
        assertEquals(first.id, restored.uiState.value.editingRoutineId)
        assertEquals("Cambio sin guardar", restored.uiState.value.draft.name)
        assertTrue(restored.back())
        assertTrue(restored.uiState.value.confirmDiscard)
        restored.leaveEditor(); advanceUntilIdle()
        assertEquals(first, repo.plans.single())
        assertTrue(repo.edits.isEmpty())
        assertNull(restored.uiState.value.editingRoutineId)
        restored.clear()
    }

    @Test fun viewingARoutineDoesNotStartAWorkout() = runTest {
        val repo = Fake(mutableListOf(first, second))
        val vm = TrainingViewModel(SavedStateHandle(), repo, { day })
        vm.activate("owner"); advanceUntilIdle()
        vm.viewRoutine(first.id)
        assertEquals(TrainingPage.ROUTINE_DETAILS, vm.uiState.value.page)
        assertEquals(first, vm.uiState.value.viewedRoutine)
        assertTrue(repo.selections.isEmpty())
        assertTrue(repo.sessions.isEmpty())
        assertTrue(vm.back())
        assertEquals(TrainingPage.LIBRARY, vm.uiState.value.page)
        assertNull(vm.uiState.value.viewedRoutine)
        vm.viewRoutine(second.id)
        vm.requestRoutine(second.id); advanceUntilIdle()
        assertEquals(TrainingPage.WORKOUT, vm.uiState.value.page)
        assertEquals(second.id, vm.uiState.value.activeSession!!.routineId)
        assertEquals(1, repo.selections.size)
        vm.clear()
    }

    @Test fun chosenRoutineIsRecoveredFromBackendAfterAFreshAppStartWithoutRecordedSets() = runTest {
        val repo = Fake(mutableListOf(first, second))
        val vm = TrainingViewModel(SavedStateHandle(), repo, { day })
        vm.activate("owner"); advanceUntilIdle()
        vm.requestRoutine(second.id); advanceUntilIdle()
        val sessionId = vm.uiState.value.activeSessionId
        assertFalse(vm.uiState.value.activeSession!!.hasRecordedData)
        vm.clear()

        // No SavedStateHandle: el dato decisivo es la sesión persistida, no una preferencia local.
        val reopened = TrainingViewModel(SavedStateHandle(), repo, { day })
        reopened.activate("owner")
        assertTrue(reopened.uiState.value.isLoading)
        advanceUntilIdle()
        assertEquals(TrainingPage.WORKOUT, reopened.uiState.value.page)
        assertEquals(sessionId, reopened.uiState.value.activeSessionId)
        assertEquals(second.id, reopened.uiState.value.activeSession!!.routineId)
        assertEquals(1, repo.selections.size)
        assertFalse(reopened.back())
        assertEquals(TrainingPage.WORKOUT, reopened.uiState.value.page)
        reopened.clear()
    }

    @Test fun returningToRoutinesResumesTheSelectedWorkoutAndDoesNotRestartIt() = runTest {
        val repo = Fake(mutableListOf(first, second))
        val vm = TrainingViewModel(SavedStateHandle(), repo, { day })
        vm.activate("owner"); advanceUntilIdle()
        vm.requestRoutine(first.id); advanceUntilIdle()
        val exercise = vm.uiState.value.activeSession!!.exercises.first()
        vm.editSeries(exercise.id, 1, weight = "12", reps = "10"); advanceUntilIdle()
        val originalId = vm.uiState.value.activeSessionId

        vm.changeRoutine()
        assertTrue(vm.uiState.value.changingRoutine)
        vm.enterRoutines()
        assertEquals(TrainingPage.WORKOUT, vm.uiState.value.page)
        assertFalse(vm.uiState.value.changingRoutine)
        vm.changeRoutine()
        vm.requestRoutine(first.id); advanceUntilIdle()
        assertEquals(originalId, vm.uiState.value.activeSessionId)
        assertEquals(1, repo.selections.size)
        assertEquals(1, vm.uiState.value.activeSession!!.exercises.first().sets.size)
        vm.clear()
    }

    @Test fun dailySelectionDoesNotCarryOverToTheNextDate() = runTest {
        val repo = Fake(mutableListOf(first))
        val vm = TrainingViewModel(SavedStateHandle(), repo, { day })
        vm.activate("owner"); advanceUntilIdle()
        vm.requestRoutine(first.id); advanceUntilIdle()
        vm.clear()
        val nextDay = TrainingViewModel(SavedStateHandle(), repo, { day.plusDays(1) })
        nextDay.activate("owner"); advanceUntilIdle()
        assertEquals(TrainingPage.LIBRARY, nextDay.uiState.value.page)
        assertNull(nextDay.uiState.value.activeSession)
        assertTrue(nextDay.uiState.value.todaySessions.isEmpty())
        assertEquals(1, repo.selections.size)
        nextDay.clear()
    }

    @Test fun failedSessionRecoveryDoesNotAllowCreatingAnotherDailySelection() = runTest {
        val repo = Fake(mutableListOf(first))
        val existing = repo.chooseRoutine(first, day, "existing", null)
        repo.failSessionLoads = true
        val vm = TrainingViewModel(SavedStateHandle(), repo, { day })
        vm.activate("owner"); advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.canChooseRoutine)
        vm.requestRoutine(first.id); advanceUntilIdle()
        assertEquals(1, repo.selections.size)
        repo.failSessionLoads = false
        vm.refresh(); advanceUntilIdle()
        assertEquals(TrainingPage.WORKOUT, vm.uiState.value.page)
        assertEquals(existing.id, vm.uiState.value.activeSessionId)
        assertEquals(1, repo.selections.size)
        vm.clear()
    }

    @Test fun historyKeepsAllRecordedSessionsOfADateAccessible() = runTest {
        val repo = Fake(mutableListOf(first, second))
        val oldDay = day.minusDays(1)
        val a = repo.chooseRoutine(first, oldDay, "old-one", null)
        val b = repo.chooseRoutine(second, oldDay, "old-two", null)
        repo.saveSet(a.exercises.first().id, 1, BigDecimal.ZERO, 10)
        repo.saveSet(b.exercises.first().id, 1, BigDecimal.ZERO, 10)
        val vm = TrainingViewModel(SavedStateHandle(), repo, { day })
        vm.activate("owner"); advanceUntilIdle()
        vm.selectDate(oldDay); advanceUntilIdle()
        assertEquals(TrainingPage.LIBRARY, vm.uiState.value.page)
        assertEquals(2, vm.uiState.value.sessions.size)
        vm.enterRoutines()
        assertEquals(TrainingPage.LIBRARY, vm.uiState.value.page)
        vm.openSession(b.id)
        assertEquals(TrainingPage.WORKOUT, vm.uiState.value.page)
        assertEquals(b.id, vm.uiState.value.activeSessionId)
        vm.clear()
    }
}
