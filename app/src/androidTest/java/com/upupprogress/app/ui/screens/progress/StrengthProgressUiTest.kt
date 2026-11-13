package com.upupprogress.app.ui.screens.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.upupprogress.app.data.model.*
import com.upupprogress.app.domain.training.TrainingProgressCalculator
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI con fixtures controlados. No inicia MainActivity, Auth ni consultas a Supabase. */
class StrengthProgressUiTest {
    @get:Rule val compose = createComposeRule()
    private val from = LocalDate.of(2026, 11, 2)
    private val through = from.plusDays(6)

    private fun rows(exercise: String) = if (exercise == "squat") listOf(
        row("load", exercise, from, "70", 1),
        row("estimate", exercise, from.plusDays(3), "50", 12),
    ) else listOf(row("press", exercise, from, "20", 8))

    private fun row(id: String, exercise: String, date: LocalDate, weight: String, reps: Int) =
        ExerciseSetRecord("test", id, null, date, "we-$id", exercise,
            if (exercise == "squat") "Sentadilla" else "Press", "pending",
            RecordedTrainingSet(id, 1, weight, reps))

    private fun showSample() {
        compose.setContent {
            var exercise by remember { mutableStateOf("squat") }
            var period by remember { mutableStateOf(TrainingPeriod.WEEK) }
            var selected by remember { mutableStateOf<StrengthChartPoint?>(null) }
            UpUpProgressTheme {
                ProgressScreen(
                    ProgressUiState(), {}, {}, modifier = Modifier.fillMaxSize(),
                    strengthState = StrengthProgressUiState(
                        exercises = listOf(RecordedExercise("squat", "Sentadilla", from), RecordedExercise("press", "Press", from)),
                        selectedExerciseId = exercise, period = period,
                        progress = TrainingProgressCalculator.calculate(rows(exercise), "test", exercise, from, through, period),
                        selectedPoint = selected,
                    ),
                    onStrengthExerciseSelected = { exercise = it; selected = null },
                    onStrengthPeriodSelected = { period = it; selected = null },
                    onStrengthPointSelected = { selected = it },
                    onStrengthPointDismiss = { selected = null },
                )
            }
        }
    }

    @Test fun touchCoincidentPointsAndReadTheCorrectSourceForEachIndicator() {
        showSample()
        // Los dos puntos valen 70 kg. Con escala 0..80 se sitúan a 35 dp de un área de 208 dp.
        compose.onNodeWithTag("strength-period-$from").performScrollTo().performTouchInput {
            click(Offset(center.x, height * (35f / 208f)))
        }
        compose.onNodeWithText("Selecciona el indicador").assertIsDisplayed()
        compose.onNodeWithText("1RM estimado: 70 kg").performClick()
        compose.onNodeWithText("5 de noviembre de 2026").assertIsDisplayed()
        compose.onNodeWithText("50 kg").assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()
        // La primera selección usa un toque real; la segunda comprueba la acción de accesibilidad.
        compose.onNodeWithTag("strength-period-$from").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Selecciona el indicador").assertIsDisplayed()
        compose.onNodeWithText("Carga máxima real: 70 kg").performClick()
        compose.onNodeWithText("2 de noviembre de 2026").assertIsDisplayed()
        compose.onAllNodesWithText("70 kg").assertCountEquals(2)
    }

    @Test fun exerciseAndPeriodSelectorsUpdateTheDisplayedGraph() {
        showSample()
        compose.onNodeWithText("Mensual").performClick()
        compose.onNodeWithTag("strength-period-selector-MONTH").assertIsSelected()
        compose.onNodeWithTag("strength-period-2026-11-01").assertExists()
        compose.onNodeWithTag("strength-exercise-selector").performClick()
        compose.onNodeWithText("Press").performClick()
        compose.onNodeWithText("Press").assertIsDisplayed()
        compose.onNodeWithTag("strength-period-2026-11-01").assertExists()
    }

    @Test fun emptyStateDoesNotShowSyntheticPoints() {
        compose.setContent {
            UpUpProgressTheme { ProgressScreen(ProgressUiState(), {}, {}) }
        }
        compose.onNodeWithText("Aún no tienes ejercicios registrados.").assertIsDisplayed()
        compose.onNodeWithTag("strength-chart").assertDoesNotExist()
    }

    @Test fun loadingAndRetryAreVisibleWithoutCallingAnyBackend() {
        var retries = 0
        val state = mutableStateOf(StrengthProgressUiState(isLoading = true))
        compose.setContent {
            UpUpProgressTheme {
                ProgressScreen(ProgressUiState(), {}, {}, strengthState = state.value, onStrengthRefresh = { retries++ })
            }
        }
        compose.onNodeWithText("Cargando tu progreso…").assertIsDisplayed()
        compose.runOnIdle { state.value = StrengthProgressUiState(error = "Error de prueba") }
        compose.onNodeWithText("Error de prueba").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
