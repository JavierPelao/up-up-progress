package com.upupprogress.app.ui.screens.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upupprogress.app.data.repository.AuthRepository
import com.upupprogress.app.data.repository.RegistrationResult
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val REGISTRATION_TIMEOUT_MESSAGE =
    "La conexión tardó demasiado. Revisa tu correo antes de volver a intentar el registro."

data class RegistrationUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val result: RegistrationResult? = null
)

class RegistrationViewModel : ViewModel() {

    private val repository = AuthRepository()
    private val mutableUiState = MutableStateFlow(RegistrationUiState())
    val uiState = mutableUiState.asStateFlow()
    private var signUpJob: Job? = null

    fun signUp(email: String, password: String) {
        if (mutableUiState.value.isLoading || mutableUiState.value.result != null) return
        mutableUiState.value = RegistrationUiState(isLoading = true)

        signUpJob = viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(30.seconds) {
                    repository.signUp(email, password)
                }
                currentCoroutineContext().ensureActive()
                mutableUiState.value = if (result != null) {
                    RegistrationUiState(result = result)
                } else {
                    RegistrationUiState(errorMessage = REGISTRATION_TIMEOUT_MESSAGE)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableUiState.value = RegistrationUiState(
                    errorMessage = registrationErrorMessage(error)
                )
            }
        }
    }

    fun reset() {
        signUpJob?.cancel()
        signUpJob = null
        mutableUiState.value = RegistrationUiState()
    }

    private fun registrationErrorMessage(error: Exception): String = when (error) {
        is AuthRestException -> when (error.errorCode) {
            AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists ->
                "No se pudo registrar ese correo. Si ya tienes cuenta, inicia sesión."
            AuthErrorCode.EmailAddressInvalid ->
                "Ingresa un correo electrónico válido."
            AuthErrorCode.WeakPassword ->
                "La contraseña no cumple los requisitos de seguridad. Usa una contraseña más segura."
            AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit ->
                "Demasiados intentos. Espera un momento y vuelve a intentarlo."
            AuthErrorCode.SignupDisabled, AuthErrorCode.EmailProviderDisabled ->
                "El registro con correo no está disponible en este momento."
            AuthErrorCode.RequestTimeout -> REGISTRATION_TIMEOUT_MESSAGE
            else -> "No se pudo completar el registro. Inténtalo de nuevo."
        }
        is HttpRequestTimeoutException -> REGISTRATION_TIMEOUT_MESSAGE
        is HttpRequestException, is IOException ->
            "No se pudo conectar con Supabase. Revisa tu conexión e inténtalo de nuevo."
        else -> "No se pudo completar el registro. Inténtalo de nuevo."
    }
}
