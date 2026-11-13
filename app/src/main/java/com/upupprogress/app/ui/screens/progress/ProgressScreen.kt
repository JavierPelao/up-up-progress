package com.upupprogress.app.ui.screens.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import com.upupprogress.app.data.model.TrainingPeriod
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

@Composable
fun ProgressScreen(
    state: ProgressUiState,
    onAnalyze: () -> Unit,
    onDismissResult: () -> Unit,
    modifier: Modifier = Modifier,
    strengthState: StrengthProgressUiState = StrengthProgressUiState(),
    onStrengthExerciseSelected: (String) -> Unit = {},
    onStrengthPeriodSelected: (TrainingPeriod) -> Unit = {},
    onStrengthPointSelected: (StrengthChartPoint) -> Unit = {},
    onStrengthPointDismiss: () -> Unit = {},
    onStrengthRefresh: () -> Unit = {},
    onWeightSelected: (BigDecimal) -> Unit = {},
    onRepetitionsPointSelected: (RepetitionsChartPoint) -> Unit = {},
    onRepetitionsPointDismiss: () -> Unit = {},
    showRepetitionsChart: Boolean = false, // Oculto temporalmente hasta revisar su presentación.
) {
    LazyColumn(
        modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            StrengthProgressCard(
                strengthState, onStrengthExerciseSelected, onStrengthPeriodSelected,
                onStrengthPointSelected, onStrengthRefresh, Modifier.widthIn(max = 800.dp),
            )
        }
        if (showRepetitionsChart) item {
            RepetitionsProgressCard(
                strengthState, onWeightSelected, onRepetitionsPointSelected, onStrengthRefresh,
                Modifier.widthIn(max = 800.dp),
            )
        }
        item {
            Button(
                onClick = onAnalyze,
                enabled = !state.isLoading,
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (state.isLoading) "Analizando…" else "Analizar mi progreso con IA")
            }
        }
    }
    strengthState.selectedPoint?.let { StrengthPointDetails(it, onStrengthPointDismiss) }

    if (showRepetitionsChart) {
        strengthState.selectedRepetitionsPoint?.let { RepetitionsPointDetails(it, onRepetitionsPointDismiss) }
    }

    if (state.dialogVisible && strengthState.selectedPoint == null &&
        (!showRepetitionsChart || strengthState.selectedRepetitionsPoint == null)) {
        val analysis = state.analysis
        AlertDialog(
            onDismissRequest = onDismissResult,
            title = {
                Text(
                    when {
                        state.error != null -> "No se pudo completar el análisis"
                        analysis?.status == "no_data" -> "Registros insuficientes"
                        else -> "Análisis de progreso"
                    },
                )
            },
            text = {
                SelectionContainer {
                    Column(
                        modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.error?.let { Text(it) }
                        analysis?.let {
                            Text("Resumen de tus registros", style = MaterialTheme.typography.titleSmall)
                            Text(it.summary)
                            if (it.suggestions.isNotEmpty()) {
                                Text(
                                    if (it.generatedByAi) "Sugerencias de IA" else "Para obtener un análisis",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                it.suggestions.forEachIndexed { index, suggestion ->
                                    Text("${index + 1}. $suggestion")
                                }
                            }
                            it.quota.remaining?.let { remaining ->
                                Text(
                                    "Análisis restantes hoy: $remaining de ${it.quota.limit}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissResult) { Text("Cerrar") }
            },
        )
    }
}
