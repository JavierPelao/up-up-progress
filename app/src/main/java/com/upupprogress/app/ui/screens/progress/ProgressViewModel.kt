package com.upupprogress.app.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upupprogress.app.data.model.WorkoutAnalysis
import com.upupprogress.app.data.repository.WorkoutAnalysisException
import com.upupprogress.app.data.repository.WorkoutAnalysisGateway
import com.upupprogress.app.data.repository.WorkoutAnalysisRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

data class ProgressUiState(
    val isLoading: Boolean = false,
    val analysis: WorkoutAnalysis? = null,
    val error: String? = null,
    val dialogVisible: Boolean = false,
)

class ProgressViewModel internal constructor(
    private val repository: WorkoutAnalysisGateway,
) : ViewModel() {
    constructor() : this(WorkoutAnalysisRepository())

    private val mutableState = MutableStateFlow(ProgressUiState())
    val uiState = mutableState.asStateFlow()
    private var owner: String? = null
    private var analysisJob: Job? = null

    fun activate(userId: String) {
        if (owner == userId) return
        clear()
        owner = userId
    }

    fun clear() {
        analysisJob?.cancel()
        analysisJob = null
        owner = null
        mutableState.value = ProgressUiState()
    }

    fun analyze() {
        val userId = owner ?: return
        if (mutableState.value.isLoading) return
        mutableState.value = ProgressUiState(isLoading = true)
        analysisJob = viewModelScope.launch {
            try {
                val analysis = withTimeoutOrNull(125.seconds) { repository.analyze() }
                ensureActive()
                if (owner == userId) {
                    mutableState.value = if (analysis == null) {
                        ProgressUiState(error = TIMEOUT_MESSAGE, dialogVisible = true)
                    } else {
                        ProgressUiState(analysis = analysis, dialogVisible = true)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (owner == userId) {
                    val message = when (error) {
                        is WorkoutAnalysisException -> error.message
                        is HttpRequestTimeoutException -> TIMEOUT_MESSAGE
                        else -> "No se pudo obtener el análisis. Revisa la conexión. El intento puede haber consumido una solicitud."
                    }
                    mutableState.value = ProgressUiState(error = message, dialogVisible = true)
                }
            }
        }
    }

    fun dismissResult() {
        mutableState.value = mutableState.value.copy(dialogVisible = false)
    }

    private companion object {
        const val TIMEOUT_MESSAGE = "El análisis tardó demasiado. No se volvió a enviar la solicitud; el intento puede haber consumido cuota."
    }
}
