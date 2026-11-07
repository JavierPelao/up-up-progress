package com.upupprogress.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Las mismas entradas se reutilizan en cada paso y al editar el resumen. */
@Composable
internal fun OnboardingStepFields(
    step: OnboardingStep,
    form: OnboardingFormState,
    errors: Map<OnboardingField, String>,
    onFormChange: (OnboardingFormState) -> Unit,
    openNumberPicker: (OnboardingNumberField) -> Unit,
) {
    when (step) {
        OnboardingStep.PERSONAL -> {
        Text("Sexo biológico para la fórmula *", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Se utiliza únicamente para elegir los coeficientes disponibles de la ecuación Mifflin–St Jeor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FormulaSexUi.entries.forEach { option ->
                FilterChip(
                    selected = form.sexForFormula == option,
                    onClick = { onFormChange(form.copy(sexForFormula = option)) },
                    label = { Text(option.label) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                )
            }
        }
        FieldError(errors[OnboardingField.FORMULA_SEX])
        NumericSelection(
            label = "Edad (años) *",
            value = form.ageYears?.let { "$it años" },
            errorMessage = errors[OnboardingField.AGE],
            onClick = { openNumberPicker(OnboardingNumberField.AGE) }
        )
        NumericSelection(
            label = "Estatura (cm) *",
            value = form.heightCm?.let { "$it cm" },
            errorMessage = errors[OnboardingField.HEIGHT],
            onClick = { openNumberPicker(OnboardingNumberField.HEIGHT) }
        )
        NumericSelection(
            label = "Peso (kg) *",
            value = form.weightHundredthsKg?.let { "${formatOnboardingWeight(it)} kg" },
            errorMessage = errors[OnboardingField.WEIGHT],
            onClick = { openNumberPicker(OnboardingNumberField.WEIGHT) }
        )
        }
        OnboardingStep.ACTIVITY -> {
            ActivityLevelUi.entries.forEach { option ->
                SingleChoiceOption(
                    label = option.label,
                    description = option.description(),
                    selected = form.activityLevel == option,
                    onClick = { onFormChange(form.copy(activityLevel = option)) },
                )
            }
            FieldError(errors[OnboardingField.ACTIVITY])
        }
        OnboardingStep.GOAL -> {
            listOf(PersonalGoalUi.GAIN_MUSCLE_OR_WEIGHT, PersonalGoalUi.MAINTAIN_WEIGHT,
                PersonalGoalUi.LOSE_WEIGHT).forEach { option ->
                SingleChoiceOption(
                    label = option.label,
                    selected = form.personalGoal == option,
                    onClick = { onFormChange(form.copy(personalGoal = option)) },
                )
            }
            FieldError(errors[OnboardingField.GOAL])
        }
        OnboardingStep.NUTRITION -> OnboardingNutritionFields(
            form = form,
            errors = errors,
            onModeSelected = { onFormChange(form.copy(nutritionGoalMode = it)) },
            onManualInputChanged = { field, value ->
                onFormChange(form.copy(manualNutritionDraft = form.manualNutritionDraft.withValue(field, value)))
            },
        )
        OnboardingStep.REVIEW -> Unit
    }
}
