package com.upupprogress.app.ui.screens.nutrition

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionWeekSelectorTest {
    @Test
    fun navigationUsesMondayWeeksAndNeverSelectsAFutureDate() {
        val today = LocalDate.of(2026, 11, 12)
        val previousSunday = LocalDate.of(2026, 11, 8)
        assertEquals(LocalDate.of(2026, 11, 2), nutritionWeekStart(previousSunday))
        assertEquals(today, adjacentNutritionWeek(previousSunday, today, 1))
        assertEquals(today, adjacentNutritionWeek(today, today, 1))
        val previous = adjacentNutritionWeek(today, today, -1)
        assertEquals(LocalDate.of(2026, 11, 5), previous)
        assertEquals(today.dayOfWeek, previous.dayOfWeek)
        assertEquals(today, adjacentNutritionWeek(previous, today, 1))
        assertEquals(LocalDate.of(2020, 2, 24), nutritionWeekStart(LocalDate.of(2020, 2, 29)))
    }

    @Test
    fun weekRangesIdentifyBothMonthsAndBothYearsWhenNeeded() {
        assertEquals("9–15 noviembre 2026", nutritionWeekRange(LocalDate.of(2026, 11, 12)))
        val monthBoundary = nutritionWeekRange(LocalDate.of(2026, 11, 30))
        assertTrue(monthBoundary.contains("nov"))
        assertTrue(monthBoundary.contains("dic"))
        val yearBoundary = nutritionWeekRange(LocalDate.of(2027, 1, 1))
        assertTrue(yearBoundary.contains("2026"))
        assertTrue(yearBoundary.contains("2027"))
        assertEquals(DayOfWeek.MONDAY, nutritionWeekStart(LocalDate.of(2027, 1, 1)).dayOfWeek)
    }
}
