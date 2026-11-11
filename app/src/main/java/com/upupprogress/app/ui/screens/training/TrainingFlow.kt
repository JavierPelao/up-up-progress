package com.upupprogress.app.ui.screens.training

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TrainingFlow(state: TrainingUiState, viewModel: TrainingViewModel, modifier: Modifier = Modifier) {
    when (state.page) {
        TrainingPage.CREATE -> CreateRoutineScreen(
            draft = state.draft, catalog = state.catalog, isSaving = state.isBusy,
            editingLocked = state.draftLocked, isEditingRoutine = state.editingRoutineId != null, error = state.error,
            onNameChange = viewModel::changeName, onDescriptionChange = viewModel::changeDescription,
            onPutExercise = viewModel::putExercise, onRemoveExercise = viewModel::removeExercise,
            onMoveExercise = viewModel::moveExercise, onSave = viewModel::saveRoutine,
            onCancel = { viewModel.back() }, modifier = modifier,
        )
        TrainingPage.ROUTINE_DETAILS -> RoutineDetailsScreen(
            state = state, onChooseRoutine = viewModel::requestRoutine,
            onRetrySelection = viewModel::retrySelection, modifier = modifier,
        )
        TrainingPage.WORKOUT -> WorkoutScreen(
            state = state, onChangeRoutine = viewModel::changeRoutine,
            onEditSeries = { id, number, weight, reps -> viewModel.editSeries(id, number, weight, reps) },
            onRetrySeries = { id, number -> viewModel.saveSeries(id, number) },
            onDiscardSeriesEdit = viewModel::discardSeriesEdit,
            onMarkExercise = viewModel::markExercise, onDateSelected = viewModel::selectDate,
            onRefresh = viewModel::refresh, modifier = modifier,
        )
        TrainingPage.LIBRARY -> TrainingScreen(
            state = state, onCreateRoutine = viewModel::startCreate,
            onChooseRoutine = viewModel::requestRoutine, onViewRoutine = viewModel::viewRoutine,
            onOpenSession = viewModel::openSession,
            onDateSelected = viewModel::selectDate, onRefresh = viewModel::refresh,
            onRetrySelection = viewModel::retrySelection,
            onToggleManagement = viewModel::toggleRoutineManagement, onEditRoutine = viewModel::startEditRoutine,
            onArchiveRoutine = viewModel::requestArchiveRoutine, onRetryArchive = viewModel::retryArchiveRoutine,
            modifier = modifier,
        )
    }
    state.confirmArchiveId?.let { id ->
        val name = state.routines.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = viewModel::cancelArchiveRoutine,
            title = { Text("Eliminar rutina") },
            text = { Text("¿Deseas eliminar la rutina $name? Los entrenamientos ya registrados se conservarán.") },
            confirmButton = { TextButton(onClick = viewModel::confirmArchiveRoutine, enabled = !state.isBusy) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = viewModel::cancelArchiveRoutine) { Text("Cancelar") } },
        )
    }
    if (state.confirmDiscard) AlertDialog(
        onDismissRequest = viewModel::cancelDiscard,
        title = { Text(if (state.editingRoutineId != null) "Salir de Editar rutina" else "Salir de Crear rutina") },
        text = { Text(if (state.draftLocked) "El guardado podría haberse completado. Al salir se actualizará la lista para comprobarlo."
            else "¿Deseas descartar el borrador de esta rutina?") },
        confirmButton = { TextButton(onClick = viewModel::leaveEditor) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = viewModel::cancelDiscard) { Text("Cancelar") } },
    )
    if (state.confirmRoutineId != null) AlertDialog(
        onDismissRequest = viewModel::cancelRoutineChange,
        title = { Text("Cambiar de rutina") },
        text = { Text("Ya tienes series, pesos o ejercicios registrados. Si aceptas cambiar de rutina, se eliminará todo lo registrado en este entrenamiento. ¿Deseas continuar?") },
        confirmButton = { TextButton(onClick = viewModel::confirmRoutineChange, enabled = !state.isBusy) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = viewModel::cancelRoutineChange) { Text("Cancelar") } },
    )
}
