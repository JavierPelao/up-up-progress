package com.upupprogress.app.domain.training

import com.upupprogress.app.data.model.*
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RepetitionsProgressCalculatorTest {
    private val from = LocalDate.of(2026, 10, 5)
    private val through = LocalDate.of(2026, 11, 1)

    private fun row(id: String, day: Int, session: String, number: Int, kg: String, reps: Int) =
        ExerciseSetRecord("owner", session, null, LocalDate.of(2026, 10, day), "we-$session", "bench", "Press de banca",
            "pending", RecordedTrainingSet(id, number, kg, reps))

    private val history = listOf(
        row("a1", 5, "a", 1, "40", 6), row("a2", 5, "a", 2, "40.00", 8),
        row("b2", 8, "b", 2, "40.0", 10), row("heavy", 5, "a", 3, "50", 20),
        row("week-two", 12, "c", 1, "50", 11), row("week-three", 19, "d", 1, "40", 9),
        row("week-four", 26, "e", 3, "40", 12), row("zero", 28, "f", 1, "99", 0),
        row("nearby", 26, "e", 4, "40.01", 15),
    )

    private fun base(period: TrainingPeriod = TrainingPeriod.WEEK, throughDate: LocalDate = through) =
        TrainingProgressCalculator.calculate(history, "owner", "bench", from, throughDate, period)

    @Test fun weeklyRepetitionsSelectOneRealSetAcrossSessionsAndLeaveMissingWeightAbsent() {
        val result = TrainingProgressCalculator.withSelectedWeight(base(), BigDecimal("40.00"))
        assertEquals(listOf(10, null, 9, 12), result.points.map { it.repetitionsAtWeight?.value })
        assertEquals(listOf("40", "40.01", "50"), result.availableWeightsKg.map { it.stripTrailingZeros().toPlainString() })
        val first = result.points.first().repetitionsAtWeight!!
        assertEquals("b2", first.source.set.id)
        assertEquals("b", first.source.sessionId)
        assertEquals(2, first.source.set.number)
        assertEquals(LocalDate.of(2026, 10, 8), first.source.performedOn)
        assertEquals("pending", first.source.completionStatus)
        assertEquals("e", result.points.last().repetitionsAtWeight!!.source.sessionId)
        assertEquals(3, result.points.last().repetitionsAtWeight!!.source.set.number)
        assertNull(result.points[1].repetitionsAtWeight)
        assertEquals(11, TrainingProgressCalculator.withSelectedWeight(base(), BigDecimal("50"))
            .points[1].repetitionsAtWeight!!.value)
    }

    @Test fun selectingWeightReusesStrengthSourcesAndMatchesTheExistingCalculation() {
        val base = base()
        val selected = TrainingProgressCalculator.withSelectedWeight(base, BigDecimal("40.0"))
        val direct = TrainingProgressCalculator.calculate(history, "owner", "bench", from, through,
            TrainingPeriod.WEEK, BigDecimal("40.0"))
        assertEquals(direct, selected)
        base.points.zip(selected.points).forEach { (before, after) ->
            assertSame(before.maximumLoad, after.maximumLoad)
            assertSame(before.estimatedOneRepMax, after.estimatedOneRepMax)
            assertSame(before.frequencySourceSets, after.frequencySourceSets)
        }
        assertTrue(base.points.all { it.repetitionsAtWeight == null })
        assertTrue(TrainingProgressCalculator.withSelectedWeight(base, BigDecimal("41"))
            .points.all { it.repetitionsAtWeight == null })
        assertThrows(IllegalArgumentException::class.java) {
            TrainingProgressCalculator.withSelectedWeight(base, BigDecimal("-1"))
        }
    }

    @Test fun monthlyRepetitionsKeepTheWinningSourceAndDoNotCreateZerosForEmptyMonths() {
        val result = TrainingProgressCalculator.withSelectedWeight(
            base(TrainingPeriod.MONTH, LocalDate.of(2026, 12, 31)), BigDecimal("40"),
        )
        assertEquals(listOf(12, null, null), result.points.map { it.repetitionsAtWeight?.value })
        assertEquals(LocalDate.of(2026, 10, 1), result.points.first().periodStart)
        val winner = result.points.first().repetitionsAtWeight!!
        assertEquals("week-four", winner.source.set.id)
        assertEquals(LocalDate.of(2026, 10, 26), winner.source.performedOn)
        assertEquals(3, winner.source.set.number)
    }
}
