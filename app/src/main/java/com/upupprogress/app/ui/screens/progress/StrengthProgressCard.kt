package com.upupprogress.app.ui.screens.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.model.TrainingPeriod
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrengthProgressCard(
    state: StrengthProgressUiState,
    onExerciseSelected: (String) -> Unit,
    onPeriodSelected: (TrainingPeriod) -> Unit,
    onPointSelected: (StrengthChartPoint) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.exercises.firstOrNull { it.id == state.selectedExerciseId }
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Evolución de fuerza", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onRefresh, enabled = !state.isLoading) { Text("Actualizar") }
            }
            Text("Compara tus mejores cargas y la fuerza estimada del mismo ejercicio.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.exercises.isNotEmpty()) {
                Box {
                    OutlinedButton(
                        onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .testTag("strength-exercise-selector"), shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(selected?.name ?: "Selecciona un ejercicio", Modifier.weight(1f))
                        Text("▾")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                        modifier = Modifier.heightIn(max = 320.dp)) {
                        state.exercises.forEach { exercise ->
                            DropdownMenuItem(
                                text = { Text(exercise.name) },
                                onClick = { expanded = false; onExerciseSelected(exercise.id) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrainingPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = state.period == period, onClick = { onPeriodSelected(period) },
                            label = { Text(if (period == TrainingPeriod.WEEK) "Semanal" else "Mensual") },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .testTag("strength-period-selector-$period"),
                        )
                    }
                }
            }
            when {
                state.isLoading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Cargando tu progreso…", style = MaterialTheme.typography.bodyMedium)
                }
                state.error != null -> {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRefresh) { Text("Reintentar") }
                }
                state.exercises.isEmpty() -> {
                    Text("Aún no tienes ejercicios registrados.", style = MaterialTheme.typography.bodyLarge)
                    Text("Registra tus series en Rutinas para consultar aquí tu evolución.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    val progress = state.progress
                    val recorded = progress?.points?.count { it.maximumLoad != null || it.estimatedOneRepMax != null } ?: 0
                    if (progress == null || recorded == 0) {
                        Text("No hay series con repeticiones realizadas para este ejercicio.")
                        Text("Las series con 0 repeticiones no generan puntos de fuerza.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val rangeFormat = DateTimeFormatter.ofPattern("d MMM uuuu", ProgressLocale)
                        Text("${progress.fromInclusive.format(rangeFormat)} – ${progress.throughInclusive.format(rangeFormat)}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StrengthLegend(StrengthMetric.MAXIMUM_LOAD)
                            StrengthLegend(StrengthMetric.ESTIMATED_ONE_REP_MAX)
                        }
                        Text("Carga (kg)", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StrengthLineChart(progress, onPointSelected)
                        Text(if (state.period == TrainingPeriod.WEEK) "Semanas · inicio el lunes" else "Meses",
                            Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Toca un punto para ver la serie original. Desliza para recorrer los períodos.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (recorded == 1) Text(
                            "Hay datos en un solo período. Al registrar otro podrás comparar la evolución.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (progress.points.none { it.estimatedOneRepMax != null }) Text(
                            "No hay series válidas para estimar 1RM: se necesitan cargas positivas y de 1 a 12 repeticiones.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Text("El 1RM es una estimación, no una carga máxima levantada.",
                            style = MaterialTheme.typography.bodySmall, color = EstimatedStrengthColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun StrengthLegend(metric: StrengthMetric) {
    val color = if (metric == StrengthMetric.MAXIMUM_LOAD) MaterialTheme.colorScheme.primary else EstimatedStrengthColor
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(16.dp)) {
            if (metric == StrengthMetric.MAXIMUM_LOAD) drawCircle(color, 4.dp.toPx())
            else {
                val r = 6.dp.toPx()
                val diamond = Path().apply {
                    moveTo(center.x, center.y - r); lineTo(center.x + r, center.y)
                    lineTo(center.x, center.y + r); lineTo(center.x - r, center.y); close()
                }
                drawPath(diamond, color, style = Stroke(2.dp.toPx()))
            }
        }
        Text(metric.label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun StrengthPointDetails(point: StrengthChartPoint, onDismiss: () -> Unit) {
    val source = point.measurement.source
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(source.exerciseName) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProgressDetail("Fecha del registro", source.performedOn.format(
                    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", ProgressLocale)))
                ProgressDetail("Indicador", point.metric.label)
                ProgressDetail("Valor", "${formatProgressNumber(point.measurement.value)} kg")
                ProgressDetail("Peso utilizado", "${formatProgressNumber(BigDecimal(source.set.weightKg))} kg")
                ProgressDetail("Repeticiones realizadas", source.set.reps.toString())
                ProgressDetail("Número de serie", source.set.number.toString())
                if (point.metric == StrengthMetric.ESTIMATED_ONE_REP_MAX) Text(
                    "Estimación a partir de esta serie; no representa un levantamiento máximo realizado.",
                    style = MaterialTheme.typography.bodySmall, color = EstimatedStrengthColor)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
internal fun ProgressDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
