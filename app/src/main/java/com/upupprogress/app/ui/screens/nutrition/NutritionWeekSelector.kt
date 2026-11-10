package com.upupprogress.app.ui.screens.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun nutritionWeekStart(date: LocalDate): LocalDate =
    date.minusDays(date.dayOfWeek.value - 1L)

internal fun adjacentNutritionWeek(date: LocalDate, today: LocalDate, direction: Int): LocalDate {
    require(direction == -1 || direction == 1)
    val candidate = date.plusWeeks(direction.toLong())
    if (nutritionWeekStart(candidate) > nutritionWeekStart(today)) return date
    return minOf(candidate, today)
}

internal fun nutritionWeekRange(date: LocalDate): String {
    val start = nutritionWeekStart(date)
    val end = start.plusDays(6)
    val locale = Locale.forLanguageTag("es-CL")
    fun LocalDate.formatWith(pattern: String) = format(DateTimeFormatter.ofPattern(pattern, locale))
    return when {
        start.year != end.year -> "${start.formatWith("d MMM yyyy")}–${end.formatWith("d MMM yyyy")}"
        start.month != end.month -> "${start.formatWith("d MMM")}–${end.formatWith("d MMM yyyy")}"
        else -> "${start.dayOfMonth}–${end.formatWith("d MMMM yyyy")}"
    }
}

@Composable
internal fun NutritionWeekSelector(
    selectedDate: LocalDate,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onOpenCalendar: () -> Unit,
    recordedDates: Set<LocalDate> = emptySet(),
) {
    val start = nutritionWeekStart(selectedDate)
    val range = nutritionWeekRange(selectedDate)
    val dayLabels = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onDateSelected(adjacentNutritionWeek(selectedDate, today, -1)) },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Semana anterior" },
                ) { Text("‹", fontSize = 28.sp) }
                TextButton(
                    onClick = onOpenCalendar,
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = "Abrir calendario. Semana del $range"
                    },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Semana", style = MaterialTheme.typography.labelSmall)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                range, modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                            )
                            Icon(CalendarOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                IconButton(
                    onClick = { onDateSelected(adjacentNutritionWeek(selectedDate, today, 1)) },
                    enabled = start < nutritionWeekStart(today),
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Semana siguiente" },
                ) { Text("›", fontSize = 28.sp) }
            }

            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                // En anchos pequeños se desplaza la fila para conservar blancos táctiles de 48 dp.
                val dayWidth = maxOf(48.dp, (maxWidth - 12.dp) / 7)
                val overflow = maxOf(0.dp, dayWidth * 7 + 12.dp - maxWidth)
                val selectedOffset = maxOf(
                    0.dp, (dayWidth + 2.dp) * (selectedDate.dayOfWeek.value - 1) - (maxWidth - dayWidth) / 2,
                )
                val targetScroll = with(LocalDensity.current) { minOf(overflow, selectedOffset).roundToPx() }
                val dayScroll = rememberScrollState(initial = targetScroll)
                LaunchedEffect(selectedDate, targetScroll) { dayScroll.scrollTo(targetScroll) }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(dayScroll).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    repeat(7) { index ->
                        val date = start.plusDays(index.toLong())
                        val selected = date == selectedDate
                        val isToday = date == today
                        val enabled = date <= today
                        val hasRecords = enabled && date in recordedDates
                        val foreground = when {
                            selected -> MaterialTheme.colorScheme.onPrimary
                            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Surface(
                            modifier = Modifier.width(dayWidth).heightIn(min = 54.dp)
                                .selectable(
                                    selected = selected, enabled = enabled, role = Role.Tab,
                                    onClick = { onDateSelected(date) },
                                )
                                .semantics(mergeDescendants = true) {
                                    contentDescription = date.format(DateTimeFormatter.ofPattern(
                                        "EEEE d MMMM yyyy", Locale.forLanguageTag("es-CL"),
                                    ))
                                    if (isToday || hasRecords) stateDescription = listOfNotNull(
                                        if (isToday) "Hoy" else null,
                                        if (hasRecords) "Con alimentos registrados" else null,
                                    ).joinToString(", ")
                                },
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = foreground,
                            border = when {
                                isToday -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                selected -> null
                                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            },
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                            ) {
                                Box(
                                    Modifier.size(6.dp).then(
                                        if (hasRecords) Modifier.background(Color(0xFF808080), CircleShape) else Modifier,
                                    ),
                                )
                                if (isToday) Text(
                                    "Hoy", style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) foreground else MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    dayLabels[index], style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CalendarOutline = ImageVector.Builder(
    name = "Calendar", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(5f, 5f); lineTo(19f, 5f); lineTo(21f, 7f); lineTo(21f, 20f)
        lineTo(3f, 20f); lineTo(3f, 7f); close()
        moveTo(3f, 10f); lineTo(21f, 10f)
        moveTo(8f, 3f); lineTo(8f, 7f)
        moveTo(16f, 3f); lineTo(16f, 7f)
        moveTo(8f, 14f); lineTo(9f, 14f)
        moveTo(14f, 14f); lineTo(15f, 14f)
    }
}.build()

@Preview(name = "Semana", showBackground = true, widthDp = 390)
@Preview(name = "Semana compacta", showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun NutritionWeekSelectorPreview() {
    UpUpProgressTheme {
        Box(Modifier.padding(16.dp)) {
            NutritionWeekSelector(
                selectedDate = LocalDate.of(2026, 11, 11),
                today = LocalDate.of(2026, 11, 12),
                onDateSelected = {},
                onOpenCalendar = {},
                recordedDates = setOf(
                    LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12),
                ),
            )
        }
    }
}
