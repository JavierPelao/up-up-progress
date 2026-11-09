package com.upupprogress.app.ui.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.upupprogress.app.data.model.*
import com.upupprogress.app.ui.components.formatFoodNumber
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun FoodProductDialog(
    state: FoodDiaryUiState,
    onQuantityChange: (String) -> Unit,
    onMealChange: (MealType) -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
) {
    val product = state.product
    val quantity = parseFoodQuantity(state.quantity)
    val values = quantity?.let { product?.perHundred?.forQuantity(it) }
    var mealMenu by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (!state.isSaving) onCancel() },
        properties = DialogProperties(dismissOnBackPress = !state.isSaving, dismissOnClickOutside = false,
            usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().imePadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    product?.let {
                        Column(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                it.name?.takeIf(String::isNotBlank) ?: "Producto sin nombre",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                textAlign = TextAlign.Center,
                            )
                            it.brand?.takeIf(String::isNotBlank)?.let { brand ->
                                Text(brand, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary, textAlign = TextAlign.Center)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    }
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.isLookingUp) {
                            CircularProgressIndicator()
                            Text("Consultando producto…")
                        }
                        product?.let {
                            Text("Valores de referencia por " + (it.basis?.let { basis -> "100 ${basis.unit}" } ?: "medida no definida"))
                            OutlinedTextField(
                                value = state.quantity, onValueChange = onQuantityChange,
                                label = { Text("Cantidad consumida (${it.basis?.unit ?: "g/ml"})") },
                                enabled = !state.isSaving && !state.saveAttempted, singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = quantity == null,
                                supportingText = if (quantity == null) { { Text("Ingresa una cantidad mayor que cero, con hasta dos decimales.") } } else null,
                            )
                            Text("Para la cantidad (${it.basis?.unit ?: "g/ml"}) seleccionada",
                                style = MaterialTheme.typography.titleSmall)
                            listOf(
                                MaterialTheme.colorScheme.primary to "Calorías: ${formatFoodNumber(values?.calories)} kcal",
                                Color(0xFF4285F4) to "Proteínas: ${formatFoodNumber(values?.protein, 2)} g",
                                Color(0xFF2E7D32) to "Carbohidratos: ${formatFoodNumber(values?.carbs, 2)} g",
                                Color(0xFFF9A825) to "Grasas: ${formatFoodNumber(values?.fat, 2)} g",
                            ).forEach { (color, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(8.dp).background(color, CircleShape))
                                    Text(label)
                                }
                            }
                            Box {
                                OutlinedButton(onClick = { mealMenu = true }, enabled = !state.isSaving && !state.saveAttempted,
                                    modifier = Modifier.fillMaxWidth()) { Text("Comida: ${state.meal.label}") }
                                DropdownMenu(expanded = mealMenu, onDismissRequest = { mealMenu = false }) {
                                    MealType.entries.forEach { meal ->
                                        DropdownMenuItem(text = { Text(meal.label) }, onClick = {
                                            onMealChange(meal); mealMenu = false
                                        })
                                    }
                                }
                            }
                            if (!it.canRecord) Text(
                                "Falta el nombre, la base de medida o algún valor nutricional. No se puede calcular y guardar este producto todavía.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        state.dialogError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (state.isSaving) {
                            CircularProgressIndicator()
                            Text("Guardando alimento…")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onAdd,
                            enabled = product?.canRecord == true && quantity != null && !state.isLookingUp && !state.isSaving,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("Agregar alimento", textAlign = TextAlign.Center) }
                        TextButton(
                            onClick = onCancel, enabled = !state.isSaving,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("Cancelar") }
                    }
                }
            }
        }
    }
}

@Preview(name = "Confirmar alimento", showBackground = true, widthDp = 390, heightDp = 850)
@Preview(name = "Confirmar alimento texto grande", showBackground = true, widthDp = 390, heightDp = 850, fontScale = 1.3f)
@Composable
private fun FoodProductPreview() {
    UpUpProgressTheme {
        FoodProductDialog(
            FoodDiaryUiState(
                pendingDate = LocalDate.of(2026, 11, 8),
                product = FoodProduct(null, "1234567890123", "Leche natural entera", "Marca de ejemplo",
                    NutritionBasis.PER_100_MILLILITERS, FoodNutrients(BigDecimal("80"), BigDecimal("3.2"), BigDecimal("4.8"), BigDecimal("4.5"))),
                quantity = "100", meal = MealType.SNACK,
            ),
            onQuantityChange = {}, onMealChange = {}, onAdd = {}, onCancel = {},
        )
    }
}
