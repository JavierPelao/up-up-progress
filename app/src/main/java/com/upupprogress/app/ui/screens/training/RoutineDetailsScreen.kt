package com.upupprogress.app.ui.screens.training

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.model.PlannedExercise
import com.upupprogress.app.data.model.RoutinePlan
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import java.time.LocalDate

@Composable
fun RoutineDetailsScreen(
    state: TrainingUiState,
    onChooseRoutine: (String) -> Unit,
    onRetrySelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routine = state.viewedRoutine
    LazyColumn(
        modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.isLoading || state.isBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (state.selectionRequest != null) item {
            Text("No se ha podido confirmar el inicio. Comprueba el resultado antes de volver a intentarlo.")
            Button(onClick = onRetrySelection, enabled = !state.isBusy) { Text("Comprobar inicio") }
        }
        if (routine == null) item { Text("No se pudo encontrar esta rutina. Vuelve a la lista y actualízala.") }
        else {
            item {
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(routine.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        routine.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        Text(
                            "${routine.exercises.size} ejercicios · ${routine.exercises.sumOf { it.plannedSets }} series totales",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { Text("Ejercicios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            itemsIndexed(routine.exercises, key = { _, item -> item.id }) { index, exercise ->
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${index + 1}.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${exercise.plannedSets} series de ${exercise.plannedReps} repeticiones",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (state.selectedDate != state.today) item {
                Text("Vuelve a hoy para realizar esta rutina.")
            }
            item {
                Button(
                    onClick = { onChooseRoutine(routine.id) },
                    enabled = state.canChooseRoutine && routine.exercises.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Realizar rutina") }
            }
        }
    }
}

@Preview(name = "Detalle de rutina", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun RoutineDetailsPreview() {
    val day = LocalDate.of(2026, 11, 11)
    val routine = RoutinePlan("preview", "Piernas", "Fuerza y estabilidad", listOf(
        PlannedExercise(name = "Sentadilla", plannedSets = 4, plannedReps = 10),
        PlannedExercise(name = "Prensa", plannedSets = 3, plannedReps = 12),
        PlannedExercise(name = "Elevación de talones", plannedSets = 3, plannedReps = 15),
    ))
    UpUpProgressTheme {
        RoutineDetailsScreen(
            TrainingUiState(page = TrainingPage.ROUTINE_DETAILS, selectedDate = day, today = day,
                routines = listOf(routine), viewedRoutineId = routine.id),
            onChooseRoutine = {}, onRetrySelection = {},
        )
    }
}
