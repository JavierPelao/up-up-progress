package com.upupprogress.app.ui.screens.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upupprogress.app.ui.theme.UpUpProgressTheme
import java.time.DayOfWeek
import java.time.LocalDate

private val dayNames = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private val dayLabels = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

@Composable
fun TrainingScreen(
    onCreateRoutine: () -> Unit,
    onViewRoutine: (routineId: String, day: DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    routines: List<TrainingRoutineUi> = emptyList(),
    today: DayOfWeek = LocalDate.now().dayOfWeek
) {
    val daysWithRoutine = routines.flatMap { it.days }.toSet()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Rutinas",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tu entrenamiento, organizado por día.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Mi semana",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (daysWithRoutine.size == 1) {
                            "1 día con rutina"
                        } else {
                            "${daysWithRoutine.size} días con rutina"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    DayOfWeek.entries.forEach { day ->
                        WeekDayItem(
                            day = day,
                            hasRoutine = day in daysWithRoutine,
                            isToday = day == today,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // El orden visual siempre es lunes a domingo, independientemente de la carga.
                DayOfWeek.entries.forEach { day ->
                    routines.filter { day in it.days }.forEach { routine ->
                        RoutineCard(
                            routine = routine,
                            day = day,
                            onViewRoutine = { onViewRoutine(routine.id, day) }
                        )
                    }
                }

                Button(
                    onClick = onCreateRoutine,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("+ Crear rutina")
                }
            }
        }
    }
}

@Composable
private fun WeekDayItem(
    day: DayOfWeek,
    hasRoutine: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = hasRoutine || isToday
    Surface(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = buildString {
                append(dayNames[day.value - 1])
                if (isToday) append(", hoy")
                append(if (hasRoutine) ", con rutina" else ", libre")
            }
        },
        shape = RoundedCornerShape(10.dp),
        color = if (accent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (isToday) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (isToday) {
                Text(
                    text = "Hoy",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = dayLabels[day.value - 1],
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = if (accent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = if (hasRoutine) "Rutina" else "Libre",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                color = if (hasRoutine) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun RoutineCard(
    routine: TrainingRoutineUi,
    day: DayOfWeek,
    onViewRoutine: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayNames[day.value - 1].uppercase(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (routine.exercises.size == 1) {
                        "1 ejercicio"
                    } else {
                        "${routine.exercises.size} ejercicios"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = routine.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = routine.exercises.joinToString(" · ").ifEmpty { "Sin ejercicios añadidos" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onViewRoutine,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Ver rutina y registrar")
            }
        }
    }
}

@Preview(name = "Sin rutinas", showBackground = true, widthDp = 390, heightDp = 720)
@Preview(name = "Sin rutinas compacto", showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun TrainingEmptyPreview() {
    UpUpProgressTheme {
        TrainingScreen(
            onCreateRoutine = {},
            onViewRoutine = { _, _ -> },
            today = DayOfWeek.FRIDAY
        )
    }
}

@Preview(name = "Con rutinas de ejemplo", showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun TrainingWithRoutinesPreview() {
    UpUpProgressTheme {
        TrainingScreen(
            onCreateRoutine = {},
            onViewRoutine = { _, _ -> },
            today = DayOfWeek.FRIDAY,
            routines = listOf(
                TrainingRoutineUi(
                    id = "preview-push",
                    name = "Empuje",
                    days = setOf(DayOfWeek.MONDAY),
                    exercises = listOf("Press de banca", "Press inclinado", "Elevaciones laterales", "Tríceps")
                ),
                TrainingRoutineUi(
                    id = "preview-legs",
                    name = "Piernas",
                    days = setOf(DayOfWeek.TUESDAY),
                    exercises = listOf("Sentadilla", "Prensa", "Curl femoral", "Elevación de talones")
                )
            )
        )
    }
}
