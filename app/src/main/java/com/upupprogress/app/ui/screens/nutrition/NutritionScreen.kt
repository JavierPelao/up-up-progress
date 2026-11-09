package com.upupprogress.app.ui.screens.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.model.*
import com.upupprogress.app.ui.components.NutritionSummaryCard
import com.upupprogress.app.ui.components.formatFoodNumber
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    state: FoodDiaryUiState,
    onDateSelected: (LocalDate) -> Unit,
    onRegisterFood: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var calendarOpen by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDateSelected(state.selectedDate.minusDays(1)) },
                    modifier = Modifier.semantics { contentDescription = "Día anterior" }) { Text("‹") }
                TextButton(onClick = { calendarOpen = true }, modifier = Modifier.weight(1f)) {
                    Text(state.selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("es-CL"))))
                }
                IconButton(onClick = { onDateSelected(state.selectedDate.plusDays(1)) },
                    enabled = state.selectedDate < state.todayDate,
                    modifier = Modifier.semantics { contentDescription = "Día siguiente" }) { Text("›") }
                TextButton(onClick = { onDateSelected(state.todayDate) }, enabled = state.selectedDate != state.todayDate) { Text("Hoy") }
            }
            NutritionSummaryCard(state.selectedDay?.consumed, state.selectedDay?.targets)
            FoodDiaryStatus(state.isLoading, state.error, onRefresh)
            Button(
                onClick = onRegisterFood, enabled = state.selectedDay != null && !state.isLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp),
            ) { Text("+ Registrar alimento") }
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Alimentos consumidos", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                    when {
                        state.selectedDay == null -> Text(if (state.isLoading) "Cargando alimentos…" else "No se pudo consultar esta fecha.")
                        state.selectedDay.entries.isEmpty() -> Text("No hay alimentos registrados para esta fecha.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> state.selectedDay.entries.forEach { FoodEntryCard(it) }
                    }
                }
            }
        }
    }
    if (calendarOpen) {
        val picker = key(state.todayDate) { rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 1900..state.todayDate.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= state.todayDate
            },
        ) }
        DatePickerDialog(
            onDismissRequest = { calendarOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        onDateSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    calendarOpen = false
                }, enabled = picker.selectedDateMillis != null) { Text("Ver fecha") }
            },
            dismissButton = { TextButton(onClick = { calendarOpen = false }) { Text("Cancelar") } },
        ) { DatePicker(picker) }
    }
}

@Composable
fun FoodDiaryStatus(isLoading: Boolean, error: String?, onRefresh: () -> Unit) {
    if (isLoading) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("Actualizando alimentos…", style = MaterialTheme.typography.bodySmall)
        }
    }
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRefresh, enabled = !isLoading) { Text("Reintentar") }
    }
}

@Composable
private fun FoodEntryCard(entry: FoodEntry) {
    val consumed = entry.consumed
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${entry.meal.label} · ${formatFoodNumber(entry.quantity)} ${entry.unit}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoodMacro("P", consumed.protein, Color(0xFF4285F4), Modifier.weight(1f))
                    FoodMacro("C", consumed.carbs, Color(0xFF2E7D32), Modifier.weight(1f))
                    FoodMacro("G", consumed.fat, Color(0xFFF9A825), Modifier.weight(1f))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Calorías", style = MaterialTheme.typography.labelSmall)
                Text(formatFoodNumber(consumed.calories), style = MaterialTheme.typography.titleMedium)
                Text("kcal", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun FoodMacro(label: String, grams: BigDecimal?, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label: ${formatFoodNumber(grams, 2)} g", style = MaterialTheme.typography.labelSmall)
        Box(Modifier.fillMaxWidth().height(3.dp).background(color))
    }
}

@Preview(name = "Nutrición con alimentos", showBackground = true, widthDp = 390, heightDp = 850)
@Preview(name = "Nutrición compacta", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun NutritionScreenPreview() {
    val date = LocalDate.of(2026, 11, 8)
    val day = FoodDay(date, listOf(
        FoodEntry("example", "preview", "bread", date, MealType.BREAKFAST, BigDecimal("100"), "g",
            "Pan molde castaño integral", FoodNutrients(BigDecimal("128"), BigDecimal("6.40"), BigDecimal("21.30"), BigDecimal("1.50")))
    ), NutritionTargets(2200, BigDecimal("140"), BigDecimal("260"), BigDecimal("70")))
    UpUpProgressTheme {
        NutritionScreen(FoodDiaryUiState(todayDate = date, selectedDay = day, today = day),
            onDateSelected = {}, onRegisterFood = {}, onRefresh = {})
    }
}
