package com.upupprogress.app.ui.screens.progress

import androidx.compose.ui.geometry.Offset
import com.upupprogress.app.data.model.*
import com.upupprogress.app.domain.training.TrainingProgressCalculator
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RepetitionsChartTest {
    @Test fun projectedPointsAndTouchKeepTheOriginalSetAndNeverAddAnAbsentPeriod() {
        val from = LocalDate.of(2026, 11, 2)
        val record = ExerciseSetRecord("owner", "session", null, from, "we", "bench", "Press", "pending",
            RecordedTrainingSet("winning-set", 3, "40.00", 12))
        val progress = TrainingProgressCalculator.calculate(listOf(record), "owner", "bench", from,
            from.plusWeeks(3), TrainingPeriod.WEEK, BigDecimal("40"))
        val point = repetitionsChartPoints(progress).single()
        assertEquals(4, progress.points.size)
        assertSame(record, point.measurement.source)
        assertEquals(3, point.measurement.source.set.number)
        val positions = listOf(ProgressPlotPosition(
            ProgressChartEntry(from, 0, BigDecimal("12"), "12 repeticiones", point), Offset(100f, 50f),
        ))
        assertSame(point, hitProgressPoints(positions, Offset(103f, 51f), 24f).single())
        assertTrue(hitProgressPoints(positions, Offset(140f, 51f), 24f).isEmpty())
        assertTrue(repetitionsChartPoints(TrainingProgressCalculator.withSelectedWeight(progress, null)).isEmpty())
    }

    @Test fun repetitionAxesAlwaysUseIntegerTicksIncludingSmallCounts() {
        listOf(1, 2, 3, 9, 12, 25, 100).forEach { max ->
            val axis = progressChartAxis(listOf(BigDecimal.valueOf(max.toLong())), integersOnly = true)
            assertTrue(axis.top >= max)
            assertEquals(0, axis.decimals)
            assertEquals(0, axis.ticks.first().compareTo(BigDecimal.ZERO))
            assertTrue(axis.ticks.all { it.stripTrailingZeros().scale() <= 0 })
            assertTrue(axis.ticks.zipWithNext().all { (a, b) -> b > a })
        }
    }

    @Test fun weightLabelsDoNotHideDifferencesOrDuplicateDecimalRepresentations() {
        assertEquals("40", formatProgressWeight(BigDecimal("40.000")))
        assertEquals(formatProgressWeight(BigDecimal("40")), formatProgressWeight(BigDecimal("40.0")))
        assertNotEquals(formatProgressWeight(BigDecimal("40.001")), formatProgressWeight(BigDecimal("40.002")))
        assertEquals("0", formatProgressWeight(BigDecimal("0.00")))
    }
}
