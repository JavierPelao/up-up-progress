package com.upupprogress.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppDestination(val label: String) {
    HOME("Inicio"),
    TRAINING("Rutinas"),
    NUTRITION("Nutrición"),
    PROGRESS("Progreso")
}

@Composable
fun AppBottomBar(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = destinationIcons.getValue(destination),
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 11.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

private fun outlineIcon(name: String, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) { draw() }
    }.build()

// Vectores locales: no requieren agregar una librería de iconos.
private val destinationIcons = mapOf(
    AppDestination.HOME to outlineIcon("Home") {
        moveTo(3f, 11f)
        lineTo(12f, 3f)
        lineTo(21f, 11f)
        moveTo(5f, 10f)
        verticalLineTo(21f)
        horizontalLineTo(10f)
        verticalLineTo(15f)
        horizontalLineTo(14f)
        verticalLineTo(21f)
        horizontalLineTo(19f)
        verticalLineTo(10f)
    },
    AppDestination.TRAINING to outlineIcon("Training") {
        moveTo(8f, 6f)
        horizontalLineTo(21f)
        moveTo(8f, 12f)
        horizontalLineTo(21f)
        moveTo(8f, 18f)
        horizontalLineTo(21f)
        moveTo(3f, 6f)
        horizontalLineTo(4f)
        moveTo(3f, 12f)
        horizontalLineTo(4f)
        moveTo(3f, 18f)
        horizontalLineTo(4f)
    },
    AppDestination.NUTRITION to outlineIcon("Nutrition") {
        moveTo(12f, 3f)
        curveTo(17f, 3f, 21f, 7f, 21f, 12f)
        curveTo(21f, 17f, 17f, 21f, 12f, 21f)
        curveTo(7f, 21f, 3f, 17f, 3f, 12f)
        curveTo(3f, 7f, 7f, 3f, 12f, 3f)
        close()
        moveTo(3f, 12f)
        horizontalLineTo(21f)
        moveTo(12f, 3f)
        verticalLineTo(21f)
    },
    AppDestination.PROGRESS to outlineIcon("Progress") {
        moveTo(3f, 18f)
        lineTo(9f, 12f)
        lineTo(13f, 15f)
        lineTo(21f, 6f)
        moveTo(15f, 6f)
        horizontalLineTo(21f)
        verticalLineTo(12f)
    }
)
