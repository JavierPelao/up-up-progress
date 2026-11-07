package com.upupprogress.app.ui.screens.registration

import android.util.Patterns
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Contraseñas solo en memoria; esta clase no tiene Saver ni genera un toString con sus valores. */
class RegistrationFields(
    val name: TextFieldState,
    val email: TextFieldState,
    val password: TextFieldState,
    val confirmation: TextFieldState,
) {
    fun isValid(): Boolean = name.text.isNotBlank() &&
        Patterns.EMAIL_ADDRESS.matcher(email.text.toString().trim()).matches() &&
        password.text.isNotBlank() && password.text.length >= MIN_REGISTRATION_PASSWORD_LENGTH &&
        password.text.toString() == confirmation.text.toString()

    fun clearPasswords() {
        password.edit { replace(0, length, "") }
        confirmation.edit { replace(0, length, "") }
    }
}

// Política mínima ya utilizada por el registro; Supabase valida la política completa.
internal const val MIN_REGISTRATION_PASSWORD_LENGTH = 6

@Composable
fun rememberRegistrationFields(): RegistrationFields {
    val name = rememberTextFieldState()
    val email = rememberTextFieldState()
    val password = remember { TextFieldState() }
    val confirmation = remember { TextFieldState() }
    return remember(name, email, password, confirmation) {
        RegistrationFields(name, email, password, confirmation)
    }
}
