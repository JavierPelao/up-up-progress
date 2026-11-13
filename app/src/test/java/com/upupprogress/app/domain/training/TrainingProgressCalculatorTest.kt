package com.upupprogress.app.domain.training

import com.upupprogress.app.data.model.*
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TrainingProgressCalculatorTest {
    private val from = LocalDate.of(2026, 11, 2)
    private val through = LocalDate.of(2026, 11, 29)

    private fun row(
        id: String, date: String, session: String, number: Int, kg: String, reps: Int,
        user: String = "owner", exercise: String = "squat", status: String = "pending",
        workoutExercise: String = "we-$session",
    ) = ExerciseSetRecord(
        user, session, "routine", LocalDate.parse(date), workoutExercise, exercise, "Sentadilla",
        status, RecordedTrainingSet(id, number, kg, reps),
    )

    private fun knownHistory() = listOf(
        row("a1", "2026-11-02", "a", 1, "80", 8),
        row("a2", "2026-11-02", "a", 2, "90", 3),
        row("a3", "2026-11-02", "a", 3, "60", 12),
        row("b1", "2026-11-05", "b", 1, "80.00", 10),
        row("b2", "2026-11-05", "b", 2, "70", 12),
        row("b3", "2026-11-05", "b", 1, "60", 15, workoutExercise = "we-b-again"),
        row("c1", "2026-11-16", "c", 1, "95", 1),
        row("c2", "2026-11-16", "c", 2, "75", 12),
        row("c3", "2026-11-16", "c", 3, "80", 7),
    )

    private fun calculate(
        records: List<ExerciseSetRecord> = knownHistory(),
        period: TrainingPeriod = TrainingPeriod.WEEK,
        start: LocalDate = from,
        end: LocalDate = through,
        weight: String? = "80.0",
    ) = TrainingProgressCalculator.calculate(
        records, "owner", "squat", start, end, period, weight?.toBigDecimal(),
    )

    private fun assertDecimal(expected: String, actual: BigDecimal?) {
        assertNotNull(actual)
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    @Test fun weeklyPeaksComeFromIndividualSetsAndFrequencyCountsDistinctSessions() {
        val result = calculate()
        assertEquals(listOf(2, 0, 1, 0), result.points.map { it.frequency })
        assertEquals(listOf("2026-11-02", "2026-11-09", "2026-11-16", "2026-11-23"),
            result.points.map { it.periodStart.toString() })
        val first = result.points.first()
        assertDecimal("90", first.maximumLoad!!.value)
        assertEquals("a2", first.maximumLoad.source.set.id)
        assertEquals(LocalDate.of(2026, 11, 2), first.maximumLoad.source.performedOn)
        assertEquals(2, first.maximumLoad.source.set.number)
        assertEquals(3, first.maximumLoad.source.set.reps)
        assertEquals("b1", first.estimatedOneRepMax!!.source.set.id)
        assertEquals(106.66666666666667, first.estimatedOneRepMax.value.toDouble(), 1e-12)
        assertEquals(10, first.repetitionsAtWeight!!.value)
        assertEquals("b1", first.repetitionsAtWeight.source.set.id)
        assertEquals(setOf("a", "b"), first.sessionIds)
        assertEquals(6, first.frequencySourceSets.size)

        val third = result.points[2]
        assertDecimal("95", third.maximumLoad!!.value)
        assertEquals("c1", third.maximumLoad.source.set.id)
        assertDecimal("105", third.estimatedOneRepMax!!.value)
        assertEquals("c2", third.estimatedOneRepMax.source.set.id)
        assertEquals(7, third.repetitionsAtWeight!!.value)
    }

    @Test fun monthlyPeaksConsiderAllSessionsAndEmptyMonthHasNoInventedMeasurements() {
        val result = calculate(period = TrainingPeriod.MONTH, end = LocalDate.of(2026, 12, 31))
        val november = result.points.first()
        assertEquals(LocalDate.of(2026, 11, 1), november.periodStart)
        assertEquals(LocalDate.of(2026, 11, 30), november.periodEndInclusive)
        assertDecimal("95", november.maximumLoad!!.value)
        assertEquals("b1", november.estimatedOneRepMax!!.source.set.id)
        assertEquals(10, november.repetitionsAtWeight!!.value)
        assertEquals(3, november.frequency)
        val december = result.points.last()
        assertNull(december.maximumLoad)
        assertNull(december.estimatedOneRepMax)
        assertNull(december.repetitionsAtWeight)
        assertEquals(0, december.frequency)
    }

    @Test fun epleyUsesOneRepDirectlyAndOnlyEstimatesTwoThroughTwelveReps() {
        fun set(kg: String, reps: Int) = RecordedTrainingSet("set", 1, kg, reps)
        assertDecimal("100", TrainingProgressCalculator.estimateOneRepMax(set("100", 1)))
        assertDecimal("64", TrainingProgressCalculator.estimateOneRepMax(set("60", 2)))
        assertDecimal("84", TrainingProgressCalculator.estimateOneRepMax(set("60", 12)))
        listOf(0, -1, 13, 30).forEach {
            assertNull(TrainingProgressCalculator.estimateOneRepMax(set("60", it)))
        }
        listOf("0", "-1", "", "NaN", "Infinity", "not-a-number").forEach {
            assertNull(TrainingProgressCalculator.estimateOneRepMax(set(it, 8)))
        }
    }

    @Test fun estimatesAreNotRoundedBeforeChoosingThePeak() {
        val result = calculate(listOf(
            row("precise", "2026-11-02", "a", 1, "60.01", 12), // 84.014
            row("lower", "2026-11-03", "b", 1, "70.01", 6), // 84.012
        )).points.first()
        assertEquals("precise", result.estimatedOneRepMax!!.source.set.id)
        assertDecimal("84.014", result.estimatedOneRepMax.value)
        assertEquals("lower", result.maximumLoad!!.source.set.id)
    }

    @Test fun exactDecimalWeightDoesNotCombineNearbyLoadsOrSumRepetitions() {
        val records = listOf(
            row("one", "2026-11-02", "a", 1, "60", 8),
            row("two", "2026-11-02", "a", 2, "60.00", 10),
            row("nearby", "2026-11-03", "b", 1, "60.01", 15),
        )
        val result = calculate(records, weight = "60.0")
        assertEquals(listOf("60", "60.01"), result.availableWeightsKg.map { it.stripTrailingZeros().toPlainString() })
        assertEquals(10, result.points.first().repetitionsAtWeight!!.value)
        assertEquals("two", result.points.first().repetitionsAtWeight!!.source.set.id)
        assertNull(calculate(records, weight = "61").points.first().repetitionsAtWeight)
        assertNull(calculate(records, weight = null).points.first().repetitionsAtWeight)
    }

    @Test fun statusDoesNotHideEffectiveSetsAndZeroRepsNeverInflatePeaksOrFrequency() {
        val result = calculate(listOf(
            row("pending", "2026-11-02", "a", 1, "40", 8),
            row("skipped", "2026-11-03", "b", 1, "50", 6, status = "skipped"),
            row("placeholder", "2026-11-04", "c", 1, "999", 0, status = "completed"),
            row("negative-reps", "2026-11-05", "d", 1, "999", -1),
        ))
        val point = result.points.first()
        assertEquals(2, point.frequency)
        assertDecimal("50", point.maximumLoad!!.value)
        assertEquals(setOf("pending", "skipped"), point.frequencySourceSets.map { it.completionStatus }.toSet())
        assertFalse(result.availableWeightsKg.any { it.compareTo(BigDecimal("999")) == 0 })
    }

    @Test fun unweightedExerciseHasFrequencyAndRepetitionsButNoEstimatedOneRepMax() {
        val point = calculate(listOf(row("bodyweight", "2026-11-02", "a", 1, "0", 12)), weight = "0")
            .points.first()
        assertEquals(1, point.frequency)
        assertDecimal("0", point.maximumLoad!!.value)
        assertEquals(12, point.repetitionsAtWeight!!.value)
        assertNull(point.estimatedOneRepMax)
    }

    @Test fun invalidWeightsDoNotCreateStrengthMeasurements() {
        val records = listOf("-1", "NaN", "Infinity", "").mapIndexed { index, kg ->
            row("bad-$index", "2026-11-02", "a", index + 1, kg, 8)
        }
        val result = calculate(records)
        assertNull(result.points.first().maximumLoad)
        assertNull(result.points.first().estimatedOneRepMax)
        assertNull(result.points.first().repetitionsAtWeight)
        assertTrue(result.availableWeightsKg.isEmpty())
        // La definición de frecuencia exige reps positivas, no carga externa.
        assertEquals(1, result.points.first().frequency)
    }

    @Test fun ownersExerciseIdsAndRequestedDatesAreIsolated() {
        val records = knownHistory() + listOf(
            row("foreign-user", "2026-11-02", "alien", 1, "900", 10, user = "other"),
            row("foreign-exercise", "2026-11-02", "alien", 1, "800", 10, exercise = "other"),
            row("outside", "2026-10-31", "old", 1, "50", 10),
        )
        val result = calculate(records)
        assertEquals(calculate().points, result.points)
        // Historial más amplio puede ofrecer cargas antiguas sin añadirlas a los puntos visibles.
        assertTrue(result.availableWeightsKg.any { it.compareTo(BigDecimal("50")) == 0 })
        assertFalse(result.availableWeightsKg.any { it >= BigDecimal("800") })
    }

    @Test fun calendarWeeksStartOnMondayEvenAcrossYearAndMonthBoundaries() {
        val records = listOf(
            row("sunday", "2027-01-03", "a", 1, "50", 8),
            row("monday", "2027-01-04", "b", 1, "55", 8),
        )
        val weekly = calculate(records, start = LocalDate.of(2027, 1, 3), end = LocalDate.of(2027, 1, 4))
        assertEquals(listOf("2026-12-28", "2027-01-04"), weekly.points.map { it.periodStart.toString() })
        assertEquals(listOf(1, 1), weekly.points.map { it.frequency })
        val monthly = calculate(listOf(
            row("nov", "2026-11-30", "a", 1, "50", 8),
            row("dec", "2026-12-01", "b", 1, "55", 8),
        ), period = TrainingPeriod.MONTH, start = LocalDate.of(2026, 11, 30), end = LocalDate.of(2026, 12, 1))
        assertEquals(listOf(1, 1), monthly.points.map { it.frequency })
        assertEquals(listOf("2026-11-01", "2026-12-01"), monthly.points.map { it.periodStart.toString() })
    }

    @Test fun partialPeriodsOnlyUseDatesWithinRequestedRangeAndTiesAreDeterministic() {
        val records = listOf(
            row("outside", "2026-11-02", "a", 1, "100", 10),
            row("older", "2026-11-03", "b", 1, "80", 10),
            row("newer-two", "2026-11-05", "c", 2, "80", 10),
            row("newer-one", "2026-11-05", "c", 1, "80", 10),
        )
        val result = calculate(records, start = LocalDate.of(2026, 11, 3), end = LocalDate.of(2026, 11, 5))
        assertEquals("newer-one", result.points.single().maximumLoad!!.source.set.id)
        assertEquals(result, calculate(records.reversed(), start = result.fromInclusive, end = result.throughInclusive))
        assertEquals(2, result.points.single().frequency)
    }

    @Test fun noHistoryHasNullMeasurementsAndZeroFrequencyForEveryPeriod() {
        val result = calculate(emptyList())
        assertEquals(4, result.points.size)
        assertTrue(result.availableWeightsKg.isEmpty())
        assertTrue(result.points.all {
            it.maximumLoad == null && it.estimatedOneRepMax == null && it.repetitionsAtWeight == null && it.frequency == 0
        })
    }

    @Test fun invalidRequestIsRejectedInsteadOfInventingResults() {
        assertThrows(IllegalArgumentException::class.java) { calculate(start = through, end = from) }
        assertThrows(IllegalArgumentException::class.java) { calculate(weight = "-1") }
    }
}
