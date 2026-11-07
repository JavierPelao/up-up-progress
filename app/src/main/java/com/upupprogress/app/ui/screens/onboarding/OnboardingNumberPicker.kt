package com.upupprogress.app.ui.screens.onboarding

import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.math.BigDecimal

enum class OnboardingNumberField(val title: String) {
    AGE("Edad en años"), HEIGHT("Estatura en centímetros"), WEIGHT("Peso en kilogramos")
}

internal fun formatOnboardingWeight(hundredthsKg: Int): String =
    BigDecimal.valueOf(hundredthsKg.toLong(), 2).toPlainString().replace('.', ',')

@Composable
internal fun OnboardingNumberPicker(
    field: OnboardingNumberField,
    initialValue: Int?,
    onDismiss: () -> Unit,
    onValueSelected: (Int) -> Unit
) {
    val isWeight = field == OnboardingNumberField.WEIGHT
    val range = when (field) {
        OnboardingNumberField.AGE ->
            OnboardingInputLimits.MIN_AGE_YEARS..OnboardingInputLimits.MAX_AGE_YEARS
        OnboardingNumberField.HEIGHT ->
            OnboardingInputLimits.MIN_HEIGHT_CM..OnboardingInputLimits.MAX_HEIGHT_CM
        OnboardingNumberField.WEIGHT ->
            OnboardingInputLimits.MIN_WEIGHT_HUNDREDTHS_KG..OnboardingInputLimits.MAX_WEIGHT_HUNDREDTHS_KG
    }
    // Solo posiciones iniciales de las ruedas; no se asignan al formulario hasta pulsar Elegir.
    val start = (initialValue ?: when (field) {
        OnboardingNumberField.AGE -> 25
        OnboardingNumberField.HEIGHT -> 170
        OnboardingNumberField.WEIGHT -> 7_000
    }).coerceIn(range)
    var whole by rememberSaveable(field) {
        mutableIntStateOf(if (isWeight) start / 100 else start)
    }
    var decimals by rememberSaveable(field) {
        mutableIntStateOf(if (isWeight) start % 100 else 0)
    }
    val selectedValue = if (isWeight) whole * 100 + decimals else whole
    val validSelection = selectedValue in range

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Desliza las ruedas para seleccionar el valor.")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberWheel(
                        label = if (isWeight) "kg" else if (field == OnboardingNumberField.AGE) "Años" else "cm",
                        value = whole,
                        range = if (isWeight) range.first / 100..range.last / 100 else range,
                        onValueChanged = { whole = it }
                    )
                    if (isWeight) {
                        Text(",", style = MaterialTheme.typography.headlineMedium)
                        NumberWheel(
                            label = "Decimales",
                            value = decimals,
                            range = 0..99,
                            padded = true,
                            onValueChanged = { decimals = it }
                        )
                    }
                }
                if (!validSelection) {
                    Text(
                        "Selecciona un peso mayor que cero y de hasta ${formatOnboardingWeight(range.last)} kg.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onValueSelected(selectedValue) },
                enabled = validSelection
            ) { Text("Elegir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun NumberWheel(
    label: String,
    value: Int,
    range: IntRange,
    padded: Boolean = false,
    onValueChanged: (Int) -> Unit
) {
    val latestOnValueChanged by rememberUpdatedState(onValueChanged)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = range.first
                    maxValue = range.last
                    this.value = value
                    wrapSelectorWheel = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    contentDescription = label
                    setFormatter { number ->
                        if (padded) number.toString().padStart(2, '0') else number.toString()
                    }
                    setOnValueChangedListener { _, _, newValue -> latestOnValueChanged(newValue) }
                }
            },
            update = { picker -> if (picker.value != value) picker.value = value },
            modifier = Modifier.width(104.dp).height(156.dp)
        )
    }
}
