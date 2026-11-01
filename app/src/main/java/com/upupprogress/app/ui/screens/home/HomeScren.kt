package com.upupprogress.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var mostrarAviso by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "UP UP PROGRESS!",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "¡Hola!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Cada registro cuenta.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        WorkoutTodayCard(
            onViewRoutine = {
                mostrarAviso = true
            }
        )

        Column(
            modifier = Modifier.padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Datos de ejemplo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NutritionTodaySection(
                calories = 1450,
                calorieGoal = 2200,
                proteinGrams = 95,
                proteinGoal = 140,
                carbsGrams = 165,
                carbsGoal = 260,
                fatGrams = 45,
                fatGoal = 70
            )
        }

        QuickActionsSection(
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}


@Composable
private fun WorkoutTodayCard(
    onViewRoutine: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "RUTINA DEL LUNES",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primaryContainer
            )

            Text(
                text = "Empuje",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )

            Text(
                text = "Lunes · Pecho, hombros y tríceps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primaryContainer
            )

            Text(
                text = "4 ejercicios",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )

            Button(
                onClick = onViewRoutine,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Ver rutina y registrar")
            }
        }
    }
}
@Composable
private fun NutritionTodaySection(
    calories: Int,
    calorieGoal: Int,
    proteinGrams: Int,
    proteinGoal: Int,
    carbsGrams: Int,
    carbsGoal: Int,
    fatGrams: Int,
    fatGoal: Int
) {
    val remaining = calorieGoal - calories
    val numberFormat = remember {
        NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CL"))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Nutrición de hoy",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(
                onClick = {
                    // TODO: Navegar a la pantalla de detalle de nutrición.
                },
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(
                    text = "Ver detalle →",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = numberFormat.format(calories),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (calorieGoal > 0) {
                                "de ${numberFormat.format(calorieGoal)} kcal"
                            } else {
                                "kcal · Meta no configurada"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (calorieGoal > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = if (remaining >= 0) {
                                    "${numberFormat.format(remaining)} kcal restantes"
                                } else {
                                    "${numberFormat.format(-remaining)} kcal sobre la meta"
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (calorieGoal > 0) {
                    NutritionProgressBar(
                        value = calories,
                        goal = calorieGoal,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MacroItem(
                        label = "Proteínas",
                        grams = proteinGrams,
                        goalGrams = proteinGoal,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MacroItem(
                        label = "Carbohidratos",
                        grams = carbsGrams,
                        goalGrams = carbsGoal,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    MacroItem(
                        label = "Grasas",
                        grams = fatGrams,
                        goalGrams = fatGoal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

}

@Composable
private fun MacroItem(
    label: String,
    grams: Int,
    goalGrams: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (goalGrams > 0) "$grams / $goalGrams g" else "$grams g",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (goalGrams > 0) {
            NutritionProgressBar(value = grams, goal = goalGrams, color = color)
        }
    }
}

@Composable
private fun NutritionProgressBar(
    value: Int,
    goal: Int,
    color: Color
) {
    val progress = if (goal > 0) {
        (value.toFloat() / goal).coerceIn(0f, 1f)
    } else {
        0f
    }

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        color = color,
        trackColor = color.copy(alpha = 0.3f),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

@Composable
private fun QuickActionsSection(modifier: Modifier = Modifier) {
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
            subtitle = "Buscar o escanear",
            onClick = { selectedAction = "Registrar alimento" },
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
                    if (action == "Registrar alimento") {
                        "Próximamente podrás buscar o escanear alimentos para registrar tu consumo."
                    } else {
                        "Próximamente podrás registrar tu peso y consultar tu evolución."
                    }
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
        Column(modifier = Modifier.padding(16.dp)) {
            NutritionTodaySection(
                calories = 1450,
                calorieGoal = 2200,
                proteinGrams = 95,
                proteinGoal = 140,
                carbsGrams = 165,
                carbsGoal = 260,
                fatGrams = 45,
                fatGoal = 70
            )
            QuickActionsSection(
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}