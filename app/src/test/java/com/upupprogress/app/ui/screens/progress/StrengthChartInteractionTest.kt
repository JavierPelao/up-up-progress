package com.upupprogress.app.ui.screens.progress

import androidx.compose.ui.geometry.Offset
import com.upupprogress.app.data.model.*
import com.upupprogress.app.domain.training.TrainingProgressCalculator
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class StrengthChartInteractionTest {
    private val from = LocalDate.of(2026, 11, 2)
    private fun points(): List<StrengthChartPoint> {
        fun row(id: String, date: LocalDate, kg: String, reps: Int) =
            ExerciseSetRecord("owner", id, null, date, "we-$id", "squat", "Sentadilla",
                "pending", RecordedTrainingSet(id, 1, kg, reps))
        val progress = TrainingProgressCalculator.calculate(
            listOf(row("load", from, "70", 1), row("estimated", from.plusDays(3), "50", 12)),
            "owner", "squat", from, from.plusWeeks(2), TrainingPeriod.WEEK,
        )
        return strengthChartPoints(progress)
    }

    @Test fun coincidentIndicatorsKeepBothSourcesAvailableForSelection() {
        val points = points()
        assertEquals(2, points.size)
        val positions = points.map { StrengthPlotPosition(it, Offset(60f, 40f)) }
        val hit = hitStrengthPoints(positions, Offset(61f, 41f), 24f)
        assertEquals(2, hit.size)
        assertEquals("load", hit.first { it.metric == StrengthMetric.MAXIMUM_LOAD }.measurement.source.set.id)
        assertEquals("estimated", hit.first { it.metric == StrengthMetric.ESTIMATED_ONE_REP_MAX }.measurement.source.set.id)
    }

    @Test fun touchingFarFromPointsDoesNotInventASelection() {
        val positions = points().map { StrengthPlotPosition(it, Offset(60f, 40f)) }
        assertTrue(hitStrengthPoints(positions, Offset(200f, 200f), 24f).isEmpty())
    }

    @Test fun emptyPeriodsDoNotBecomeZeroPoints() {
        assertTrue(points().all { it.periodStart == from })
    }
}
