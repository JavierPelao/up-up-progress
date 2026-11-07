package com.upupprogress.app.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.upupprogress.app.ui.theme.UpUpProgressTheme

internal val OnboardingFormSaver = listSaver<OnboardingFormState, Any>(
    save = { it.toSavedValues() },
    restore = { restoreOnboardingForm(it) },
)

/** Pasos visuales del mismo borrador. Solo el último paso permite confirmar. */
@Composable
fun OnboardingScreen(
    form: OnboardingFormState,
    onFormChange: (OnboardingFormState) -> Unit,
    step: OnboardingStep,
    onStepChange: (OnboardingStep) -> Unit,
    onBackToAccount: () -> Unit,
    onConfirm: (OnboardingFormState) -> Unit,
    modifier: Modifier = Modifier,
    email: String = "",
    onEditAccount: () -> Unit = onBackToAccount,
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    var attempted by rememberSaveable(step) { mutableStateOf(false) }
    var editingSection by rememberSaveable { mutableStateOf<OnboardingStep?>(null) }
    var numberPickerKey by rememberSaveable { mutableStateOf<String?>(null) }
    val errors = if (attempted) validateOnboardingStep(form, step) else emptyMap()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scroll = rememberScrollState()

    fun closeKeyboard() {
        focusManager.clearFocus()
        keyboard?.hide()
    }
    fun goBack() {
        closeKeyboard()
        when {
            editingSection != null -> editingSection = null
            step == OnboardingStep.PERSONAL -> onBackToAccount()
            else -> onStepChange(OnboardingStep.entries[step.ordinal - 1])
        }
    }
    fun openPicker(field: OnboardingNumberField) {
        closeKeyboard()
        numberPickerKey = field.name
    }
    BackHandler(enabled = !isLoading) { goBack() }
    LaunchedEffect(step) {
        closeKeyboard()
        scroll.scrollTo(0)
    }

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding().imePadding().verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            LinearProgressIndicator(
                progress = { (step.ordinal + 2) / 6f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .semantics { contentDescription = "Paso ${step.ordinal + 2} de 6" },
            )
            Text(step.title, style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() })
            Text(step.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isLoading) {
                CircularProgressIndicator()
                Text("Creando tu cuenta…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } else {
                if (step == OnboardingStep.REVIEW) {
                    OnboardingReview(form, email, errors, editingSection,
                        onEditSection = { closeKeyboard(); editingSection = it },
                        onEditAccount = onEditAccount,
                        onFormChange = onFormChange,
                        openNumberPicker = ::openPicker)
                } else {
                    OnboardingSection(step.title) {
                        OnboardingStepFields(step, form, errors, onFormChange, ::openPicker)
                    }
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (errors.isNotEmpty()) Text("Revisa los campos indicados antes de continuar.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (step == OnboardingStep.REVIEW) {
                    Text("Por ahora solo se creará tu cuenta. Los datos personales y las metas aún no se guardarán.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = {
                        closeKeyboard()
                        attempted = true
                        if (validateOnboardingStep(form, step).isEmpty()) {
                            if (step == OnboardingStep.REVIEW) onConfirm(form)
                            else onStepChange(OnboardingStep.entries[step.ordinal + 1])
                        } else if (step == OnboardingStep.REVIEW) {
                            editingSection = firstStepWithErrors(form)?.takeIf { it != OnboardingStep.REVIEW }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(if (step == OnboardingStep.REVIEW) "Confirmar y crear cuenta" else "Continuar") }
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
                onFormChange(when (field) {
                    OnboardingNumberField.AGE -> form.copy(ageYears = value)
                    OnboardingNumberField.HEIGHT -> form.copy(heightCm = value)
                    OnboardingNumberField.WEIGHT -> form.copy(weightHundredthsKg = value)
                })
                numberPickerKey = null
            },
        )
    }
}

@Composable
internal fun OnboardingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
internal fun NumericSelection(
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
internal fun SingleChoiceOption(label: String, selected: Boolean, description: String? = null, onClick: () -> Unit) {
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
internal fun FieldError(message: String?) {
    if (message != null) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}


@Preview(name = "Datos personales - Pixel 7", device = Devices.PIXEL_7, showSystemUi = true, locale = "es-rCL")
@Preview(name = "Datos personales - texto grande", device = Devices.PIXEL_7, fontScale = 1.3f, showSystemUi = true)
@Composable
private fun OnboardingPreview() {
    OnboardingStepPreview(OnboardingStep.PERSONAL)
}

@Preview(name = "Actividad física - Pixel 7", device = Devices.PIXEL_7, showSystemUi = true, locale = "es-rCL")
@Composable
private fun ActivityStepPreview() { OnboardingStepPreview(OnboardingStep.ACTIVITY) }

@Preview(name = "Resumen editable - Pixel 7", device = Devices.PIXEL_7, showSystemUi = true, locale = "es-rCL")
@Composable
private fun ReviewStepPreview() { OnboardingStepPreview(OnboardingStep.REVIEW) }

@Composable
private fun OnboardingStepPreview(initialStep: OnboardingStep) {
    var form by rememberSaveable(stateSaver = OnboardingFormSaver) {
        mutableStateOf(OnboardingFormState(name = "Ejemplo", sexForFormula = FormulaSexUi.FEMALE,
            ageYears = 34, heightCm = 162, weightHundredthsKg = 6050,
            activityLevel = ActivityLevelUi.LIGHTLY_ACTIVE, personalGoal = PersonalGoalUi.LOSE_WEIGHT,
            nutritionGoalMode = NutritionGoalModeUi.AUTOMATIC))
    }
    var step by rememberSaveable { mutableStateOf(initialStep) }
    var reviewed by remember { mutableStateOf(false) }
    UpUpProgressTheme {
        OnboardingScreen(form, { form = it }, step, { step = it }, {},
            onConfirm = { reviewed = true },
            email = "ejemplo@correo.cl",
            errorMessage = if (reviewed) "Revisión local completada. No se han enviado datos." else null)
    }
}
