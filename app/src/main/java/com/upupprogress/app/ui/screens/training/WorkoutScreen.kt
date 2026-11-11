package com.upupprogress.app.ui.screens.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import com.upupprogress.app.data.model.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WorkoutScreen(
    state: TrainingUiState,
    onChangeRoutine: () -> Unit,
    onEditSeries: (String, Int, String?, String?) -> Unit,
    onRetrySeries: (String, Int) -> Unit,
    onDiscardSeriesEdit: (String, Int) -> Unit,
    onMarkExercise: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onDateSelected: (LocalDate) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val session = state.activeSession
    val editable = state.selectedDate == state.today && !state.isBusy && state.selectionRequest == null
    LazyColumn(
        modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TrainingDateHeader(state.selectedDate, state.today, onDateSelected) }
        if (state.isLoading || state.isBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (session == null && !state.isLoading) item {
            Text("Actualiza la lista para consultar el entrenamiento.")
            TextButton(onClick = onRefresh, enabled = !state.isBusy) { Text("Actualizar") }
        }
        state.error?.let { item {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRefresh, enabled = !state.isBusy && !state.pendingSeries) { Text("Actualizar") }
        } }
        if (session != null) {
            item {
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val day = state.selectedDate.dayOfWeek
                            .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-CL"))
                            .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-CL")) }
                        Text("$day · ${session.routineName}", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        session.routineDescription?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                if (state.selectedDate == state.today) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(
                            onClick = onChangeRoutine, enabled = editable && !state.pendingSeries,
                        ) { Text("Cambiar rutina") }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state.selectedDate == state.today) "Ejercicios del día" else "Ejercicios registrados",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${session.exercises.size} ${if (session.exercises.size == 1) "ejercicio" else "ejercicios"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            session.exercises.forEach { exercise -> item(key = exercise.id) {
                WorkoutExerciseCard(
                    exercise, state, editable, onEditSeries, onRetrySeries, onDiscardSeriesEdit, onMarkExercise,
                )
            } }
        }
    }
}

@Composable
private fun WorkoutExerciseCard(
    exercise: WorkoutExercise,
    state: TrainingUiState,
    editable: Boolean,
    onEditSeries: (String, Int, String?, String?) -> Unit,
    onRetrySeries: (String, Int) -> Unit,
    onDiscardSeriesEdit: (String, Int) -> Unit,
    onMarkExercise: (String, String) -> Unit,
) {
    val completedColor = Color(0xFF2E7D32)
    val skippedColor = MaterialTheme.colorScheme.error
    var confirmCompletion by rememberSaveable(exercise.id) { mutableStateOf(false) }
    val missingSeries = (1..exercise.plannedSets).count { n -> exercise.sets.none { it.number == n } }
    if (confirmCompletion) {
        AlertDialog(
            onDismissRequest = { confirmCompletion = false },
            title = { Text("¿Completar el ejercicio?") },
            text = {
                Text(
                    "Te ${if (missingSeries == 1) "queda 1 serie" else "quedan $missingSeries series"} sin registrar en " +
                        "«${exercise.name}». Si continúas, se guardarán con 0 repeticiones y 0 kg. " +
                        "Las series ya registradas se conservarán.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editable && !state.pendingSeries,
                    onClick = {
                        confirmCompletion = false
                        onMarkExercise(exercise.id, "completed")
                    },
                ) { Text("Completar ejercicio") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCompletion = false }) { Text("Cancelar") }
            },
        )
    }
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(38.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(exercise.position.toString(), color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        exercise.name.trimStart().replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-CL")) },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    )
                    if (exercise.plannedReps > 0) Text(
                        "${exercise.plannedSets} series de ${exercise.plannedReps} repeticiones",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (state.selectedDate == state.today) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WorkoutStatusButton(
                            symbol = "✓", description = if (exercise.completionStatus == "completed")
                                "Desmarcar completado: ${exercise.name}" else "Completar ${exercise.name}",
                            selected = exercise.completionStatus == "completed", color = completedColor,
                            enabled = editable && !state.pendingSeries && exercise.plannedSets > 0,
                            onClick = {
                                if (exercise.completionStatus != "completed" && missingSeries > 0) confirmCompletion = true
                                else onMarkExercise(exercise.id, "completed")
                            },
                        )
                        WorkoutStatusButton(
                            symbol = "×", description = if (exercise.completionStatus == "skipped")
                                "Desmarcar omitido: ${exercise.name}" else "Omitir ${exercise.name}",
                            selected = exercise.completionStatus == "skipped", color = skippedColor,
                            enabled = editable && !state.pendingSeries,
                            onClick = { onMarkExercise(exercise.id, "skipped") },
                        )
                    }
                    if (exercise.completionStatus != "pending") {
                        val statusColor = if (exercise.completionStatus == "completed") completedColor else skippedColor
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusColor.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, statusColor),
                        ) {
                            Text(
                                if (exercise.completionStatus == "completed") "Completado" else "Omitido",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall, color = statusColor,
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val numbers = if (editable) (1..exercise.plannedSets).toList() else exercise.sets.map { it.number }.sorted()
                numbers.forEach { number ->
                    val recorded = exercise.sets.firstOrNull { it.number == number }
                    val input = state.inputs["${exercise.id}:$number"] ?: TrainingSeriesInput(
                        recorded?.weightKg.orEmpty(), recorded?.reps?.toString().orEmpty(),
                    )
                    Column(Modifier.width(104.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${number}ª serie", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TrainingNumberField(
                                value = input.weight,
                                label = "kg", description = "Peso en kg, serie $number, ${exercise.name}",
                                enabled = editable && !input.saving && !input.uncertain,
                                keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f),
                                onValueChange = { onEditSeries(exercise.id, number, it, null) },
                            )
                            TrainingNumberField(
                                value = input.reps,
                                label = "reps", description = "Repeticiones, serie $number, ${exercise.name}",
                                enabled = editable && !input.saving && !input.uncertain,
                                keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                                onValueChange = { onEditSeries(exercise.id, number, null, it) },
                            )
                        }
                        if (input.saving || input.dirty) Text(
                            if (input.saving) "Guardando…" else "Sin guardar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        input.error?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            if (editable) TextButton(onClick = { onRetrySeries(exercise.id, number) }) { Text("Reintentar") }
                        }
                        if (editable && input.dirty && !input.saving && !input.uncertain) TextButton(
                            onClick = { onDiscardSeriesEdit(exercise.id, number) },
                        ) { Text("Descartar edición", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            if (!editable && exercise.sets.isEmpty()) Text("Sin series registradas.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkoutStatusButton(
    symbol: String,
    description: String,
    selected: Boolean,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedIconButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.size(40.dp).semantics {
            contentDescription = description
            this.selected = selected
        },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = if (selected) color else Color.Transparent,
            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledContainerColor = if (selected) color else Color.Transparent,
            disabledContentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrainingNumberField(
    value: String, label: String, description: String, enabled: Boolean,
    keyboardType: KeyboardType, modifier: Modifier, onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value, onValueChange = onValueChange, enabled = enabled, singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.heightIn(min = 56.dp)
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.heightIn(min = 24.dp)) {
                    if (value.isEmpty()) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Preview(name = "Entrenamiento", showBackground = true, widthDp = 390, heightDp = 850)
@Preview(name = "Entrenamiento compacto", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun WorkoutPreview() {
    val date = LocalDate.of(2026, 11, 10)
    val session = WorkoutSession("preview", date.toString(), "routine", "Piernas", listOf(
        WorkoutExercise("first", "squat", "Sentadilla", 1, 3, 10,
            completionStatus = "completed",
            sets = (1..3).map { RecordedTrainingSet("set-$it", it, "20", 10) }),
        WorkoutExercise("second", "legpress", "Prensa", 2, 3, 12, completionStatus = "skipped"),
    ), routineDescription = "Trabajo de piernas y glúteos. Mantener una técnica controlada.")
    UpUpProgressTheme {
        WorkoutScreen(
            TrainingUiState(page = TrainingPage.WORKOUT, selectedDate = date, today = date,
                sessions = listOf(session), activeSessionId = session.id),
            onChangeRoutine = {}, onEditSeries = { _, _, _, _ -> },
            onRetrySeries = { _, _ -> }, onDiscardSeriesEdit = { _, _ -> }, onMarkExercise = { _, _ -> },
        )
    }
}
