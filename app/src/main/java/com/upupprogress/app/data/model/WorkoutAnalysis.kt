package com.upupprogress.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutAnalysis(
    val ok: Boolean,
    val status: String,
    val source: String,
    val summary: String,
    val suggestions: List<String>,
    val quota: WorkoutAnalysisQuota,
) {
    val generatedByAi: Boolean
        get() = status == "analyzed" && source == "groq"
}

@Serializable
data class WorkoutAnalysisQuota(
    val limit: Int,
    val remaining: Int? = null,
)
