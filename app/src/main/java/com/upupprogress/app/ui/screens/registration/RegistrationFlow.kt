package com.upupprogress.app.ui.screens.registration

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalFocusManager
import com.upupprogress.app.data.repository.RegistrationResult
import com.upupprogress.app.ui.screens.onboarding.*
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import androidx.compose.ui.tooling.preview.Preview

/** La cuenta se crea únicamente al confirmar el resumen; los pasos comparten un borrador. */
@Composable
fun RegistrationFlow(
    onSignUp: (String, String) -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    result: RegistrationResult? = null,
) {
    val fields = rememberRegistrationFields()
    var form by rememberSaveable(stateSaver = OnboardingFormSaver) { mutableStateOf(OnboardingFormState()) }
    var showOnboarding by rememberSaveable { mutableStateOf(false) }
    var step by rememberSaveable { mutableStateOf(OnboardingStep.PERSONAL) }
    var returnToReview by rememberSaveable { mutableStateOf(false) }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val autofill = LocalAutofillManager.current

    fun returnToSummary() {
        focus.clearFocus()
        form = form.copy(name = fields.name.text.toString().trim())
        step = OnboardingStep.REVIEW
        showOnboarding = true
        returnToReview = false
    }

    fun leave() {
        focus.clearFocus()
        fields.clearPasswords()
        autofill?.cancel()
        onBackToLogin()
    }
    DisposableEffect(fields, autofill) {
        onDispose {
            fields.clearPasswords()
            autofill?.cancel()
        }
    }
    LaunchedEffect(result) {
        if (result != null) fields.clearPasswords()
    }
    BackHandler(enabled = isLoading) { /* Esperar la respuesta evita duplicar una creación incierta. */ }
    BackHandler(enabled = !showOnboarding && !isLoading) {
        if (returnToReview && result == null) returnToSummary() else leave()
    }

    if (!showOnboarding || result != null) {
        RegistrationScreen(
            onSignUp = { _, _ ->
                form = form.copy(name = fields.name.text.toString().trim())
                showOnboarding = true
                if (!returnToReview) step = OnboardingStep.PERSONAL
                else step = OnboardingStep.REVIEW
                returnToReview = false
                accountMessage = null
            },
            onBackToLogin = ::leave,
            modifier = modifier,
            isLoading = isLoading,
            errorMessage = accountMessage ?: errorMessage,
            result = result,
            fields = fields,
        )
    } else {
        OnboardingScreen(
            form = form,
            onFormChange = { updated ->
                form = updated
                if (fields.name.text.toString() != updated.name) {
                    fields.name.edit { replace(0, length, updated.name) }
                }
            },
            step = step,
            onStepChange = { step = it },
            onBackToAccount = { showOnboarding = false; returnToReview = false },
            onEditAccount = { showOnboarding = false; returnToReview = true },
            onConfirm = {
                if (fields.isValid()) {
                    onSignUp(fields.email.text.toString().trim(), fields.password.text.toString())
                } else {
                    accountMessage = "Revisa tus datos de acceso. Si la pantalla se recreó, vuelve a ingresar la contraseña por seguridad."
                    showOnboarding = false
                    returnToReview = true
                }
            },
            modifier = modifier,
            email = fields.email.text.toString().trim(),
            isLoading = isLoading,
            errorMessage = errorMessage,
        )
    }
}

@Preview(name = "Registro por pasos", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RegistrationFlowPreview() {
    UpUpProgressTheme { RegistrationFlow(onSignUp = { _, _ -> }, onBackToLogin = {}) }
}
