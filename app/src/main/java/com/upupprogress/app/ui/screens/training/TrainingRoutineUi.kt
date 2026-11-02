package com.upupprogress.app.ui.screens.training

import java.time.DayOfWeek
data class TrainingRoutineUi(
    val id: String,
    val name: String,
    val days: Set<DayOfWeek>,
    val exercises: List<String>
)
