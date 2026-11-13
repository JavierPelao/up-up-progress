package com.upupprogress.app.data.model

import java.math.BigDecimal
import java.time.LocalDate

/** Serie persistida y su procedencia. El estado del ejercicio no decide si hubo trabajo real. */
data class ExerciseSetRecord(
    val userId: String,
    val sessionId: String,
    val routineId: String?,
    val performedOn: LocalDate,
    val workoutExerciseId: String,
    val exerciseId: String,
    val exerciseName: String,
    val completionStatus: String,
    val set: RecordedTrainingSet,
)

/** Solo ejercicios con series persistidas; incluye el inicio real de su historial. */
data class RecordedExercise(
    val id: String,
    val name: String,
    val firstPerformedOn: LocalDate,
)

enum class TrainingPeriod { WEEK, MONTH }

/** El valor y su fuente siempre corresponden a una misma serie. */
data class TrainingSetMetric<T>(
    val value: T,
    val source: ExerciseSetRecord,
)

data class TrainingProgressPoint(
    val periodStart: LocalDate,
    val periodEndInclusive: LocalDate,
    val maximumLoad: TrainingSetMetric<BigDecimal>?,
    val estimatedOneRepMax: TrainingSetMetric<BigDecimal>?,
    val repetitionsAtWeight: TrainingSetMetric<Int>?,
    val frequencySourceSets: List<ExerciseSetRecord>,
) {
    val sessionIds: Set<String> get() = frequencySourceSets.map { it.sessionId }.toSet()
    val frequency: Int get() = sessionIds.size
}

data class TrainingProgress(
    val userId: String,
    val exerciseId: String,
    val fromInclusive: LocalDate,
    val throughInclusive: LocalDate,
    val period: TrainingPeriod,
    val selectedWeightKg: BigDecimal?,
    val availableWeightsKg: List<BigDecimal>,
    val points: List<TrainingProgressPoint>,
)
