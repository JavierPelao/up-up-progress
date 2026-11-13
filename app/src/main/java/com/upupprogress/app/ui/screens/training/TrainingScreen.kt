package com.upupprogress.app.ui.screens.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.model.PlannedExercise
import com.upupprogress.app.data.model.RoutinePlan
import com.upupprogress.app.data.model.WorkoutSession
import com.upupprogress.app.ui.screens.nutrition.NutritionWeekSelector
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import java.time.Instant
import java.time.ZoneOffset
import java.time.LocalDate

private val RoutineReturnIcon = ImageVector.Builder("ReturnToWorkout", 24.dp, 24.dp, 24f, 24f).apply {
    path(
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(20f, 12f)
        horizontalLineTo(4f)
        moveTo(10f, 6f)
        lineTo(4f, 12f)
        lineTo(10f, 18f)
    }
}.build()

@Composable
fun TrainingScreen(
    state: TrainingUiState,
    onCreateRoutine: () -> Unit,
    onChooseRoutine: (String) -> Unit,
    onViewRoutine: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
    onRetrySelection: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleManagement: () -> Unit = {},
    onEditRoutine: (String) -> Unit = {},
    onArchiveRoutine: (String) -> Unit = {},
    onRetryArchive: () -> Unit = {},
) {
    val visibleSessions = state.sessions.filter { state.selectedDate == state.today || it.hasRecordedData }
    val returnSession = state.activeSession?.takeIf { state.changingRoutine && !state.isManagingRoutines }
    LazyColumn(
        modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!state.isManagingRoutines && !state.changingRoutine) item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                TrainingDateHeader(state.selectedDate, state.today, onDateSelected)
            }
        }
        if (state.isLoading || state.isBusy) item { LinearProgressIndicator(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) }
        state.error?.let { message -> item {
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            TextButton(onClick = onRefresh, enabled = !state.isBusy, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Actualizar") }
        } }
        if (state.selectionRequest != null) item {
            Text("Hay un cambio de rutina sin confirmar. Compruébalo antes de realizar otro cambio.", modifier = Modifier.padding(horizontal = 16.dp))
            Button(onClick = onRetrySelection, enabled = !state.isBusy, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Comprobar cambio") }
        }
        if (state.pendingArchiveId != null) item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("La eliminación no está confirmada. Comprueba el resultado antes de continuar.")
                Button(onClick = onRetryArchive, enabled = !state.isBusy) { Text("Comprobar eliminación") }
            }
        }
        if (state.pendingSeries) item {
            Text("Hay series pendientes de confirmar. Abre el entrenamiento para revisarlas.",
                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (state.selectedDate != state.today && !state.isLoading) {
            item { Text("Entrenamientos registrados", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp)) }
            if (visibleSessions.isEmpty()) item { Text("No hay entrenamientos registrados para esta fecha.", modifier = Modifier.padding(horizontal = 16.dp)) }
            visibleSessions.forEach { session -> item(key = "session:${session.id}") {
                Card(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(session.routineName, style = MaterialTheme.typography.titleMedium)
                        Text("${session.exercises.sumOf { it.sets.size }} series registradas")
                        OutlinedButton(onClick = { onOpenSession(session.id) }, enabled = !state.isBusy) {
                            Text("Ver historial")
                        }
                    }
                }
            } }
        }
        if (state.selectedDate == state.today && !state.isLoading && state.error == null) item {
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        returnSession?.let { session ->
                            IconButton(
                                onClick = { onOpenSession(session.id) },
                                enabled = !state.isBusy && state.selectionRequest == null && state.pendingArchiveId == null,
                                modifier = Modifier.size(48.dp),
                            ) { Icon(RoutineReturnIcon, contentDescription = "Volver al entrenamiento actual") }
                        }
                        Text(
                            if (state.isManagingRoutines) "Edita tus rutinas"
                            else if (state.changingRoutine) "Selecciona otra rutina para hoy"
                            else "Selecciona la rutina que realizarás hoy",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                        )
                        if (returnSession != null) Spacer(Modifier.width(48.dp))
                    }
                    Text(
                        when (state.routines.size) {
                            0 -> "Todavía no has creado una rutina."
                            1 -> "Tienes 1 rutina registrada"
                            else -> "Tienes ${state.routines.size} rutinas registradas"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    state.routines.forEach { routine ->
                        val card = TrainingRoutineUi(routine.id, routine.name, routine.description,
                            routine.exercises.size, routine.exercises.sumOf { it.plannedSets })
                        RoutineSelectionCard(card, state.canChooseRoutine,
                            onChoose = { onChooseRoutine(card.id) },
                            onView = { onViewRoutine(card.id) },
                            viewEnabled = !state.isBusy,
                            managing = state.isManagingRoutines, canManage = state.canManageRoutines,
                            onEdit = { onEditRoutine(card.id) }, onArchive = { onArchiveRoutine(card.id) },
                        )
                    }
                }
            }
        }
        if (state.selectedDate == state.today && !state.isManagingRoutines) item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onCreateRoutine, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = !state.isBusy && !state.isLoading && state.selectionRequest == null &&
                        state.pendingArchiveId == null && !state.pendingSeries,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = BorderStroke(1.dp, if (state.canManageRoutines) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant),
                ) { Text("+ Crear rutina") }
                if (state.routines.isNotEmpty()) FilledTonalButton(
                    onClick = onToggleManagement, enabled = state.canManageRoutines,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFF1F1F1),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = BorderStroke(1.dp, if (state.canManageRoutines) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant),
                ) { Text("Editar") }
            }
        }
        if (state.selectedDate == state.today && state.isManagingRoutines) item {
            Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                OutlinedButton(
                    onClick = onToggleManagement, enabled = state.canManageRoutines,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ),
                    border = BorderStroke(1.dp, if (state.canManageRoutines) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth(0.7f),
                ) { Text("Salir") }
            }
        }
    }
}

@Composable
private fun RoutineSelectionCard(
    card: TrainingRoutineUi,
    canChoose: Boolean,
    onChoose: () -> Unit,
    onView: () -> Unit,
    viewEnabled: Boolean,
    managing: Boolean,
    canManage: Boolean,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (managing || (canChoose && card.exerciseCount > 0)) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(card.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            card.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${card.exerciseCount} ${if (card.exerciseCount == 1) "ejercicio" else "ejercicios"} · " +
                    "${card.totalSets} ${if (card.totalSets == 1) "serie total" else "series totales"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (managing) {
                    OutlinedButton(
                        onClick = onEdit, enabled = canManage,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = BorderStroke(1.dp, if (canManage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant),
                    ) { Text("Editar") }
                    OutlinedIconButton(
                        onClick = onArchive, enabled = canManage, modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Icon(deleteTrainingItemIcon, contentDescription = "Eliminar ${card.name}") }
                } else {
                Button(
                    onClick = onChoose, enabled = canChoose && card.exerciseCount > 0,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Realizar rutina") }
                OutlinedButton(
                    onClick = onView, enabled = viewEnabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Ver") }
                }
            }
        }
    }
}

/** Conserva el selector semanal y el calendario existentes, también durante el entrenamiento. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainingDateHeader(selectedDate: LocalDate, today: LocalDate, onDateSelected: (LocalDate) -> Unit,
    showReturnToToday: Boolean = true) {
    var calendarOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NutritionWeekSelector(
            selectedDate = selectedDate, today = today,
            onDateSelected = onDateSelected,
            onOpenCalendar = { calendarOpen = true },
        )
        if (showReturnToToday && selectedDate != today) {
            TextButton(onClick = { onDateSelected(today) }) { Text("Volver a hoy") }
        }
    }
    if (calendarOpen) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 1900..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { calendarOpen = false },
            confirmButton = { TextButton(onClick = {
                picker.selectedDateMillis?.let {
                    onDateSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                calendarOpen = false
            }, enabled = picker.selectedDateMillis != null) { Text("Ver fecha") } },
            dismissButton = { TextButton(onClick = { calendarOpen = false }) { Text("Cancelar") } },
        ) { DatePicker(picker) }
    }
}

@Preview(name = "Rutinas", showBackground = true, widthDp = 390, heightDp = 900)
@Preview(name = "Rutinas compacto", showBackground = true, widthDp = 320, heightDp = 720, fontScale = 1.3f)
@Composable
private fun TrainingLibraryPreview() {
    val day = LocalDate.of(2026, 11, 11)
    val routines = listOf(
        RoutinePlan("legs", "Piernas", "Fuerza y estabilidad", listOf(
            PlannedExercise(name = "Sentadilla", plannedSets = 4, plannedReps = 10),
            PlannedExercise(name = "Prensa", plannedSets = 3, plannedReps = 12),
        )),
        RoutinePlan("torso", "Torso", "Pecho, espalda y brazos", listOf(
            PlannedExercise(name = "Press de banca", plannedSets = 3, plannedReps = 10),
            PlannedExercise(name = "Remo", plannedSets = 3, plannedReps = 12),
        )),
    )
    UpUpProgressTheme {
        TrainingScreen(
            TrainingUiState(selectedDate = day, today = day, routines = routines),
            onCreateRoutine = {}, onChooseRoutine = {}, onViewRoutine = {}, onOpenSession = {},
            onDateSelected = {}, onRefresh = {}, onRetrySelection = {},
        )
    }
}

@Preview(name = "Editar rutinas", showBackground = true, widthDp = 390, heightDp = 780)
@Preview(name = "Editar rutinas compacto", showBackground = true, widthDp = 320, heightDp = 720, fontScale = 1.3f)
@Composable
private fun RoutineManagementPreview() {
    val day = LocalDate.of(2026, 11, 11)
    val routine = RoutinePlan("preview", "Piernas", "Fuerza y estabilidad", listOf(
        PlannedExercise(name = "Sentadilla", plannedSets = 4, plannedReps = 10),
        PlannedExercise(name = "Prensa", plannedSets = 3, plannedReps = 12),
    ))
    UpUpProgressTheme {
        TrainingScreen(
            TrainingUiState(selectedDate = day, today = day, routines = listOf(routine), isManagingRoutines = true),
            onCreateRoutine = {}, onChooseRoutine = {}, onViewRoutine = {}, onOpenSession = {},
            onDateSelected = {}, onRefresh = {}, onRetrySelection = {},
        )
    }
}

@Preview(name = "Seleccionar otra rutina", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun ChangeRoutinePreview() {
    val day = LocalDate.of(2026, 11, 11)
    val routine = RoutinePlan("preview", "Piernas", "Fuerza y estabilidad", listOf(
        PlannedExercise(name = "Sentadilla", plannedSets = 4, plannedReps = 10),
    ))
    val session = WorkoutSession("session", day.toString(), routine.id, routine.name, emptyList())
    UpUpProgressTheme {
        TrainingScreen(
            TrainingUiState(selectedDate = day, today = day, routines = listOf(routine),
                changingRoutine = true, sessions = listOf(session), activeSessionId = session.id),
            onCreateRoutine = {}, onChooseRoutine = {}, onViewRoutine = {}, onOpenSession = {},
            onDateSelected = {}, onRefresh = {}, onRetrySelection = {},
        )
    }
}
