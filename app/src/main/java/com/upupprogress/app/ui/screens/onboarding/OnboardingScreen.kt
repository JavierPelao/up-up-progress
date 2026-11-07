package com.upupprogress.app.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upupprogress.app.ui.theme.UpUpProgressTheme

// Conserva el borrador de la interfaz al recrearse la pantalla. No guarda datos en Supabase.
private val OnboardingFormSaver = listSaver<OnboardingFormState, Any>(
    save = { it.toSavedValues() },
    restore = { restoreOnboardingForm(it) }
)

/** Formulario con modalidades nutricionales y validación local, sin guardado ni navegación. */
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    var form by rememberSaveable(stateSaver = OnboardingFormSaver) {
        mutableStateOf(OnboardingFormState())
    }
    var validationAttempted by rememberSaveable { mutableStateOf(false) }
    var numberPickerKey by rememberSaveable { mutableStateOf<String?>(null) }
    val errors = if (validationAttempted) validateOnboardingForm(form) else emptyMap()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun openNumberPicker(field: OnboardingNumberField) {
        focusManager.clearFocus()
        keyboardController?.hide()
        numberPickerKey = field.name
    }

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
            verticalArrangement = Arrangement.spacedBy(20.dp)
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
                        "↑",
                        modifier = Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(
                    "Up Up Progress!",
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Configuración inicial",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Cuéntanos sobre ti para preparar tu perfil y tus metas.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Los campos con * son obligatorios.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OnboardingSection("Datos personales") {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }),
                    isError = errors[OnboardingField.NAME] != null,
                    supportingText = errors[OnboardingField.NAME]?.let { { Text(it) } },
                    shape = RoundedCornerShape(12.dp)
                )
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
                            onClick = { form = form.copy(sexForFormula = option) },
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

            OnboardingSection("Actividad y objetivo") {
                Text("Actividad física *", style = MaterialTheme.typography.bodyMedium)
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActivityLevelUi.entries.forEach { option ->
                        SingleChoiceOption(
                            label = option.label,
                            selected = form.activityLevel == option,
                            onClick = { form = form.copy(activityLevel = option) }
                        )
                    }
                }
                FieldError(errors[OnboardingField.ACTIVITY])
                Text("Objetivo personal *", style = MaterialTheme.typography.bodyMedium)
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PersonalGoalUi.entries.forEach { option ->
                        SingleChoiceOption(
                            label = option.label,
                            selected = form.personalGoal == option,
                            onClick = { form = form.copy(personalGoal = option) }
                        )
                    }
                }
                FieldError(errors[OnboardingField.GOAL])
            }

            OnboardingSection("Configuración nutricional") {
                OnboardingNutritionFields(
                    form = form,
                    errors = errors,
                    onModeSelected = { form = form.copy(nutritionGoalMode = it) },
                    onManualSetupSelected = { form = form.copy(manualGoalsSetup = it) },
                    onManualInputChanged = { field, value ->
                        form = form.copy(
                            manualNutritionDraft = form.manualNutritionDraft.withValue(field, value)
                        )
                    }
                )
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    validationAttempted = true
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Revisar datos") }
            if (validationAttempted) {
                Text(
                    text = when {
                        errors.isNotEmpty() ->
                            "Revisa los campos indicados. Los datos todavía no se han guardado."
                        form.nutritionGoalMode == NutritionGoalModeUi.AUTOMATIC ->
                            "Datos revisados. Las metas mostradas son estimadas y todavía no se han guardado."
                        form.manualGoalsSetup == ManualGoalsSetupUi.LATER ->
                            "Datos revisados. Tus metas siguen pendientes. La configuración todavía no se ha guardado."
                        else -> "Datos revisados. Tus metas manuales todavía no se han guardado."
                    },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (errors.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                )
            }
        }
    }

    OnboardingNumberField.entries.firstOrNull { it.name == numberPickerKey }?.let { field ->
        OnboardingNumberPicker(
            field = field,
            initialValue = when (field) {
                OnboardingNumberField.AGE -> form.ageYears
                OnboardingNumberField.HEIGHT -> form.heightCm
                OnboardingNumberField.WEIGHT -> form.weightHundredthsKg
            },
            onDismiss = { numberPickerKey = null },
            onValueSelected = { value ->
                form = when (field) {
                    OnboardingNumberField.AGE -> form.copy(ageYears = value)
                    OnboardingNumberField.HEIGHT -> form.copy(heightCm = value)
                    OnboardingNumberField.WEIGHT -> form.copy(weightHundredthsKg = value)
                }
                numberPickerKey = null
            }
        )
    }
}

@Composable
private fun OnboardingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun NumericSelection(
    label: String,
    value: String?,
    errorMessage: String?,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (errorMessage != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: "Seleccionar", style = MaterialTheme.typography.bodyLarge)
        }
    }
    FieldError(errorMessage)
}

@Composable
internal fun SingleChoiceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun FieldError(message: String?) {
    if (message != null) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Preview(name = "Onboarding - Pixel 7", device = Devices.PIXEL_7, showSystemUi = true, locale = "es-rCL")
@Preview(name = "Onboarding - texto grande", device = Devices.PIXEL_7, showSystemUi = true, fontScale = 1.3f, locale = "es-rCL")
@Composable
private fun OnboardingPreview() {
    UpUpProgressTheme { OnboardingScreen() }
}
