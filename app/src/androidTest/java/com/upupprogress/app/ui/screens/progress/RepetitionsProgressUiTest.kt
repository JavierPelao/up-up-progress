package com.upupprogress.app.ui.screens.progress

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.upupprogress.app.data.model.*
import com.upupprogress.app.data.repository.TrainingProgressGateway
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.*

/** Ambos gráficos con el ViewModel real y repositorio en memoria. Sin Auth, Supabase ni IA. */
class RepetitionsProgressUiTest {
    @get:Rule val compose = createComposeRule()
    private val from = LocalDate.of(2026, 10, 5)
    private val through = LocalDate.of(2026, 11, 1)

    private fun row(id: String, date: String, session: String, number: Int, kg: String, reps: Int, exercise: String = "bench") =
        ExerciseSetRecord("test", session, null, LocalDate.parse(date), "we-$session", exercise,
            if (exercise == "bench") "Press de banca" else "Remo", "pending", RecordedTrainingSet(id, number, kg, reps))

    private inner class Fake : TrainingProgressGateway {
        var historyCalls = 0
        val records = mapOf(
            "bench" to listOf(
                row("a1", "2026-10-05", "session-a", 1, "40", 6),
                row("a2", "2026-10-05", "session-a", 2, "40.00", 8),
                row("b2", "2026-10-08", "session-b", 2, "40.0", 10),
                row("heavy", "2026-10-05", "session-a", 3, "50", 20),
                row("c1", "2026-10-12", "session-c", 1, "50", 11),
                row("d1", "2026-10-19", "session-d", 1, "40", 9),
                row("e3", "2026-10-26", "session-e", 3, "40", 12),
            ),
            "row" to listOf(row("row1", "2026-10-05", "session-row", 1, "30", 7, "row")),
        )
        override fun currentUserId() = "test"
        override suspend fun loadRecordedExercises(throughInclusive: LocalDate) = listOf(
            RecordedExercise("bench", "Press de banca", from), RecordedExercise("row", "Remo", from),
        )
        override suspend fun loadExerciseHistory(exerciseId: String, fromInclusive: LocalDate, throughInclusive: LocalDate): List<ExerciseSetRecord> {
            historyCalls++
            return records[exerciseId].orEmpty()
        }
    }

    private val fake = Fake()
    private val model = StrengthProgressViewModel(fake, { through })

    @After fun clearModel() { compose.runOnIdle { model.clear() } }

    private fun showSample() {
        compose.setContent {
            val state by model.uiState.collectAsState()
            UpUpProgressTheme {
                ProgressScreen(
                    ProgressUiState(), {}, {}, strengthState = state, showRepetitionsChart = true,
                    onStrengthExerciseSelected = model::selectExercise, onStrengthPeriodSelected = model::selectPeriod,
                    onStrengthPointSelected = model::selectPoint, onStrengthPointDismiss = model::dismissPoint,
                    onStrengthRefresh = model::refresh, onWeightSelected = model::selectWeight,
                    onRepetitionsPointSelected = model::selectRepetitionsPoint,
                    onRepetitionsPointDismiss = model::dismissRepetitionsPoint,
                )
            }
        }
        compose.runOnIdle { model.activate("test"); model.refresh() }
        awaitProgress()
    }

    private fun awaitProgress() {
        compose.waitUntil(10_000) {
            model.uiState.value.let { it.progress != null && !it.isLoading && !it.isRepetitionsLoading }
        }
        compose.waitForIdle()
    }

    private fun showCard(index: Int) { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index) }

    @Test fun pointTouchShowsItsOriginalSeriesAndEmptyWeightPeriodsHaveNoPoint() {
        showSample(); showCard(1)
        compose.onNodeWithTag("repetitions-period-2026-10-12").assertDoesNotExist()
        // El mejor punto es 12 reps; la escala entera 0..15 lo sitúa a 48,8 dp del área de 208 dp.
        compose.onNodeWithTag("repetitions-period-2026-10-26").performScrollTo().performTouchInput {
            click(Offset(center.x, height * (48.8f / 208f)))
        }
        compose.onNodeWithText("Repeticiones · Press de banca").assertIsDisplayed()
        compose.onNodeWithText("26 de octubre de 2026").assertIsDisplayed()
        compose.onNodeWithText("Peso seleccionado").assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("session-e").assertIsDisplayed()
        compose.runOnIdle { assertEquals("e3", model.uiState.value.selectedRepetitionsPoint!!.measurement.source.set.id) }
        compose.onNodeWithText("Cerrar").performClick()
        compose.runOnIdle { assertNull(model.uiState.value.selectedRepetitionsPoint) }
    }

    @Test fun weightSelectorIsUniqueAndDoesNotReloadHistoryOrChangeStrength() {
        showSample()
        val strength = model.uiState.value.progress!!.points.first().maximumLoad
        showCard(1)
        compose.onNodeWithTag("repetitions-weight-selector").performClick()
        compose.onNodeWithTag("repetitions-weight-option-40").assertExists()
        compose.onAllNodesWithText("40 kg").assertCountEquals(2) // Botón y única opción equivalente.
        compose.onNodeWithTag("repetitions-weight-option-50").performClick()
        awaitProgress()
        compose.onNodeWithTag("repetitions-period-2026-10-12").assertExists()
        compose.runOnIdle {
            assertEquals(1, fake.historyCalls)
            assertSame(strength, model.uiState.value.progress!!.points.first().maximumLoad)
            assertEquals(20, repetitionsChartPoints(model.uiState.value.progress!!).first().measurement.value)
        }
        showCard(0)
        compose.onNodeWithTag("strength-chart").assertExists()
    }

    @Test fun globalExerciseAndMonthlySelectorsUpdateBothChartsAndTheWeightOptions() {
        showSample()
        compose.onNodeWithText("Mensual").performClick()
        awaitProgress()
        compose.onNodeWithTag("strength-period-2026-10-01").assertExists()
        showCard(1)
        compose.onNodeWithTag("repetitions-period-2026-10-01").assertExists()
        compose.onNodeWithTag("repetitions-period-2026-10-26").assertDoesNotExist()
        showCard(0)
        compose.onNodeWithTag("strength-exercise-selector").performClick()
        compose.onNodeWithText("Remo").performClick()
        awaitProgress(); showCard(1)
        compose.onNodeWithText("30 kg").assertIsDisplayed()
        compose.onNodeWithTag("repetitions-weight-selector").performClick()
        compose.onNodeWithTag("repetitions-weight-option-30").assertExists()
        compose.onNodeWithTag("repetitions-weight-option-40").assertDoesNotExist()
        compose.onNodeWithTag("repetitions-weight-option-30").performClick()
        compose.runOnIdle {
            assertEquals(TrainingPeriod.MONTH, model.uiState.value.period)
            assertEquals(0, BigDecimal("30").compareTo(model.uiState.value.selectedWeightKg))
            assertEquals("session-row", repetitionsChartPoints(model.uiState.value.progress!!).single().measurement.source.sessionId)
        }
    }

    @Test fun secondCardShowsLoadingRetryAndEmptyStatesWithoutAnyBackend() {
        val state = mutableStateOf(StrengthProgressUiState(isLoading = true))
        var retries = 0
        compose.setContent {
            UpUpProgressTheme {
                ProgressScreen(ProgressUiState(), {}, {}, strengthState = state.value,
                    showRepetitionsChart = true, onStrengthRefresh = { retries++ })
            }
        }
        showCard(1)
        compose.onNodeWithText("Cargando repeticiones…").assertIsDisplayed()
        compose.runOnIdle { state.value = StrengthProgressUiState(error = "Error de prueba") }
        showCard(1)
        compose.onNodeWithText("No se pudieron cargar las repeticiones.").assertIsDisplayed()
        compose.onNodeWithText("Reintentar repeticiones").performClick()
        compose.runOnIdle { assertEquals(1, retries); state.value = StrengthProgressUiState() }
        showCard(1)
        compose.onNodeWithText("Registra series en Rutinas para consultar tus repeticiones por carga.").assertIsDisplayed()
        compose.onNodeWithTag("repetitions-chart").assertDoesNotExist()
    }
}
