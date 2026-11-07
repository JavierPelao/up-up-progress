package com.upupprogress.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

@Composable
internal fun OnboardingReview(
    form: OnboardingFormState,
    email: String,
    errors: Map<OnboardingField, String>,
    editingSection: OnboardingStep?,
    onEditSection: (OnboardingStep?) -> Unit,
    onEditAccount: () -> Unit,
    onFormChange: (OnboardingFormState) -> Unit,
    openNumberPicker: (OnboardingNumberField) -> Unit,
) {
    OnboardingSection("Tu cuenta") {
        OutlinedTextField(
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it)) },
            label = { Text("Nombre *") },
            singleLine = true,
            isError = errors[OnboardingField.NAME] != null,
            supportingText = errors[OnboardingField.NAME]?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (email.isNotBlank()) Text(email, style = MaterialTheme.typography.bodyMedium)
        Text("La contraseña no se muestra por seguridad.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onEditAccount) { Text("Editar correo o contraseña") }
    }
    OnboardingStep.entries.filter { it != OnboardingStep.REVIEW }.forEach { section ->
        OnboardingSection(section.title) {
            val editing = editingSection == section
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing) "Editando esta sección" else "Tu selección", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { onEditSection(if (editing) null else section) }) {
                    Text(if (editing) "Listo" else "Editar")
                }
            }
            if (editing) {
                OnboardingStepFields(section, form, errors, onFormChange, openNumberPicker)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (section) {
                        OnboardingStep.PERSONAL -> {
                            Text("Sexo para la fórmula: ${form.sexForFormula?.label ?: "Sin seleccionar"}")
                            Text("Edad: ${form.ageYears?.let { "$it años" } ?: "Sin seleccionar"}")
                            Text("Estatura: ${form.heightCm?.let { "$it cm" } ?: "Sin seleccionar"}")
                            Text("Peso: ${form.weightHundredthsKg?.let { formatOnboardingWeight(it) + " kg" } ?: "Sin seleccionar"}")
                        }
                        OnboardingStep.ACTIVITY -> {
                            Text(form.activityLevel?.label ?: "Sin seleccionar")
                            form.activityLevel?.let { Text(it.description(), style = MaterialTheme.typography.bodySmall) }
                        }
                        OnboardingStep.GOAL -> Text(form.personalGoal?.label ?: "Sin seleccionar")
                        OnboardingStep.NUTRITION -> {
                            Text("Modalidad: ${form.nutritionGoalMode?.label ?: "Sin seleccionar"}")
                            val automatic = (form.automaticNutritionResultOrNull() as? AutomaticNutritionResult.Estimated)?.estimate
                            val manual = form.manualNutritionTargetsOrNull()
                            if (automatic != null) {
                                NutritionReviewValues(automatic.caloriesKcal, automatic.proteinGrams,
                                    automatic.carbsGrams, automatic.fatGrams)
                                Text("Estimación orientativa.", style = MaterialTheme.typography.bodySmall)
                            } else if (manual != null) {
                                NutritionReviewValues(manual.caloriesKcal, manual.proteinGrams, manual.carbsGrams, manual.fatGrams)
                            } else Text("Revisa esta sección para completar tus metas.",
                                color = MaterialTheme.colorScheme.error)
                        }
                        OnboardingStep.REVIEW -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun NutritionReviewValues(calories: Int, protein: BigDecimal, carbs: BigDecimal, fat: BigDecimal) {
    fun format(value: BigDecimal) = value.stripTrailingZeros().toPlainString().replace('.', ',')
    Text("$calories kcal/día", style = MaterialTheme.typography.titleMedium)
    Text("Proteínas: ${format(protein)} g")
    Text("Carbohidratos: ${format(carbs)} g")
    Text("Grasas: ${format(fat)} g")
}
