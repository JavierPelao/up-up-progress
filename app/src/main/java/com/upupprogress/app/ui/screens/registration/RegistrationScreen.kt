package com.upupprogress.app.ui.screens.registration

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.repository.RegistrationResult
import com.upupprogress.app.ui.theme.UpUpProgressTheme

@Composable
fun RegistrationScreen(
    onSignUp: (email: String, password: String) -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    result: RegistrationResult? = null,
    fields: RegistrationFields = rememberRegistrationFields(),
) {
    val nameState = fields.name
    val emailState = fields.email
    val passwordState = fields.password
    val confirmationState = fields.confirmation
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmationVisible by remember { mutableStateOf(false) }
    var submitAttempted by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val autofillManager = LocalAutofillManager.current
    val email = emailState.text.toString().trim()
    val password = passwordState.text.toString()
    val confirmation = confirmationState.text.toString()

    val nameError = if (submitAttempted && nameState.text.isBlank()) "Ingresa tu nombre." else null
    val emailError = when {
        !submitAttempted -> null
        email.isBlank() -> "Ingresa tu correo electrónico."
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Ingresa un correo electrónico válido."
        else -> null
    }
    val passwordError = when {
        !submitAttempted -> null
        password.isBlank() -> "Ingresa una contraseña."
        password.length < MIN_REGISTRATION_PASSWORD_LENGTH -> "Usa al menos $MIN_REGISTRATION_PASSWORD_LENGTH caracteres."
        else -> null
    }
    val confirmationError = when {
        !submitAttempted -> null
        confirmation.isEmpty() -> "Confirma tu contraseña."
        confirmation != password -> "Las contraseñas no coinciden."
        else -> null
    }

    fun clearPasswords() {
        passwordState.edit { replace(0, length, "") }
        confirmationState.edit { replace(0, length, "") }
    }

    fun leaveForm() {
        focusManager.clearFocus()
        autofillManager?.cancel()
        clearPasswords()
        onBackToLogin()
    }

    fun submit() {
        if (isLoading || result != null) return
        submitAttempted = true
        val currentEmail = emailState.text.toString().trim()
        val currentPassword = passwordState.text.toString()
        val currentConfirmation = confirmationState.text.toString()
        if (nameState.text.isNotBlank() && currentEmail.isNotBlank() &&
            Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches() &&
            currentPassword.isNotBlank() && currentPassword.length >= MIN_REGISTRATION_PASSWORD_LENGTH &&
            currentPassword == currentConfirmation
        ) {
            focusManager.clearFocus()
            onSignUp(currentEmail, currentPassword)
        }
    }

    LaunchedEffect(result) {
        if (result != null) {
            clearPasswords()
            autofillManager?.cancel()
            submitAttempted = false
        }
    }


    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            LinearProgressIndicator(
                progress = { 1f / 6f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .semantics { contentDescription = "Paso 1 de 6" },
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (result != null) {
                        val pendingConfirmation = result == RegistrationResult.EmailConfirmationRequired
                        Text(
                            text = if (pendingConfirmation) "Revisa tu correo" else "Registro completado",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (pendingConfirmation) {
                                "Si corresponde, recibirás un enlace para confirmar tu cuenta. Revisa también tu correo no deseado y después inicia sesión para terminar de guardar tu perfil, peso y metas."
                            } else {
                                "Tu cuenta está lista para continuar con tu progreso."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { leaveForm() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Volver al Login")
                        }
                    } else {
                        Text(
                            text = "Crear cuenta",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Completa tus datos de acceso. Después te guiaremos paso a paso.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            state = nameState,
                            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.PersonFullName },
                            enabled = !isLoading,
                            label = { Text("Nombre") },
                            lineLimits = TextFieldLineLimits.SingleLine,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next,
                            ),
                            isError = nameError != null,
                            supportingText = nameError?.let { { Text(it) } },
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                        )
                        OutlinedTextField(
                            state = emailState,
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentType = ContentType.Username + ContentType.EmailAddress
                            },
                            enabled = !isLoading,
                            label = { Text("Correo electrónico") },
                            placeholder = { Text("nombre@ejemplo.com") },
                            lineLimits = TextFieldLineLimits.SingleLine,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                                autoCorrectEnabled = false
                            ),
                            isError = emailError != null,
                            supportingText = emailError?.let { { Text(it) } },
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors
                        )
                        OutlinedSecureTextField(
                            state = passwordState,
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentType = ContentType.NewPassword
                            },
                            enabled = !isLoading,
                            label = { Text("Contraseña") },
                            trailingIcon = {
                                TextButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    enabled = !isLoading
                                ) {
                                    Text(if (passwordVisible) "Ocultar" else "Mostrar")
                                }
                            },
                            textObfuscationMode = if (passwordVisible) {
                                TextObfuscationMode.Visible
                            } else {
                                TextObfuscationMode.Hidden
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next,
                                autoCorrectEnabled = false
                            ),
                            isError = passwordError != null,
                            supportingText = {
                                Text(passwordError ?: "Mínimo $MIN_REGISTRATION_PASSWORD_LENGTH caracteres.")
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors
                        )
                        OutlinedSecureTextField(
                            state = confirmationState,
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentType = ContentType.NewPassword
                            },
                            enabled = !isLoading,
                            label = { Text("Confirmar contraseña") },
                            trailingIcon = {
                                TextButton(
                                    onClick = { confirmationVisible = !confirmationVisible },
                                    enabled = !isLoading
                                ) {
                                    Text(if (confirmationVisible) "Ocultar" else "Mostrar")
                                }
                            },
                            textObfuscationMode = if (confirmationVisible) {
                                TextObfuscationMode.Visible
                            } else {
                                TextObfuscationMode.Hidden
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false
                            ),
                            onKeyboardAction = { submit() },
                            isError = confirmationError != null,
                            supportingText = confirmationError?.let { { Text(it) } },
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Button(
                            onClick = { submit() },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isLoading) "Creando cuenta..." else "Continuar")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "¿Ya tienes cuenta?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { leaveForm() }) {
                                Text("Iniciar sesión", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Registro", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Registro compacto", showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun RegistrationPreview() {
    UpUpProgressTheme {
        RegistrationScreen(onSignUp = { _, _ -> }, onBackToLogin = {})
    }
}

@Preview(name = "Confirmación de correo", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RegistrationConfirmationPreview() {
    UpUpProgressTheme {
        RegistrationScreen(
            onSignUp = { _, _ -> },
            onBackToLogin = {},
            result = RegistrationResult.EmailConfirmationRequired
        )
    }
}
