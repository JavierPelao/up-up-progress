package com.upupprogress.app.domain.training

import com.upupprogress.app.data.model.ExerciseSetRecord
import com.upupprogress.app.data.model.RecordedTrainingSet
import com.upupprogress.app.data.model.TrainingPeriod
import com.upupprogress.app.data.model.TrainingProgress
import com.upupprogress.app.data.model.TrainingProgressPoint
import com.upupprogress.app.data.model.TrainingSetMetric
import java.math.BigDecimal
import java.math.MathContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Cálculos puros sobre registros persistidos: no consulta red, no escribe datos y no depende de Compose. */
object TrainingProgressCalculator {
    /**
     * Epley por serie, sin redondear a dos decimales antes de comparar máximos.
     * Una repetición usa la carga real; 2..12 usan Epley; el resto no genera estimación.
     */
    fun estimateOneRepMax(set: RecordedTrainingSet): BigDecimal? {
        val weight = validWeight(set) ?: return null
        if (weight.signum() == 0 || set.reps !in 1..12) return null
        if (set.reps == 1) return weight
        return weight.multiply(BigDecimal.valueOf(30L + set.reps))
            .divide(BigDecimal.valueOf(30), MathContext.DECIMAL128)
    }

    /**
     * Incluye todos los períodos que intersectan el rango, con semanas desde el lunes.
     * En los extremos se usan solo las series dentro del rango solicitado.
     *
     * Los pesos disponibles proceden de todo el historial recibido para este usuario/ejercicio,
     * aunque sea más amplio que el rango del gráfico. Para ofrecer todos los pesos históricos,
     * el llamador debe cargar ese historial completo, no solo las fechas visibles.
     *
     * Empates: fecha más reciente; luego IDs de sesión/ejercicio realizado, número de serie e ID.
     * Así la fuente no depende del orden en que llegaron las páginas del servidor.
     */
    fun calculate(
        records: List<ExerciseSetRecord>,
        userId: String,
        exerciseId: String,
        fromInclusive: LocalDate,
        throughInclusive: LocalDate,
        period: TrainingPeriod,
        selectedWeightKg: BigDecimal? = null,
    ): TrainingProgress {
        require(userId.isNotBlank() && exerciseId.isNotBlank())
        require(!throughInclusive.isBefore(fromInclusive))
        require(selectedWeightKg == null || selectedWeightKg.signum() >= 0)

        // pending/skipped pueden conservar series efectivas; completed puede contener ceros.
        val effective = records.filter {
            it.userId == userId && it.exerciseId == exerciseId && it.set.reps > 0 && it.set.number > 0
        }.sortedWith(
            compareByDescending<ExerciseSetRecord> { it.performedOn }
                .thenBy { it.sessionId }.thenBy { it.workoutExerciseId }
                .thenBy { it.set.number }.thenBy { it.set.id },
        )
        // TreeSet compara BigDecimal numéricamente: 60, 60.0 y 60.00 son una sola carga.
        val weights = effective.mapNotNull { validWeight(it.set) }.toSortedSet().toList()
        val grouped = effective.filter { it.performedOn in fromInclusive..throughInclusive }
            .groupBy { periodStart(it.performedOn, period) }
        val points = generateSequence(periodStart(fromInclusive, period)) { nextPeriod(it, period) }
            .takeWhile { it <= throughInclusive }
            .map { start ->
                val sources = grouped[start].orEmpty()
                val loads = sources.mapNotNull { source ->
                    validWeight(source.set)?.let { TrainingSetMetric(it, source) }
                }
                val estimates = sources.mapNotNull { source ->
                    estimateOneRepMax(source.set)?.let { TrainingSetMetric(it, source) }
                }
                val repetitions = bestRepetitions(sources, selectedWeightKg)
                TrainingProgressPoint(
                    periodStart = start,
                    periodEndInclusive = nextPeriod(start, period).minusDays(1),
                    maximumLoad = loads.maxByOrNull { it.value },
                    estimatedOneRepMax = estimates.maxByOrNull { it.value },
                    repetitionsAtWeight = repetitions,
                    // Frecuencia depende de reps > 0, también en ejercicios sin carga externa.
                    frequencySourceSets = sources,
                )
            }.toList()

        return TrainingProgress(
            userId, exerciseId, fromInclusive, throughInclusive, period, selectedWeightKg, weights, points,
        )
    }

    /** Reutiliza los períodos y sus series efectivas sin volver a calcular carga máxima ni Epley. */
    fun withSelectedWeight(progress: TrainingProgress, selectedWeightKg: BigDecimal?): TrainingProgress {
        require(selectedWeightKg == null || selectedWeightKg.signum() >= 0)
        return progress.copy(
            selectedWeightKg = selectedWeightKg,
            points = progress.points.map { point -> point.copy(
                repetitionsAtWeight = bestRepetitions(point.frequencySourceSets, selectedWeightKg),
            ) },
        )
    }

    private fun bestRepetitions(
        sources: List<ExerciseSetRecord>, selectedWeightKg: BigDecimal?,
    ): TrainingSetMetric<Int>? {
        if (selectedWeightKg == null) return null
        return sources.filter { validWeight(it.set)?.compareTo(selectedWeightKg) == 0 }
            .maxByOrNull { it.set.reps }
            ?.let { TrainingSetMetric(it.set.reps, it) }
    }

    private fun validWeight(set: RecordedTrainingSet): BigDecimal? =
        set.weightKg.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }

    private fun periodStart(date: LocalDate, period: TrainingPeriod): LocalDate = when (period) {
        TrainingPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        TrainingPeriod.MONTH -> date.withDayOfMonth(1)
    }

    private fun nextPeriod(start: LocalDate, period: TrainingPeriod): LocalDate = when (period) {
        TrainingPeriod.WEEK -> start.plusWeeks(1)
        TrainingPeriod.MONTH -> start.plusMonths(1)
    }
}
