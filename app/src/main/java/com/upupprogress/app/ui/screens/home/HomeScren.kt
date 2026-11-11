package com.upupprogress.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.model.*
import com.upupprogress.app.ui.components.NutritionSummaryCard
import com.upupprogress.app.ui.screens.nutrition.FoodDiaryStatus
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import java.math.BigDecimal

@Composable
fun HomeScreen(
    targets: NutritionTargets,
    diary: FoodDay?,
    isNutritionLoading: Boolean,
    nutritionError: String?,
    onRetryNutrition: () -> Unit,
    onRegisterFood: () -> Unit,
    onCreateRoutine: () -> Unit,
    onViewNutrition: () -> Unit,
    modifier: Modifier = Modifier,
    hasRoutines: Boolean = false,
    todayRoutineName: String? = null,
    onOpenTraining: () -> Unit = onCreateRoutine,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("¡Hola!", style = MaterialTheme.typography.headlineMedium)
        Text("Cada registro cuenta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        WorkoutTodayCard(onCreateRoutine, onOpenTraining, hasRoutines, todayRoutineName)
        Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Nutrición de hoy", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onViewNutrition) { Text("Ver detalle →") }
            }
            NutritionSummaryCard(diary?.consumed, if (diary != null) diary.targets else targets)
            FoodDiaryStatus(isNutritionLoading, nutritionError, onRetryNutrition)
        }
        QuickActionsSection(onRegisterFood, Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun WorkoutTodayCard(
    onCreateRoutine: () -> Unit,
    onOpenTraining: () -> Unit,
    hasRoutines: Boolean,
    todayRoutineName: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Rutina de hoy",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = todayRoutineName ?: if (hasRoutines) "Selecciona una rutina para realizar hoy." else "Tu rutina aún no ha sido creada, pincha el botón de abajo para crear rutina.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Button(
                onClick = if (hasRoutines || todayRoutineName != null) onOpenTraining else onCreateRoutine,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) { Text(if (todayRoutineName != null) "Ver entrenamiento" else if (hasRoutines) "Elegir rutina" else "Crear rutina") }
        }
    }
}


@Composable
private fun QuickActionsSection(onRegisterFood: () -> Unit, modifier: Modifier = Modifier) {
    var selectedAction by rememberSaveable { mutableStateOf<String?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionCard(
            symbol = "+",
            title = "Registrar alimento",
            subtitle = "Escanear código",
            onClick = onRegisterFood,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        QuickActionCard(
            symbol = "↗",
            title = "Registrar peso",
            subtitle = "Ver tu evolución",
            onClick = { selectedAction = "Registrar peso" },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }

    selectedAction?.let { action ->
        AlertDialog(
            onDismissRequest = { selectedAction = null },
            title = { Text(action) },
            text = {
                Text(
"Próximamente podrás registrar tu peso y consultar tu evolución."
                )
            },
            confirmButton = {
                TextButton(onClick = { selectedAction = null }) {
                    Text("Entendido")
                }
            }
        )
    }
}

@Composable
private fun QuickActionCard(
    symbol: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = symbol,
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Preview(showBackground = true, backgroundColor = 0xFFFFF7ED, widthDp = 360)
@Composable
private fun NutritionTodayPreview() {
    UpUpProgressTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NutritionSummaryCard(
                FoodNutrients(BigDecimal("1450"), BigDecimal("95"), BigDecimal("165"), BigDecimal("45")),
                NutritionTargets(2200, BigDecimal("140"), BigDecimal("260"), BigDecimal("70")),
            )
            QuickActionsSection(onRegisterFood = {})
        }
    }
}
