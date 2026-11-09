package com.upupprogress.app.ui.screens.registration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upupprogress.app.data.model.AuthenticatedAccount
import com.upupprogress.app.data.model.OnboardingStatus
import com.upupprogress.app.data.repository.*
import com.upupprogress.app.ui.screens.onboarding.*
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.time.LocalDate
import java.util.Locale
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
private const val SETUP_ERROR_MESSAGE =
    "No se pudo confirmar el guardado de tus datos. Revisa la conexión y vuelve a intentarlo."

enum class AccountSetupPhase { IDLE, CHECKING, REQUIRED, COMPLETE }

data class RegistrationUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val result: RegistrationResult? = null,
    val setupPhase: AccountSetupPhase = AccountSetupPhase.IDLE,
    val setupForm: OnboardingFormState = OnboardingFormState(),
    val savedStatus: OnboardingStatus? = null,
    val showSavedConfirmation: Boolean = false,
    val loadingMessage: String = "Creando tu cuenta…",
)

class RegistrationViewModel internal constructor(
    private val savedState: SavedStateHandle,
    private val auth: RegistrationAuthGateway,
    private val onboarding: OnboardingGateway,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    constructor(savedStateHandle: SavedStateHandle) : this(savedStateHandle, AuthRepository(), OnboardingRepository())

    private val mutableUiState = MutableStateFlow(RegistrationUiState())
    val uiState = mutableUiState.asStateFlow()
    val authenticatedEmail: String get() = auth.currentAccount()?.email.orEmpty()
    private var signUpJob: Job? = null
    private var setupJob: Job? = null
    private var checkedUserId: String? = null

    fun signUp(email: String, password: String, form: OnboardingFormState) {
        if (mutableUiState.value.isLoading || mutableUiState.value.result != null) return
        if (validateOnboardingForm(form).isNotEmpty()) {
            mutableUiState.value = RegistrationUiState(errorMessage = "Revisa los datos del formulario.")
            return
        }
        rememberDraft(form, email, null)
        mutableUiState.value = RegistrationUiState(isLoading = true)
        signUpJob = viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(30_000L) { auth.signUp(email, password) }
                currentCoroutineContext().ensureActive()
                if (auth.currentAccount() != null) {
                    resolveAuthenticated()
                } else if (result == RegistrationResult.EmailConfirmationRequired) {
                    mutableUiState.value = RegistrationUiState(result = result)
                } else {
                    mutableUiState.value = RegistrationUiState(errorMessage = REGISTRATION_TIMEOUT_MESSAGE)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableUiState.value = RegistrationUiState(errorMessage = registrationErrorMessage(error))
            }
        }
    }

    /** Se ejecuta también al iniciar sesión después de confirmar el correo. */
    fun onAuthenticated() {
        if (signUpJob?.isActive == true || setupJob?.isActive == true) return
        val account = auth.currentAccount() ?: return
        if (checkedUserId == account.id && mutableUiState.value.setupPhase == AccountSetupPhase.COMPLETE) return
        setupJob = viewModelScope.launch { resolveAuthenticated() }
    }

    fun completeProfile(form: OnboardingFormState) {
        if (mutableUiState.value.isLoading || setupJob?.isActive == true) return
        val account = auth.currentAccount() ?: return
        if (validateOnboardingForm(form).isNotEmpty()) return
        rememberDraft(form, account.email.orEmpty(), account.id)
        setupJob = viewModelScope.launch { resolveAuthenticated() }
    }

    private suspend fun resolveAuthenticated() {
        val account = auth.currentAccount() ?: return
        mutableUiState.value = RegistrationUiState(
            isLoading = true, setupPhase = AccountSetupPhase.CHECKING,
            loadingMessage = "Comprobando tus datos…",
        )
        try {
            val status = withTimeoutOrNull(30_000L) { onboarding.loadStatus() }
                ?: throw IOException("Respuesta pendiente")
            ensureAccount(account)
            val pending = draftFor(account)
            if (status.isComplete) {
                confirm(status, account, pending != null)
                return
            }
            val form = pending ?: status.toForm()
            mutableUiState.value = RegistrationUiState(
                setupPhase = AccountSetupPhase.REQUIRED, setupForm = form,
                isLoading = pending != null, loadingMessage = "Guardando tus datos…",
            )
            if (pending != null) {
                try {
                    // La fecha se conserva entre reintentos; no se crean pesos para días distintos.
                    val date = savedState.get<String>("pending_date")?.let(LocalDate::parse) ?: today()
                    savedState["pending_user_id"] = account.id
                    val saved = withTimeoutOrNull(30_000L) { onboarding.complete(pending.toSubmission(date)) }
                        ?: throw IOException("Guardado pendiente")
                    ensureAccount(account)
                    confirm(saved, account, true)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    ensureAccount(account)
                    mutableUiState.value = RegistrationUiState(
                        setupPhase = AccountSetupPhase.REQUIRED, setupForm = pending,
                        errorMessage = SETUP_ERROR_MESSAGE,
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (auth.currentAccount()?.id != account.id) return
            mutableUiState.value = RegistrationUiState(
                setupPhase = AccountSetupPhase.CHECKING,
                errorMessage = "No se pudo consultar tu configuración. Revisa la conexión y vuelve a intentarlo.",
            )
        }
    }

    private fun confirm(status: OnboardingStatus, account: AuthenticatedAccount, showConfirmation: Boolean) {
        check(status.isComplete && status.userId == account.id && status.targets != null &&
            status.weightKg != null && status.measuredOn != null && !status.displayName.isNullOrBlank())
        checkedUserId = account.id
        mutableUiState.value = RegistrationUiState(
            result = RegistrationResult.SessionStarted,
            setupPhase = AccountSetupPhase.COMPLETE, savedStatus = status,
            showSavedConfirmation = showConfirmation,
        )
    }

    fun acknowledgeSaved() {
        auth.currentAccount()?.let { if (draftFor(it) != null) clearDraft() }
        mutableUiState.value = mutableUiState.value.copy(showSavedConfirmation = false)
    }

    fun onSignedOut() {
        // Una Activity recreada también observa el estado invitado; no cancela un alta pendiente.
        if (checkedUserId == null && mutableUiState.value.setupPhase == AccountSetupPhase.IDLE) return
        signUpJob?.cancel()
        setupJob?.cancel()
        checkedUserId = null
        mutableUiState.value = RegistrationUiState()
    }

    /** Volver al login conserva el borrador sin contraseñas para terminar después de confirmar correo. */
    fun reset() {
        signUpJob?.cancel()
        setupJob?.cancel()
        mutableUiState.value = RegistrationUiState()
    }

    private fun rememberDraft(form: OnboardingFormState, email: String, userId: String?) {
        val sameForm = savedState.get<ArrayList<Any>>("pending_form")?.let(::restoreOnboardingForm) == form
        val sameEmail = savedState.get<String>("pending_email") == normalizeEmail(email)
        if (!sameForm || !sameEmail || savedState.get<String>("pending_date") == null) {
            savedState["pending_date"] = today().toString()
        }
        savedState["pending_form"] = ArrayList(form.toSavedValues())
        savedState["pending_email"] = normalizeEmail(email)
        savedState["pending_user_id"] = userId
    }

    private fun draftFor(account: AuthenticatedAccount): OnboardingFormState? {
        val owner = savedState.get<String>("pending_user_id")
        val matches = if (owner != null) owner == account.id else
            !account.email.isNullOrBlank() && savedState.get<String>("pending_email") == normalizeEmail(account.email)
        if (!matches) return null
        return savedState.get<ArrayList<Any>>("pending_form")?.let(::restoreOnboardingForm)
    }

    private fun clearDraft() {
        listOf("pending_form", "pending_date", "pending_email", "pending_user_id").forEach { savedState.remove<Any>(it) }
    }

    private suspend fun ensureAccount(account: AuthenticatedAccount) {
        currentCoroutineContext().ensureActive()
        if (auth.currentAccount()?.id != account.id) throw CancellationException("La sesión cambió")
    }

    private fun normalizeEmail(email: String) = email.trim().lowercase(Locale.ROOT)

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
