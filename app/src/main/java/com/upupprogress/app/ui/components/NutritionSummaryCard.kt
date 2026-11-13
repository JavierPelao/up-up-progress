package com.upupprogress.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.model.FoodNutrients
import com.upupprogress.app.data.model.NutritionTargets
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

fun formatFoodNumber(value: BigDecimal?, decimals: Int = 0): String {
    if (value == null) return "—"
    return NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL")).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = 2
        roundingMode = RoundingMode.HALF_UP
    }.format(value)
}

@Composable
fun NutritionSummaryCard(
    consumed: FoodNutrients?,
    targets: NutritionTargets?,
    modifier: Modifier = Modifier,
) {
    val calorieGoal = targets?.caloriesKcal?.toBigDecimal()
    val remaining = if (calorieGoal != null && consumed?.calories != null) calorieGoal - consumed.calories else null
    Card(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Calorías", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${formatFoodNumber(consumed?.calories)} / ${formatFoodNumber(calorieGoal)}",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    )
                }
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary) {
                    Text(
                        when {
                            remaining == null -> if (targets == null) "Sin meta disponible" else "Consumo sin confirmar"
                            remaining >= BigDecimal.ZERO -> "${formatFoodNumber(remaining)} kcal restantes"
                            else -> "${formatFoodNumber(remaining.abs())} kcal sobre la meta"
                        },
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            NutrientProgress(consumed?.calories, calorieGoal, MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SummaryMacro("Proteínas", consumed?.protein, targets?.proteinGrams,
                    MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                SummaryMacro("Carbohidratos", consumed?.carbs, targets?.carbsGrams,
                    MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                SummaryMacro("Grasas", consumed?.fat, targets?.fatGrams,
                    MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }
            if (consumed != null && !consumed.isComplete) {
                Text("Hay registros con información nutricional incompleta.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SummaryMacro(label: String, consumed: BigDecimal?, goal: BigDecimal?, color: Color, modifier: Modifier) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${formatFoodNumber(consumed)} / ${formatFoodNumber(goal)} g", modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        NutrientProgress(consumed, goal, color)
    }
}

@Composable
private fun NutrientProgress(value: BigDecimal?, goal: BigDecimal?, color: Color) {
    val progress = if (value != null && goal != null && goal > BigDecimal.ZERO)
        (value.toFloat() / goal.toFloat()).coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(
        progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp),
        color = color, trackColor = color.copy(alpha = 0.3f),
        strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
    )
}

@Preview(name = "Resumen nutricional", showBackground = true, widthDp = 390)
@Preview(name = "Resumen compacto", showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun NutritionSummaryPreview() {
    UpUpProgressTheme {
        NutritionSummaryCard(
            FoodNutrients(BigDecimal("1450"), BigDecimal("95"), BigDecimal("165"), BigDecimal("45")),
            NutritionTargets(2200, BigDecimal("140"), BigDecimal("260"), BigDecimal("70")),
            Modifier.padding(16.dp),
        )
    }
}
