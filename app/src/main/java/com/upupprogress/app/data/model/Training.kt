package com.upupprogress.app.data.model

import java.math.BigDecimal
import java.util.UUID
import kotlinx.serialization.Serializable

/** Límites técnicos de los formularios; no son recomendaciones de entrenamiento. */
object TrainingInputLimits {
    const val MAX_NAME = 120
    const val MAX_DESCRIPTION = 1000
    const val MAX_EXERCISES = 50
    const val MAX_SETS = 30
    const val MAX_REPS = 1000
}

@Serializable
data class ExerciseCatalogItem(val id: String, val name: String)

@Serializable
data class PlannedExercise(
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: String? = null,
    val name: String,
    val plannedSets: Int,
    val plannedReps: Int,
)

@Serializable
data class RoutineDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val exercises: List<PlannedExercise> = emptyList(),
) {
    val isValid: Boolean get() = name.trim().length in 1..TrainingInputLimits.MAX_NAME &&
        description.length <= TrainingInputLimits.MAX_DESCRIPTION &&
        exercises.size in 1..TrainingInputLimits.MAX_EXERCISES && exercises.all {
            it.name.trim().length in 1..TrainingInputLimits.MAX_NAME &&
                it.plannedSets in 1..TrainingInputLimits.MAX_SETS &&
                it.plannedReps in 1..TrainingInputLimits.MAX_REPS
        }
}

@Serializable
data class RoutinePlan(
    val id: String,
    val name: String,
    val description: String? = null,
    val exercises: List<PlannedExercise> = emptyList(),
)

fun reorderedExercises(exercises: List<PlannedExercise>, fromId: String, toId: String): List<PlannedExercise> {
    val from = exercises.indexOfFirst { it.id == fromId }
    val to = exercises.indexOfFirst { it.id == toId }
    if (from < 0 || to < 0 || from == to) return exercises
    return exercises.toMutableList().apply { add(to, removeAt(from)) }
}

fun parseTrainingWeight(input: String): BigDecimal? {
    if (input.isBlank()) return BigDecimal.ZERO
    val raw = input.trim().replace(',', '.')
    if (!Regex("[0-9]{1,5}(\\.[0-9]{1,2})?").matches(raw)) return null
    return raw.toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO && it <= BigDecimal("99999.99") }
}

@Serializable
data class RecordedTrainingSet(
    val id: String,
    val number: Int,
    val weightKg: String,
    val reps: Int,
)

@Serializable
data class WorkoutExercise(
    val id: String,
    val exerciseId: String,
    val name: String,
    val position: Int,
    val plannedSets: Int,
    val plannedReps: Int,
    val completionStatus: String = "pending",
    val sets: List<RecordedTrainingSet> = emptyList(),
)

@Serializable
data class WorkoutSession(
    val id: String,
    val performedOn: String,
    val routineId: String?,
    val routineName: String,
    val exercises: List<WorkoutExercise>,
    val routineDescription: String? = null,
) {
    val hasRecordedData: Boolean get() = exercises.any { it.sets.isNotEmpty() || it.completionStatus != "pending" }
}
