package com.upupprogress.app.ui.screens.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.model.RecordedExercise
import com.upupprogress.app.data.model.TrainingPeriod
import com.upupprogress.app.domain.training.TrainingProgressCalculator
import java.math.BigDecimal
import java.time.LocalDate

@Preview(name = "Repeticiones · Semanal", showBackground = true, widthDp = 390, heightDp = 850)
@Preview(name = "Repeticiones · Compacto", showBackground = true, widthDp = 320, heightDp = 740, fontScale = 1.3f)
@Composable
private fun RepetitionsProgressPreview() { PreviewRepetitionsContent(TrainingPeriod.WEEK) }

@Preview(name = "Repeticiones · Mensual", showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun RepetitionsMonthlyPreview() { PreviewRepetitionsContent(TrainingPeriod.MONTH) }

@Composable
private fun PreviewRepetitionsContent(period: TrainingPeriod) {
    val from = LocalDate.of(2026, 10, 5)
    val through = LocalDate.of(2026, 11, 12)
    var weight by remember { mutableStateOf(BigDecimal("60")) }
    var point by remember { mutableStateOf<RepetitionsChartPoint?>(null) }
    val base = remember(period) { TrainingProgressCalculator.calculate(
        previewTrainingRecords(), "preview", "squat", from, through, period) }
    val progress = remember(base, weight) { TrainingProgressCalculator.withSelectedWeight(base, weight) }
    ProgressPreviewFrame { modifier ->
        Column(modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            RepetitionsProgressCard(
                StrengthProgressUiState(
                    exercises = listOf(RecordedExercise("squat", "Sentadilla", from)),
                    selectedExerciseId = "squat", period = period, progress = progress, selectedWeightKg = weight,
                ),
                onWeightSelected = { weight = it; point = null }, onPointSelected = { point = it }, onRefresh = {},
            )
        }
    }
    point?.let { RepetitionsPointDetails(it) { point = null } }
}
