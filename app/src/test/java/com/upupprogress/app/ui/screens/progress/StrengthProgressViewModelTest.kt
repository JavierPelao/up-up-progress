package com.upupprogress.app.ui.screens.progress

import com.upupprogress.app.data.model.*
import com.upupprogress.app.data.repository.TrainingProgressGateway
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.*

@OptIn(ExperimentalCoroutinesApi::class)
class StrengthProgressViewModelTest {
    private val from = LocalDate.of(2026, 11, 2)
    private val today = LocalDate.of(2026, 11, 12)

    @Before fun setMain() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }

    private fun record(id: String, exercise: String, date: String, kg: String, reps: Int, number: Int = 1) =
        ExerciseSetRecord("owner", "session-$date", "routine", LocalDate.parse(date), "we-$exercise-$date",
            exercise, if (exercise == "squat") "Sentadilla" else "Press", "pending",
            RecordedTrainingSet(id, number, kg, reps))

    private inner class Fake : TrainingProgressGateway {
        var user = "owner"
        var options = listOf(RecordedExercise("squat", "Sentadilla", from), RecordedExercise("press", "Press", from))
        var failOptions = false
        var failHistory = false
        var delayedExercise: String? = null
        var optionsCalls = 0
        val historyCalls = mutableListOf<String>()
        val records = mutableMapOf(
            "squat" to listOf(record("real", "squat", "2026-11-02", "90", 3, 2),
                record("estimated", "squat", "2026-11-05", "80", 10)),
            "press" to listOf(record("press-set", "press", "2026-11-09", "30", 8)),
        )
        override fun currentUserId() = user
        override suspend fun loadRecordedExercises(throughInclusive: LocalDate): List<RecordedExercise> {
            optionsCalls++
            if (failOptions) throw IOException("synthetic")
            return options
        }
        override suspend fun loadExerciseHistory(
            exerciseId: String, fromInclusive: LocalDate, throughInclusive: LocalDate,
        ): List<ExerciseSetRecord> {
            historyCalls.add(exerciseId)
            if (delayedExercise == exerciseId) withContext(NonCancellable) { delay(500) }
            if (failHistory) throw IOException("synthetic")
            return records[exerciseId].orEmpty()
        }
    }

    private fun TestScope.model(fake: Fake, clock: () -> LocalDate = { today }) =
        StrengthProgressViewModel(fake, clock, StandardTestDispatcher(testScheduler)).also { it.activate("owner") }

    @Test fun loadsRecordedExercisesAndPreservesBothOriginalSources() = runTest {
        val fake = Fake()
        val vm = model(fake)
        assertEquals(0, fake.optionsCalls) // Entrar en la pantalla solicita refresh; activar no lee.
        vm.refresh(); advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("squat", state.selectedExerciseId)
        val points = strengthChartPoints(state.progress!!)
        assertEquals("real", points.first { it.metric == StrengthMetric.MAXIMUM_LOAD }.measurement.source.set.id)
        assertEquals("estimated", points.first { it.metric == StrengthMetric.ESTIMATED_ONE_REP_MAX }.measurement.source.set.id)
        vm.selectPoint(points.last())
        assertEquals("estimated", vm.uiState.value.selectedPoint!!.measurement.source.set.id)
        vm.dismissPoint()
        assertNull(vm.uiState.value.selectedPoint)
        vm.clear()
    }

    @Test fun changingPeriodReusesHistoryAndClosesOldPointDetails() = runTest {
        val fake = Fake(); val vm = model(fake)
        vm.refresh(); advanceUntilIdle()
        vm.selectPoint(strengthChartPoints(vm.uiState.value.progress!!).first())
        vm.selectPeriod(TrainingPeriod.MONTH); advanceUntilIdle()
        assertEquals(TrainingPeriod.MONTH, vm.uiState.value.progress!!.period)
        assertEquals(1, vm.uiState.value.progress!!.points.size)
        assertNull(vm.uiState.value.selectedPoint)
        assertEquals(listOf("squat"), fake.historyCalls)
        assertEquals(1, fake.optionsCalls)
        vm.selectPeriod(TrainingPeriod.WEEK); advanceUntilIdle()
        assertEquals(2, vm.uiState.value.progress!!.points.size)
        vm.clear()
    }

    @Test fun switchingExerciseRejectsStaleSelectionsAndClearsPriorData() = runTest {
        val fake = Fake(); val vm = model(fake)
        vm.refresh(); advanceUntilIdle()
        val stale = strengthChartPoints(vm.uiState.value.progress!!).first()
        vm.selectExercise("press")
        assertNull(vm.uiState.value.progress)
        assertNull(vm.uiState.value.selectedPoint)
        advanceUntilIdle()
        vm.selectPoint(stale)
        assertNull(vm.uiState.value.selectedPoint)
        assertEquals("press", vm.uiState.value.progress!!.exerciseId)
        assertTrue(strengthChartPoints(vm.uiState.value.progress!!).all { it.measurement.source.exerciseId == "press" })
        vm.clear()
    }

    @Test fun lateCancelledHistoryDoesNotOverwriteTheNewExerciseOrPeriod() = runTest {
        val fake = Fake().apply { delayedExercise = "squat" }
        val vm = model(fake)
        vm.refresh(); runCurrent()
        vm.selectExercise("press")
        vm.selectPeriod(TrainingPeriod.MONTH)
        advanceUntilIdle()
        assertEquals("press", vm.uiState.value.progress!!.exerciseId)
        assertEquals(TrainingPeriod.MONTH, vm.uiState.value.progress!!.period)
        assertFalse(vm.uiState.value.isLoading)
        vm.clear()
    }

    @Test fun emptyHistoryShowsEmptyStateAndErrorsCanBeRetriedWithoutStaleChart() = runTest {
        val fake = Fake().apply { options = emptyList() }
        val vm = model(fake)
        vm.refresh(); advanceUntilIdle()
        assertTrue(vm.uiState.value.exercises.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.progress)
        assertTrue(fake.historyCalls.isEmpty())
        fake.failOptions = true
        vm.refresh(); advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)
        fake.failOptions = false
        fake.options = listOf(RecordedExercise("squat", "Sentadilla", from))
        vm.refresh(); advanceUntilIdle()
        assertNotNull(vm.uiState.value.progress)
        fake.failHistory = true
        vm.refresh(); advanceUntilIdle()
        assertNull(vm.uiState.value.progress)
        assertNotNull(vm.uiState.value.error)
        fake.failHistory = false
        vm.refresh(); advanceUntilIdle()
        assertNotNull(vm.uiState.value.progress)
        assertNull(vm.uiState.value.error)
        vm.clear()
    }

    @Test fun zeroOnlyHistoryDoesNotInventStrengthPoints() = runTest {
        val fake = Fake()
        fake.records["squat"] = listOf(record("zero", "squat", "2026-11-02", "0", 0))
        val vm = model(fake)
        vm.refresh(); advanceUntilIdle()
        assertTrue(strengthChartPoints(vm.uiState.value.progress!!).isEmpty())
        assertFalse(vm.uiState.value.isLoading)
        vm.clear()
    }

    @Test fun logoutCancelsRequestsAndDoesNotKeepAnotherAccountsData() = runTest {
        val fake = Fake().apply { delayedExercise = "squat" }
        val vm = model(fake)
        vm.refresh(); runCurrent()
        vm.clear(); fake.user = "other"
        advanceUntilIdle()
        assertEquals(StrengthProgressUiState(), vm.uiState.value)
        vm.activate("other")
        fake.delayedExercise = null
        fake.records.replaceAll { _, rows -> rows.map { it.copy(userId = "other") } }
        vm.refresh(); advanceUntilIdle()
        assertEquals("other", vm.uiState.value.progress!!.userId)
        assertTrue(strengthChartPoints(vm.uiState.value.progress!!).all { it.measurement.source.userId == "other" })
        vm.clear()
    }

    @Test fun refreshKeepsThePeriodAndClockChangeUsesTheNewDate() = runTest {
        var day = today
        val fake = Fake(); val vm = model(fake) { day }
        vm.refresh(); advanceUntilIdle()
        vm.selectExercise("press"); advanceUntilIdle()
        vm.selectPeriod(TrainingPeriod.MONTH); advanceUntilIdle()
        vm.refresh(); advanceUntilIdle()
        assertEquals("press", vm.uiState.value.selectedExerciseId)
        assertEquals(TrainingPeriod.MONTH, vm.uiState.value.period)
        day = day.plusDays(1)
        vm.onClockChanged(); advanceUntilIdle()
        assertEquals(day, vm.uiState.value.progress!!.throughInclusive)
        vm.clear()
    }

    @Test fun repetitionsDefaultToTheLowestDistinctEffectiveWeight() = runTest {
        val fake = Fake()
        fake.records["squat"] = listOf(
            record("first", "squat", "2026-11-02", "40", 8),
            record("best", "squat", "2026-11-05", "40.00", 10, 2),
            record("heavy", "squat", "2026-11-05", "50", 5),
            record("placeholder", "squat", "2026-11-06", "30", 0),
        )
        val vm = model(fake)
        vm.refresh(); advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(0, BigDecimal("40").compareTo(state.selectedWeightKg))
        assertEquals(listOf("40", "50"), state.progress!!.availableWeightsKg.map { it.stripTrailingZeros().toPlainString() })
        val point = repetitionsChartPoints(state.progress).single()
        assertEquals(10, point.measurement.value)
        assertEquals("best", point.measurement.source.set.id)
        assertEquals(2, point.measurement.source.set.number)
        vm.clear()
    }

    @Test fun weightChangesReuseCachedSeriesAndStrengthAndRejectStaleRepetitionPoints() = runTest {
        val fake = Fake(); val vm = model(fake)
        vm.refresh(); advanceUntilIdle()
        val before = vm.uiState.value.progress!!
        val stale = repetitionsChartPoints(before).first()
        vm.selectRepetitionsPoint(stale)
        vm.selectWeight(BigDecimal("90.00"))
        assertTrue(vm.uiState.value.isRepetitionsLoading)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.selectedRepetitionsPoint)
        advanceUntilIdle()
        val after = vm.uiState.value.progress!!
        assertEquals(3, repetitionsChartPoints(after).single().measurement.value)
        assertEquals("real", repetitionsChartPoints(after).single().measurement.source.set.id)
        assertSame(before.points.first().maximumLoad, after.points.first().maximumLoad)
        assertSame(before.points.first().estimatedOneRepMax, after.points.first().estimatedOneRepMax)
        vm.selectRepetitionsPoint(stale)
        assertNull(vm.uiState.value.selectedRepetitionsPoint)
        val unchanged = vm.uiState.value
        vm.selectWeight(BigDecimal("999")); vm.selectWeight(BigDecimal("90.0"))
        assertSame(unchanged, vm.uiState.value)
        assertEquals(1, fake.optionsCalls)
        assertEquals(listOf("squat"), fake.historyCalls)
        vm.clear()
    }

    @Test fun exerciseChangePreservesAnEquivalentWeightOrChoosesAValidAlternative() = runTest {
        val fake = Fake()
        fake.records["press"] = listOf(record("common", "press", "2026-11-09", "80.00", 8),
            record("lighter", "press", "2026-11-09", "30", 10))
        val vm = model(fake)
        vm.refresh(); advanceUntilIdle()
        vm.selectExercise("press"); advanceUntilIdle()
        assertEquals(0, BigDecimal("80").compareTo(vm.uiState.value.selectedWeightKg))
        assertEquals("common", repetitionsChartPoints(vm.uiState.value.progress!!).single().measurement.source.set.id)
        vm.selectWeight(BigDecimal("30")); advanceUntilIdle()
        vm.selectExercise("squat"); advanceUntilIdle()
        assertEquals(0, BigDecimal("80").compareTo(vm.uiState.value.selectedWeightKg))
        fake.records["squat"] = emptyList()
        vm.refresh(); advanceUntilIdle()
        assertNull(vm.uiState.value.selectedWeightKg)
        assertTrue(vm.uiState.value.progress!!.availableWeightsKg.isEmpty())
        assertTrue(repetitionsChartPoints(vm.uiState.value.progress!!).isEmpty())
        vm.clear()
    }

    @Test fun sharedPeriodUpdatesBothChartsAndRepetitionDetailsRemainExclusive() = runTest {
        val fake = Fake(); val vm = model(fake)
        vm.refresh(); advanceUntilIdle()
        val point = repetitionsChartPoints(vm.uiState.value.progress!!).first()
        vm.selectRepetitionsPoint(point)
        assertEquals(point, vm.uiState.value.selectedRepetitionsPoint)
        vm.selectPoint(strengthChartPoints(vm.uiState.value.progress!!).first())
        assertNull(vm.uiState.value.selectedRepetitionsPoint)
        vm.selectRepetitionsPoint(point)
        assertNull(vm.uiState.value.selectedPoint)
        vm.selectPeriod(TrainingPeriod.MONTH); advanceUntilIdle()
        val progress = vm.uiState.value.progress!!
        assertNull(vm.uiState.value.selectedRepetitionsPoint)
        assertEquals(TrainingPeriod.MONTH, progress.period)
        assertEquals(LocalDate.of(2026, 11, 1), repetitionsChartPoints(progress).single().periodStart)
        assertEquals("estimated", repetitionsChartPoints(progress).single().measurement.source.set.id)
        assertEquals(listOf("squat"), fake.historyCalls)
        vm.clear()
    }

    @Test fun rapidWeightPeriodAndExerciseChangesDoNotPublishOldSources() = runTest {
        val fake = Fake(); val vm = model(fake)
        vm.refresh(); advanceUntilIdle()
        vm.selectWeight(BigDecimal("90"))
        vm.selectPeriod(TrainingPeriod.MONTH)
        vm.selectExercise("press")
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.isRepetitionsLoading)
        assertFalse(state.isLoading)
        assertEquals(TrainingPeriod.MONTH, state.period)
        assertEquals(0, BigDecimal("30").compareTo(state.selectedWeightKg))
        assertTrue(repetitionsChartPoints(state.progress!!).all { it.measurement.source.exerciseId == "press" })
        vm.clear()
        assertEquals(StrengthProgressUiState(), vm.uiState.value)
    }

    @Test fun refreshKeepsTheChosenWeightAndFallsBackWhenItNoLongerExists() = runTest {
        val fake = Fake(); val vm = model(fake)
        vm.refresh(); advanceUntilIdle()
        vm.selectWeight(BigDecimal("90")); advanceUntilIdle()
        vm.refresh(); advanceUntilIdle()
        assertEquals(0, BigDecimal("90").compareTo(vm.uiState.value.selectedWeightKg))
        fake.records["squat"] = fake.records.getValue("squat").filter { it.set.id != "real" }
        vm.refresh(); advanceUntilIdle()
        assertEquals(0, BigDecimal("80").compareTo(vm.uiState.value.selectedWeightKg))
        vm.selectRepetitionsPoint(repetitionsChartPoints(vm.uiState.value.progress!!).first())
        vm.dismissRepetitionsPoint()
        assertNull(vm.uiState.value.selectedRepetitionsPoint)
        vm.clear()
    }
}
