package com.upupprogress.app.ui.screens.training

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import com.upupprogress.app.data.model.*

@Composable
fun CreateRoutineScreen(
    draft: RoutineDraft,
    catalog: List<ExerciseCatalogItem>,
    isSaving: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPutExercise: (PlannedExercise) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    editingLocked: Boolean = false,
    isEditingRoutine: Boolean = false,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var exerciseDialog by rememberSaveable { mutableStateOf(false) }
    var removingId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val dragScope = rememberCoroutineScope()
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { autoScrollJob?.cancel() } }

    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding(),
        state = listState, contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(if (isEditingRoutine) "Modifica tu rutina. Los entrenamientos ya iniciados conservarán sus ejercicios y registros."
                else "Elige tus ejercicios y define sus series, repeticiones y orden.",
                style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedTextField(
                value = draft.name, onValueChange = onNameChange, enabled = !isSaving && !editingLocked,
                label = { Text("Nombre del entrenamiento") }, placeholder = { Text("Ej.: Piernas") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = draft.description, onValueChange = onDescriptionChange, enabled = !isSaving && !editingLocked,
                label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 5,
            )
        }
        item { Text("Ejercicios", style = MaterialTheme.typography.titleLarge) }
        itemsIndexed(draft.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
            Card(
                modifier = Modifier.fillMaxWidth()
                    .semantics {
                        customActions = listOfNotNull(
                            if (index > 0) CustomAccessibilityAction("Subir ejercicio") {
                                if (!isSaving && !editingLocked) onMoveExercise(exercise.id, draft.exercises[index - 1].id)
                                !isSaving && !editingLocked
                            } else null,
                            if (index < draft.exercises.lastIndex) CustomAccessibilityAction("Bajar ejercicio") {
                                if (!isSaving && !editingLocked) onMoveExercise(exercise.id, draft.exercises[index + 1].id)
                                !isSaving && !editingLocked
                            } else null,
                        )
                    }
                    .pointerInput(exercise.id, isSaving, editingLocked) {
                        if (!isSaving && !editingLocked) detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                draggingId = exercise.id
                                dragY = (listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == exercise.id }?.offset ?: 0) + offset.y
                                autoScrollJob?.cancel()
                                autoScrollJob = dragScope.launch {
                                    while (isActive && draggingId == exercise.id) {
                                        withFrameNanos { }
                                        val info = listState.layoutInfo
                                        val edge = 48.dp.toPx()
                                        val delta = when {
                                            dragY < info.viewportStartOffset + edge -> -12f
                                            dragY > info.viewportEndOffset - edge -> 12f
                                            else -> 0f
                                        }
                                        if (delta != 0f) {
                                            listState.scrollBy(delta)
                                            val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                                it.key != exercise.id && dragY in it.offset.toFloat()..(it.offset + it.size).toFloat()
                                            }?.key as? String
                                            if (target != null && draft.exercises.any { it.id == target }) onMoveExercise(exercise.id, target)
                                        }
                                    }
                                }
                            },
                            onDragEnd = { draggingId = null; autoScrollJob?.cancel() },
                            onDragCancel = { draggingId = null; autoScrollJob?.cancel() },
                            onDrag = { change, amount ->
                                change.consume()
                                dragY += amount.y
                                val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    it.key != exercise.id && dragY in it.offset.toFloat()..(it.offset + it.size).toFloat()
                                }?.key as? String
                                if (target != null && draft.exercises.any { it.id == target }) {
                                    onMoveExercise(exercise.id, target)
                                }
                            },
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (draggingId == exercise.id) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Row(
                        Modifier.weight(1f).padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("${index + 1}.", style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            Text("${exercise.plannedSets} series de ${exercise.plannedReps} repeticiones")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedIconButton(
                            onClick = { editingId = exercise.id; exerciseDialog = true },
                            enabled = !isSaving && !editingLocked,
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, if (!isSaving && !editingLocked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Icon(editExerciseIcon, contentDescription = "Editar ${exercise.name}")
                        }
                        IconButton(
                            onClick = { removingId = exercise.id },
                            enabled = !isSaving && !editingLocked,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(deleteTrainingItemIcon, contentDescription = "Quitar ${exercise.name}")
                        }
                    }
                }
            }
        }
        if (draft.exercises.size >= 2) item {
            Text(
                "Puede cambiar el orden de los ejercicios seleccionados arrastrando el ejercicio con el dedo.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            OutlinedButton(
                onClick = { editingId = null; exerciseDialog = true },
                enabled = !isSaving && !editingLocked && draft.exercises.size < TrainingInputLimits.MAX_EXERCISES,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("+ Añadir ejercicio") }
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(
                onClick = onSave, enabled = draft.isValid && !isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (isSaving) "Guardando…" else if (isEditingRoutine) "Guardar cambios" else "Guardar rutina") }
        }
        item { TextButton(onClick = onCancel, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") } }
    }

    if (exerciseDialog) {
        key(editingId) {
            RoutineExerciseDialog(
                initial = draft.exercises.firstOrNull { it.id == editingId }, catalog = catalog,
                onCancel = { exerciseDialog = false },
                onAccept = { onPutExercise(it); exerciseDialog = false },
            )
        }
    }
    removingId?.let { id ->
        val exercise = draft.exercises.firstOrNull { it.id == id }
        if (exercise != null) AlertDialog(
            onDismissRequest = { removingId = null },
            title = { Text("Quitar ejercicio") },
            text = { Text("¿Realmente desea quitar el ejercicio ${exercise.name}?") },
            confirmButton = { TextButton(onClick = { onRemoveExercise(id); removingId = null }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { removingId = null }) { Text("Cancelar") } },
        )
    }
}

private fun exerciseActionIcon(name: String, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) { draw() }
    }.build()

private val editExerciseIcon = exerciseActionIcon("EditExercise") {
    moveTo(4f, 16f)
    lineTo(15f, 5f)
    lineTo(19f, 9f)
    lineTo(8f, 20f)
    lineTo(4f, 20f)
    close()
    moveTo(13f, 7f)
    lineTo(17f, 11f)
}

internal val deleteTrainingItemIcon = exerciseActionIcon("DeleteExercise") {
    moveTo(4f, 6f)
    horizontalLineTo(20f)
    moveTo(9f, 6f)
    verticalLineTo(3f)
    horizontalLineTo(15f)
    verticalLineTo(6f)
    moveTo(6f, 6f)
    lineTo(7f, 21f)
    horizontalLineTo(17f)
    lineTo(18f, 6f)
    moveTo(10f, 10f)
    verticalLineTo(17f)
    moveTo(14f, 10f)
    verticalLineTo(17f)
}

@Composable
private fun RoutineExerciseDialog(
    initial: PlannedExercise?,
    catalog: List<ExerciseCatalogItem>,
    onCancel: () -> Unit,
    onAccept: (PlannedExercise) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var sets by rememberSaveable { mutableStateOf(initial?.plannedSets?.toString().orEmpty()) }
    var reps by rememberSaveable { mutableStateOf(initial?.plannedReps?.toString().orEmpty()) }
    val setsNumber = sets.toIntOrNull()
    val repsNumber = reps.toIntOrNull()
    val matches = catalog.filter { name.isNotBlank() && it.name.contains(name.trim(), ignoreCase = true) }.take(4)
    val valid = name.trim().length in 1..TrainingInputLimits.MAX_NAME &&
        setsNumber != null && setsNumber in 1..TrainingInputLimits.MAX_SETS &&
        repsNumber != null && repsNumber in 1..TrainingInputLimits.MAX_REPS
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (initial == null) "Añadir ejercicio" else "Editar ejercicio") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    name, { if (it.length <= TrainingInputLimits.MAX_NAME) name = it },
                    label = { Text("Nombre del ejercicio") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                matches.forEach { match ->
                    TextButton(onClick = { name = match.name }, modifier = Modifier.fillMaxWidth()) { Text(match.name) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        sets, { if (it.length <= 2 && it.all(Char::isDigit)) sets = it },
                        label = { Text("Series") }, supportingText = { Text("1–${TrainingInputLimits.MAX_SETS}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true,
                    )
                    OutlinedTextField(
                        reps, { if (it.length <= 4 && it.all(Char::isDigit)) reps = it },
                        label = { Text("Repeticiones") }, supportingText = { Text("1–${TrainingInputLimits.MAX_REPS}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val matched = catalog.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
                onAccept(PlannedExercise(
                    id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                    exerciseId = matched?.id,
                    name = matched?.name ?: name.trim(),
                    plannedSets = requireNotNull(setsNumber), plannedReps = requireNotNull(repsNumber),
                ))
            }) { Text(if (initial == null) "Agregar" else "Guardar cambios") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
    )
}

@Preview(name = "Crear rutina", showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun CreateRoutinePreview() {
    UpUpProgressTheme {
        CreateRoutineScreen(
            draft = RoutineDraft(
                id = "preview", name = "Pecho y brazos", description = "Rutina de ejemplo",
                exercises = listOf(
                    PlannedExercise(id = "one", name = "Press de banca", plannedSets = 3, plannedReps = 10),
                    PlannedExercise(id = "two", name = "Press inclinado", plannedSets = 3, plannedReps = 12),
                ),
            ),
            catalog = emptyList(), isSaving = false, error = null,
            onNameChange = {}, onDescriptionChange = {}, onPutExercise = {},
            onRemoveExercise = {}, onMoveExercise = { _, _ -> }, onSave = {}, onCancel = {},
        )
    }
}
