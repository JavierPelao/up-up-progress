package com.upupprogress.app.ui.screens.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.model.TrainingPeriod
import com.upupprogress.app.data.model.TrainingProgress
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

// No redondear las opciones: dos cargas distintas no deben mostrar la misma etiqueta.
internal fun formatProgressWeight(value: BigDecimal): String =
    formatProgressNumber(value, value.stripTrailingZeros().scale().coerceAtLeast(0))

@Composable
fun RepetitionsProgressCard(
    state: StrengthProgressUiState,
    onWeightSelected: (BigDecimal) -> Unit,
    onPointSelected: (RepetitionsChartPoint) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = state.progress
    val weights = progress?.availableWeightsKg.orEmpty()
    val weight = state.selectedWeightKg
    var expanded by remember(state.selectedExerciseId) { mutableStateOf(false) }
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Progreso de repeticiones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Tu mejor serie con una misma carga. Usa el ejercicio y período seleccionados arriba.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                state.isLoading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Cargando repeticiones…")
                }
                state.error != null -> {
                    Text("No se pudieron cargar las repeticiones.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRefresh) { Text("Reintentar repeticiones") }
                }
                state.exercises.isEmpty() -> Text(
                    "Registra series en Rutinas para consultar tus repeticiones por carga.",
                    style = MaterialTheme.typography.bodyMedium)
                weights.isEmpty() || progress == null || weight == null -> Text(
                    "No hay cargas con repeticiones registradas para este ejercicio.")
                else -> {
                    val exercise = state.exercises.firstOrNull { it.id == state.selectedExerciseId }
                    Text("${exercise?.name ?: "Ejercicio"} · ${if (state.period == TrainingPeriod.WEEK) "Semanal" else "Mensual"}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Carga seleccionada (kg)", style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("repetitions-weight-selector"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("${formatProgressWeight(weight)} kg", Modifier.weight(1f))
                            Text("▾")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                            modifier = Modifier.heightIn(max = 320.dp)) {
                            weights.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("${formatProgressWeight(option)} kg") },
                                    modifier = Modifier.testTag("repetitions-weight-option-${option.stripTrailingZeros().toPlainString()}"),
                                    onClick = { expanded = false; onWeightSelected(option) },
                                )
                            }
                        }
                    }
                    if (state.isRepetitionsLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Actualizando repeticiones…")
                    } else {
                        val points = remember(progress) { repetitionsChartPoints(progress) }
                        if (points.isEmpty()) Text("No hay series con esta carga en los períodos consultados.")
                        else {
                            Text("Repeticiones", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            RepetitionsLineChart(progress, onPointSelected)
                            Text(if (state.period == TrainingPeriod.WEEK) "Semanas · inicio el lunes" else "Meses",
                                Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Toca un punto para consultar su serie. Los períodos sin esta carga quedan sin punto.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (points.size == 1) Text("Hay datos en un solo período para esta carga.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RepetitionsLineChart(
    progress: TrainingProgress,
    onPointSelected: (RepetitionsChartPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val points = remember(progress) { repetitionsChartPoints(progress) }
    val entries = remember(points) { points.map { point ->
        ProgressChartEntry(point.periodStart, 0, BigDecimal.valueOf(point.measurement.value.toLong()),
            "${point.measurement.value} repeticiones con ${formatProgressWeight(point.selectedWeightKg)} kg", point)
    } }
    TrainingProgressLineChart(
        progress, entries, listOf(ProgressChartLine(MaterialTheme.colorScheme.primary)), "repetitions",
        onPointsSelected = { it.firstOrNull()?.let(onPointSelected) }, modifier = modifier,
        integersOnly = true, selectionKey = progress.selectedWeightKg?.stripTrailingZeros()?.toPlainString().orEmpty(),
    )
}

@Composable
fun RepetitionsPointDetails(point: RepetitionsChartPoint, onDismiss: () -> Unit) {
    val source = point.measurement.source
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeticiones · ${source.exerciseName}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProgressDetail("Fecha del registro", source.performedOn.format(
                    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", ProgressLocale)))
                ProgressDetail("Peso seleccionado", "${formatProgressWeight(point.selectedWeightKg)} kg")
                ProgressDetail("Repeticiones realizadas", source.set.reps.toString())
                ProgressDetail("Número de serie", source.set.number.toString())
                ProgressDetail("Sesión de entrenamiento", source.sessionId)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
