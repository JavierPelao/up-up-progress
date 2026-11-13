package com.upupprogress.app.ui.screens.progress

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.model.TrainingProgress

internal val EstimatedStrengthColor = Color(0xFF0F766E)
internal data class StrengthPlotPosition(val point: StrengthChartPoint, val offset: Offset)

/** Conserva el contrato de selección de fuerza usando la geometría compartida. */
internal fun hitStrengthPoints(
    positions: List<StrengthPlotPosition>, tap: Offset, radius: Float,
): List<StrengthChartPoint> = hitProgressPoints(positions.map { position ->
    ProgressPlotPosition(ProgressChartEntry(position.point.periodStart, 0, position.point.measurement.value, "", position.point),
        position.offset)
}, tap, radius)

@Composable
internal fun StrengthLineChart(
    progress: TrainingProgress,
    onPointSelected: (StrengthChartPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val points = remember(progress) { strengthChartPoints(progress) }
    val entries = remember(points) { points.map { point ->
        ProgressChartEntry(point.periodStart, point.metric.ordinal, point.measurement.value,
            "${point.metric.label}: ${formatProgressNumber(point.measurement.value)} kg", point)
    } }
    var candidates by remember(progress) { mutableStateOf(emptyList<StrengthChartPoint>()) }
    TrainingProgressLineChart(
        progress = progress, entries = entries,
        lines = listOf(ProgressChartLine(MaterialTheme.colorScheme.primary),
            ProgressChartLine(EstimatedStrengthColor, diamond = true, dashed = true)),
        tagPrefix = "strength", modifier = modifier,
        onPointsSelected = { found ->
            if (found.size == 1) onPointSelected(found.single())
            else if (found.isNotEmpty()) candidates = found
        },
    )
    if (candidates.isNotEmpty()) AlertDialog(
        onDismissRequest = { candidates = emptyList() },
        title = { Text("Selecciona el indicador") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { point ->
                    TextButton(
                        onClick = { candidates = emptyList(); onPointSelected(point) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("${point.metric.label}: ${formatProgressNumber(point.measurement.value)} kg") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { candidates = emptyList() }) { Text("Cancelar") } },
    )
}
