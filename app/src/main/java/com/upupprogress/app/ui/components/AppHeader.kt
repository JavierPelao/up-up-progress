package com.upupprogress.app.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.upupprogress.app.ui.theme.UpUpProgressTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onProfile: (() -> Unit)? = null,
) {
    CenterAlignedTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            onBack?.let { action ->
                IconButton(onClick = action) { Icon(backIcon, contentDescription = "Volver") }
            }
        },
        actions = {
            onProfile?.let { action ->
                IconButton(onClick = action) { Icon(profileIcon, contentDescription = "Ver perfil") }
            }
        },
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

private fun headerIcon(name: String, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) { draw() }
    }.build()

private val backIcon = headerIcon("Back") {
    moveTo(20f, 12f)
    horizontalLineTo(4f)
    moveTo(10f, 6f)
    lineTo(4f, 12f)
    lineTo(10f, 18f)
}

private val profileIcon = headerIcon("Profile") {
    moveTo(16f, 7f)
    curveTo(16f, 9.2f, 14.2f, 11f, 12f, 11f)
    curveTo(9.8f, 11f, 8f, 9.2f, 8f, 7f)
    curveTo(8f, 4.8f, 9.8f, 3f, 12f, 3f)
    curveTo(14.2f, 3f, 16f, 4.8f, 16f, 7f)
    close()
    moveTo(4f, 21f)
    verticalLineTo(19f)
    curveTo(4f, 15.7f, 7.6f, 14f, 12f, 14f)
    curveTo(16.4f, 14f, 20f, 15.7f, 20f, 19f)
    verticalLineTo(21f)
}

@Preview(name = "Barra de Inicio", showBackground = true, widthDp = 390)
@Composable
private fun AppHeaderPreview() {
    UpUpProgressTheme { AppHeader(title = "Up Up Progress", onProfile = {}) }
}
