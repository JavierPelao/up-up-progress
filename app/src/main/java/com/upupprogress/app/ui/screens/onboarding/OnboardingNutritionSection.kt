package com.upupprogress.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import java.math.BigDecimal

@Composable
internal fun OnboardingNutritionFields(
    form: OnboardingFormState,
    errors: Map<OnboardingField, String>,
    onModeSelected: (NutritionGoalModeUi) -> Unit,
    onManualInputChanged: (ManualNutritionField, String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun closeKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    Text("Modalidad de metas nutricionales *", style = MaterialTheme.typography.bodyMedium)
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NutritionGoalModeUi.entries.forEach { option ->
            FilterChip(
                selected = form.nutritionGoalMode == option,
                onClick = {
                    closeKeyboard()
                    onModeSelected(option)
                },
                label = { Text(option.label) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            )
        }
    }
    FieldError(errors[OnboardingField.NUTRITION_MODE])

    when (form.nutritionGoalMode) {
        null -> Text(
            "Elige cómo quieres definir tus metas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        NutritionGoalModeUi.AUTOMATIC -> AutomaticNutritionSummary(
            result = form.automaticNutritionResultOrNull()
        )
        NutritionGoalModeUi.MANUAL -> {
            val draftErrors = validateManualNutritionDraft(form.manualNutritionDraft)
            ManualNutritionField.entries.forEach { field ->
                val value = form.manualNutritionDraft[field]
                val error = errors[field.toOnboardingField()]
                    ?: draftErrors[field].takeIf { value.isNotEmpty() }
                OutlinedTextField(
                    value = value,
                    onValueChange = { edited ->
                        if (acceptsManualNutritionEdit(field, edited)) {
                            onManualInputChanged(field, edited)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("${field.label} (${field.unit}) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = if (field == ManualNutritionField.CALORIES) {
                            KeyboardType.Number
                        } else KeyboardType.Decimal,
                        imeAction = if (field == ManualNutritionField.FAT) ImeAction.Done
                            else ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onDone = { closeKeyboard() }),
                    isError = error != null,
                    supportingText = {
                        Text(error ?: if (field == ManualNutritionField.CALORIES) {
                            "De ${ManualNutritionInputLimits.MIN_CALORIES_KCAL} a ${ManualNutritionInputLimits.MAX_CALORIES_KCAL} kcal, sin decimales."
                        } else {
                            "De 0 a ${ManualNutritionInputLimits.MAX_MACRO_GRAMS} g. Hasta dos decimales, con coma o punto."
                        })
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun AutomaticNutritionSummary(result: AutomaticNutritionResult?) {
    when (result) {
        null -> Text(
            "Completa los datos de la fórmula, tu actividad y tu objetivo para ver una estimación.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        is AutomaticNutritionResult.Unavailable -> Text(
            automaticNutritionIssueMessage(result.issue),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        is AutomaticNutritionResult.Estimated -> {
            val estimate = result.estimate
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Metas estimadas", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${estimate.caloriesKcal} kcal/día",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Proteínas: ${formatAutomaticMacro(estimate.proteinGrams)} g")
                Text("Carbohidratos: ${formatAutomaticMacro(estimate.carbsGrams)} g")
                Text("Grasas: ${formatAutomaticMacro(estimate.fatGrams)} g")
                Text(
                    "Estas metas son orientativas y pueden variar según tus necesidades. Todavía no se han guardado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatAutomaticMacro(grams: BigDecimal): String =
    grams.toPlainString().replace('.', ',')

@Composable
private fun NutritionPreviewContent(
    mode: NutritionGoalModeUi,
    initialForm: OnboardingFormState = OnboardingFormState(),
) {
    var form by remember {
        mutableStateOf(initialForm.copy(nutritionGoalMode = mode))
    }
    UpUpProgressTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Configuración nutricional", style = MaterialTheme.typography.titleLarge)
                OnboardingNutritionFields(
                    form = form,
                    errors = emptyMap(),
                    onModeSelected = { form = form.copy(nutritionGoalMode = it) },
                    onManualInputChanged = { field, value ->
                        form = form.copy(
                            manualNutritionDraft = form.manualNutritionDraft.withValue(field, value)
                        )
                    }
                )
            }
        }
    }
}

@Preview(name = "Manual - ahora", device = Devices.PIXEL_7, showSystemUi = true, locale = "es-rCL")
@Preview(name = "Manual - texto grande", device = Devices.PIXEL_7, showSystemUi = true, fontScale = 1.3f, locale = "es-rCL")
@Composable
private fun ManualNutritionPreview() {
    NutritionPreviewContent(NutritionGoalModeUi.MANUAL)
}

@Preview(name = "Automático", device = Devices.PIXEL_7, showSystemUi = true, locale = "es-rCL")
@Composable
private fun AutomaticNutritionPreview() {
    NutritionPreviewContent(NutritionGoalModeUi.AUTOMATIC)
}

@Preview(name = "Automático - estimación", device = Devices.PIXEL_7, showSystemUi = true, locale = "es-rCL")
@Preview(name = "Automático - texto grande", device = Devices.PIXEL_7, showSystemUi = true, fontScale = 1.3f, locale = "es-rCL")
@Composable
private fun AutomaticNutritionEstimatePreview() {
    NutritionPreviewContent(
        mode = NutritionGoalModeUi.AUTOMATIC,
        initialForm = OnboardingFormState(
            sexForFormula = FormulaSexUi.MALE,
            ageYears = 30,
            heightCm = 175,
            weightHundredthsKg = 7_000,
            activityLevel = ActivityLevelUi.ACTIVE,
            personalGoal = PersonalGoalUi.MAINTAIN_WEIGHT,
        )
    )
}

@Preview(name = "Automático - fuera del alcance", device = Devices.PIXEL_7, showSystemUi = true, locale = "es-rCL")
@Composable
private fun AutomaticNutritionUnavailablePreview() {
    NutritionPreviewContent(
        mode = NutritionGoalModeUi.AUTOMATIC,
        initialForm = OnboardingFormState(
            sexForFormula = FormulaSexUi.FEMALE,
            ageYears = 78,
            heightCm = 100,
            weightHundredthsKg = 3_000,
            activityLevel = ActivityLevelUi.SEDENTARY,
            personalGoal = PersonalGoalUi.MAINTAIN_WEIGHT,
        )
    )
}
