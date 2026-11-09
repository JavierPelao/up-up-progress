package com.upupprogress.app.ui.screens.registration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.model.OnboardingStatus
import com.upupprogress.app.ui.screens.onboarding.*
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Composable
fun AccountSetupScreen(
    state: RegistrationUiState,
    email: String,
    onComplete: (OnboardingFormState) -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onSignOut: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.isLoading) { /* Esperar la confirmación antes de salir. */ }
    Column(modifier.fillMaxSize()) {
        TextButton(onClick = onSignOut, enabled = !state.isLoading) { Text("Volver al inicio de sesión") }
        when (state.setupPhase) {
            AccountSetupPhase.REQUIRED -> {
                var form by rememberSaveable(stateSaver = OnboardingFormSaver) { mutableStateOf(state.setupForm) }
                var step by rememberSaveable {
                    mutableStateOf(if (validateOnboardingForm(form).isEmpty()) OnboardingStep.REVIEW else OnboardingStep.PERSONAL)
                }
                OnboardingScreen(
                    form = form, onFormChange = { form = it }, step = step, onStepChange = { step = it },
                    onBackToAccount = onExit, onEditAccount = null, onConfirm = onComplete,
                    email = email, isLoading = state.isLoading, errorMessage = state.errorMessage,
                    submissionLabel = "Guardar perfil y metas",
                    submissionDescription = "El peso inicial conservará la fecha del registro entre reintentos. Revisa tus datos antes de guardar.",
                    loadingMessage = state.loadingMessage, modifier = Modifier.weight(1f),
                )
            }
            AccountSetupPhase.COMPLETE -> SavedOnboardingSummary(
                status = requireNotNull(state.savedStatus), onContinue = onContinue, modifier = Modifier.weight(1f)
            )
            else -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.isLoading || state.errorMessage == null) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(state.loadingMessage)
                }
                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text("Volver a intentar") }
                }
            }
        }
    }
}

@Composable
private fun SavedOnboardingSummary(status: OnboardingStatus, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    fun BigDecimal.formatted() = stripTrailingZeros().toPlainString().replace('.', ',')
    val targets = requireNotNull(status.targets)
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Tu configuración está lista", style = MaterialTheme.typography.headlineMedium)
        Text(status.displayName.orEmpty(), style = MaterialTheme.typography.titleLarge)
        Text("Estos son los datos y las metas confirmados para tu cuenta.")
        Text("Peso: " + status.weightKg?.formatted() + " kg")
        Text("Fecha del peso: " + status.measuredOn?.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")))
        Text(if (status.nutritionGoalMode == "automatic") "Metas automáticas" else "Metas manuales",
            style = MaterialTheme.typography.titleMedium)
        Text(targets.caloriesKcal.toString() + " kcal/día")
        Text("Proteínas: " + targets.proteinGrams.formatted() + " g")
        Text("Carbohidratos: " + targets.carbsGrams.formatted() + " g")
        Text("Grasas: " + targets.fatGrams.formatted() + " g")
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continuar al inicio") }
    }
}
