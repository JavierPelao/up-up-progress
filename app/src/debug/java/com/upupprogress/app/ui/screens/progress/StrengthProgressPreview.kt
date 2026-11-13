package com.upupprogress.app.ui.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.model.*
import com.upupprogress.app.domain.training.TrainingProgressCalculator
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import java.time.LocalDate
import java.math.BigDecimal

// Fixtures exclusivos de previews de debug: no se conectan a repositorios ni al historial normal.
internal fun previewTrainingRecords(): List<ExerciseSetRecord> {
    fun row(id: String, date: String, kg: String, reps: Int, number: Int = 1) = ExerciseSetRecord(
        "preview", "session-$date", null, LocalDate.parse(date), "we-$date", "squat", "Sentadilla",
        "pending", RecordedTrainingSet(id, number, kg, reps),
    )
    return listOf(
        row("a", "2026-10-05", "60", 8), row("b", "2026-10-12", "65", 6),
        row("c", "2026-10-19", "70", 8), row("d", "2026-11-02", "80", 10),
        row("e", "2026-11-02", "90", 3, 2), row("f", "2026-11-09", "95", 1),
        row("g", "2026-11-09", "80", 12, 2),
        row("h", "2026-10-12", "60.00", 10, 2), row("i", "2026-10-19", "60", 9, 2),
        row("j", "2026-11-02", "60", 12, 3),
    )
}

@Composable
internal fun ProgressPreviewFrame(content: @Composable (Modifier) -> Unit) {
    UpUpProgressTheme {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Text("Vista previa · datos de ejemplo",
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp),
                style = MaterialTheme.typography.labelLarge)
            content(Modifier.weight(1f))
        }
    }
}

@Preview(name = "Fuerza · Semanal", showBackground = true, widthDp = 390, heightDp = 850)
@Preview(name = "Fuerza · Compacto", showBackground = true, widthDp = 320, heightDp = 740, fontScale = 1.3f)
@Composable
private fun StrengthProgressPreview() { PreviewStrengthContent(TrainingPeriod.WEEK) }

@Preview(name = "Fuerza · Mensual", showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun StrengthMonthlyPreview() { PreviewStrengthContent(TrainingPeriod.MONTH) }

@Composable
private fun PreviewStrengthContent(initialPeriod: TrainingPeriod) {
    val from = LocalDate.of(2026, 10, 5)
    val through = LocalDate.of(2026, 11, 12)
    val records = remember { previewTrainingRecords() }
    var period by remember { mutableStateOf(initialPeriod) }
    var weight by remember { mutableStateOf(BigDecimal("60")) }
    var selectedPoint by remember { mutableStateOf<StrengthChartPoint?>(null) }
    var repetitionPoint by remember { mutableStateOf<RepetitionsChartPoint?>(null) }
    val base = remember(period) { TrainingProgressCalculator.calculate(records, "preview", "squat", from, through, period) }
    val progress = remember(base, weight) { TrainingProgressCalculator.withSelectedWeight(base, weight) }
    ProgressPreviewFrame { modifier ->
        ProgressScreen(
            state = ProgressUiState(), onAnalyze = {}, onDismissResult = {}, modifier = modifier,
            strengthState = StrengthProgressUiState(
                exercises = listOf(RecordedExercise("squat", "Sentadilla", from)),
                selectedExerciseId = "squat", period = period,
                progress = progress, selectedPoint = selectedPoint,
                selectedWeightKg = weight, selectedRepetitionsPoint = repetitionPoint,
            ),
            onStrengthPeriodSelected = { period = it; selectedPoint = null; repetitionPoint = null },
            onStrengthPointSelected = { selectedPoint = it; repetitionPoint = null },
            onStrengthPointDismiss = { selectedPoint = null },
            onWeightSelected = { weight = it; selectedPoint = null; repetitionPoint = null },
            onRepetitionsPointSelected = { repetitionPoint = it; selectedPoint = null },
            onRepetitionsPointDismiss = { repetitionPoint = null },
        )
    }
}

@Preview(name = "Fuerza · Sin historial", showBackground = true, widthDp = 390, heightDp = 600)
@Composable
private fun StrengthEmptyPreview() {
    ProgressPreviewFrame { ProgressScreen(ProgressUiState(), {}, {}, modifier = it) }
}

@Preview(name = "Fuerza · Error", showBackground = true, widthDp = 390, heightDp = 600)
@Composable
private fun StrengthErrorPreview() {
    ProgressPreviewFrame {
        ProgressScreen(ProgressUiState(), {}, {}, modifier = it,
            strengthState = StrengthProgressUiState(error = "No se pudo cargar tu progreso. Revisa la conexión y vuelve a intentarlo."))
    }
}
