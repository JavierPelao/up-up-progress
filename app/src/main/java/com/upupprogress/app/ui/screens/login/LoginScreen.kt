package com.upupprogress.app.ui.screens.login

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import androidx.compose.ui.unit.sp
@Composable
fun LoginScreen(
    onSignIn: (email: String, password: String) -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    val emailState = rememberTextFieldState()
    // La contraseña permanece solo en memoria; no se guarda al recrear la Activity.
    val passwordState = remember { TextFieldState() }
    var passwordVisible by remember { mutableStateOf(false) }
    var submitAttempted by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val autofillManager = LocalAutofillManager.current
    val email = emailState.text.toString().trim()
    val emailError = when {
        !submitAttempted -> null
        email.isBlank() -> "Ingresa tu correo electrónico."
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Ingresa un correo electrónico válido."
        else -> null
    }
    val passwordError = if (submitAttempted && passwordState.text.isEmpty()) {
        "Ingresa tu contraseña."
    } else {
        null
    }

    fun submit() {
        if (isLoading) return
        submitAttempted = true
        if (email.isNotBlank() &&
            Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
            passwordState.text.isNotEmpty()
        ) {
            focusManager.clearFocus()
            onSignIn(email, passwordState.text.toString())
        }
    }

    fun leaveForm(action: () -> Unit) {
        focusManager.clearFocus()
        // No ofrecer guardar credenciales de este formulario sin autenticar.
        autofillManager?.cancel()
        passwordState.edit { replace(0, length, "") }
        action()
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
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↑",
                        modifier = Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Up Up Progress!",
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "¡Hola de nuevo!",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Iniciar sesión",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ingresa con tu correo y contraseña.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            contentType = ContentType.Password
                        },
                        enabled = !isLoading,
                        label = { Text("Contraseña") },
                        trailingIcon = {
                            TextButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !isLoading
                            ) {
                                Text(
                                    text = if (passwordVisible) "Ocultar" else "Mostrar",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        },
                        textObfuscationMode = if (passwordVisible) {
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
                        isError = passwordError != null,
                        supportingText = passwordError?.let { { Text(it) } },
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )
                    TextButton(
                        onClick = { leaveForm(onForgotPassword) },
                        enabled = !isLoading,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("¿Olvidaste tu contraseña?")
                    }

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
                        Text(if (isLoading) "Ingresando..." else "Iniciar sesión")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "¿No tienes cuenta?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { leaveForm(onCreateAccount) },
                            enabled = !isLoading
                        ) {
                            Text("Crear cuenta", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

        }
    }
}

@Preview(name = "Login", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Login compacto", showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun LoginPreview() {
    UpUpProgressTheme {
        LoginScreen(
            onSignIn = { _, _ -> },
            onForgotPassword = {},
            onCreateAccount = {},
        )
    }
}
