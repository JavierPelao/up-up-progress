package com.upupprogress.app.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.model.NutritionTargets
import com.upupprogress.app.data.model.OnboardingStatus
import com.upupprogress.app.ui.screens.onboarding.ActivityLevelUi
import com.upupprogress.app.ui.screens.onboarding.FormulaSexUi
import com.upupprogress.app.ui.screens.onboarding.PersonalGoalUi
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Solo consulta de datos confirmados. Las credenciales no forman parte del perfil. */
@Composable
fun ProfileScreen(
    status: OnboardingStatus,
    email: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    isSigningOut: Boolean = false,
) {
    fun BigDecimal.displayNumber() = stripTrailingZeros().toPlainString().replace('.', ',')
    fun BigDecimal.displayGrams() = setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileSection("Datos de cuenta") {
                ProfileValue("Nombre", status.displayName)
                ProfileValue("Correo electrónico", email)
            }
            ProfileSection("Datos físicos") {
                ProfileValue("Sexo para la fórmula",
                    FormulaSexUi.entries.firstOrNull { it.backendValue == status.sexForFormula }?.label)
                ProfileValue("Edad", status.ageYears?.let { "$it años" })
                ProfileValue("Estatura", status.heightCm?.let { "${it.displayNumber()} cm" })
                ProfileValue("Último peso registrado", status.weightKg?.let { "${it.displayNumber()} kg" })
                ProfileValue("Fecha del peso", status.measuredOn?.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")))
            }
            ProfileSection("Actividad y objetivo") {
                ProfileValue("Actividad física",
                    ActivityLevelUi.entries.firstOrNull { it.backendValue == status.activityLevel }?.label)
                ProfileValue("Objetivo personal",
                    PersonalGoalUi.entries.firstOrNull { it.backendValue == status.goalType }?.label)
            }
            ProfileSection("Configuración nutricional") {
                ProfileValue("Modalidad", when (status.nutritionGoalMode) {
                    "manual" -> "Manual"
                    "automatic" -> "Automática"
                    else -> null
                })
                ProfileValue("Calorías diarias", status.targets?.caloriesKcal?.let { "$it kcal" })
                ProfileValue("Proteínas", status.targets?.proteinGrams?.let { "${it.displayGrams()} g" })
                ProfileValue("Carbohidratos", status.targets?.carbsGrams?.let { "${it.displayGrams()} g" })
                ProfileValue("Grasas", status.targets?.fatGrams?.let { "${it.displayGrams()} g" })
            }
            OutlinedButton(
                onClick = onSignOut, enabled = !isSigningOut,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (isSigningOut) "Cerrando sesión…" else "Cerrar sesión") }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun ProfileValue(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.takeIf { it.isNotBlank() } ?: "No disponible", style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(name = "Perfil", showBackground = true, widthDp = 390, heightDp = 1400)
@Preview(name = "Perfil compacto", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun ProfilePreview() {
    UpUpProgressTheme {
        ProfileScreen(
            status = OnboardingStatus(
                isComplete = true, displayName = "Ejemplo", ageYears = 30,
                heightCm = BigDecimal("175"), sexForFormula = "male",
                weightKg = BigDecimal("70"), measuredOn = LocalDate.of(2026, 11, 8),
                activityLevel = "high", goalType = "maintain_weight", nutritionGoalMode = "automatic",
                targets = NutritionTargets(2473, BigDecimal("126.00"), BigDecimal("306.78"), BigDecimal("82.43")),
            ),
            email = "ejemplo@example.invalid", onSignOut = {},
        )
    }
}
