package com.upupprogress.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upupprogress.app.data.repository.AuthRepository
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val AUTH_TIMEOUT_MILLIS = 30_000L
private const val TIMEOUT_MESSAGE = "La conexión tardó demasiado. Inténtalo de nuevo."

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val isRestoringSession: Boolean = false,
)

class LoginViewModel : ViewModel() {

    private val repository = AuthRepository()
    private val operationState = MutableStateFlow(OperationState())

    val uiState: StateFlow<LoginUiState> = combine(
        repository.sessionStatus,
        operationState
    ) { sessionStatus, operation ->
        LoginUiState(
            isLoading = operation.isLoading || (sessionStatus is SessionStatus.Initializing || sessionStatus is SessionStatus.RefreshFailure),
            errorMessage = operation.errorMessage
                ?: if (sessionStatus is SessionStatus.RefreshFailure) {
                    "No se pudo renovar tu sesión. Revisa tu conexión; volveremos a intentarlo automáticamente."
                } else {
                    null
                },
            isAuthenticated = sessionStatus is SessionStatus.Authenticated,
            isRestoringSession = (sessionStatus is SessionStatus.Initializing || sessionStatus is SessionStatus.RefreshFailure),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LoginUiState(
            isAuthenticated = repository.sessionStatus.value is SessionStatus.Authenticated,
            isLoading = (repository.sessionStatus.value is SessionStatus.Initializing || repository.sessionStatus.value is SessionStatus.RefreshFailure),
            isRestoringSession = (repository.sessionStatus.value is SessionStatus.Initializing || repository.sessionStatus.value is SessionStatus.RefreshFailure),
        )
    )

    fun signIn(email: String, password: String) {
        launchAuthRequest { repository.signIn(email, password) }
    }

    fun signOut() {
        launchAuthRequest { repository.signOut() }
    }

    private fun launchAuthRequest(request: suspend () -> Unit) {
        if (operationState.value.isLoading) return
        operationState.value = OperationState(isLoading = true)

        viewModelScope.launch {
            try {
                val completed = withTimeoutOrNull(AUTH_TIMEOUT_MILLIS) {
                    request()
                    true
                } ?: false
                if (!completed) {
                    operationState.update { it.copy(errorMessage = TIMEOUT_MESSAGE) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                operationState.update { it.copy(errorMessage = authenticationErrorMessage(error)) }
            } finally {
                operationState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun authenticationErrorMessage(error: Exception): String = when (error) {
        is AuthRestException -> when (error.errorCode) {
            AuthErrorCode.InvalidCredentials, AuthErrorCode.UserNotFound ->
                "Correo o contraseña incorrectos."
            AuthErrorCode.EmailNotConfirmed ->
                "Confirma tu correo electrónico antes de iniciar sesión."
            AuthErrorCode.OverRequestRateLimit ->
                "Demasiados intentos. Espera un momento y vuelve a intentarlo."
            AuthErrorCode.RequestTimeout -> TIMEOUT_MESSAGE
            else -> "No se pudo completar la autenticación. Inténtalo de nuevo."
        }
        is HttpRequestTimeoutException -> TIMEOUT_MESSAGE
        is HttpRequestException, is IOException ->
            "No se pudo conectar con Supabase. Revisa tu conexión e inténtalo de nuevo."
        else -> "No se pudo completar la solicitud. Inténtalo de nuevo."
    }

    private data class OperationState(
        val isLoading: Boolean = false,
        val errorMessage: String? = null
    )
}
