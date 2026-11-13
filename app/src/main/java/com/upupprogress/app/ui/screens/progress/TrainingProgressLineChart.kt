package com.upupprogress.app.ui.screens.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.upupprogress.app.data.model.TrainingPeriod
import com.upupprogress.app.data.model.TrainingProgress
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

internal val ProgressLocale: Locale = Locale.forLanguageTag("es-CL")

internal fun formatProgressNumber(value: BigDecimal, decimals: Int = 2): String =
    NumberFormat.getNumberInstance(ProgressLocale).apply {
        maximumFractionDigits = decimals
        minimumFractionDigits = 0
    }.format(value)

internal fun progressPeriodLabel(date: LocalDate, period: TrainingPeriod): String =
    date.format(DateTimeFormatter.ofPattern(if (period == TrainingPeriod.WEEK) "d MMM\nuuuu" else "MMM\nuuuu", ProgressLocale))

internal data class ProgressChartEntry<T>(
    val periodStart: LocalDate,
    val line: Int,
    val value: BigDecimal,
    val description: String,
    val source: T,
)

internal data class ProgressChartLine(val color: Color, val diamond: Boolean = false, val dashed: Boolean = false)
internal data class ProgressPlotPosition<T>(val entry: ProgressChartEntry<T>, val offset: Offset)

internal fun <T> hitProgressPoints(
    positions: List<ProgressPlotPosition<T>>, tap: Offset, radius: Float,
): List<T> = positions.filter { (it.offset - tap).getDistance() <= radius }
    .sortedBy { (it.offset - tap).getDistance() }.map { it.entry.source }

internal data class ProgressChartAxis(val top: Double, val ticks: List<BigDecimal>, val decimals: Int)

internal fun progressChartAxis(values: List<BigDecimal>, integersOnly: Boolean): ProgressChartAxis {
    val peak = values.maxOfOrNull { it.toDouble() } ?: 0.0
    if (peak <= 0) return if (integersOnly) ProgressChartAxis(1.0, listOf(BigDecimal.ZERO, BigDecimal.ONE), 0)
        else ProgressChartAxis(1.0, (0..4).map { BigDecimal.valueOf(it * 0.25) }, 2)
    val magnitude = 10.0.pow(floor(log10(peak / 4)))
    val normalized = peak / 4 / magnitude
    val niceStep = magnitude * when {
        normalized <= 1 -> 1
        normalized <= 2 -> 2
        normalized <= 5 -> 5
        else -> 10
    }
    val step = if (integersOnly) maxOf(1.0, niceStep) else niceStep
    val count = ceil(peak / step).toInt()
    return ProgressChartAxis(count * step, (0..count).map { BigDecimal.valueOf(it * step) },
        if (integersOnly) 0 else BigDecimal.valueOf(step).stripTrailingZeros().scale().coerceAtLeast(0))
}

/** Presentación compartida: nunca calcula métricas ni sustituye un período ausente por cero. */
@Composable
internal fun <T> TrainingProgressLineChart(
    progress: TrainingProgress,
    entries: List<ProgressChartEntry<T>>,
    lines: List<ProgressChartLine>,
    tagPrefix: String,
    onPointsSelected: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    integersOnly: Boolean = false,
    selectionKey: String = "",
) {
    val axis = remember(entries, integersOnly) { progressChartAxis(entries.map { it.value }, integersOnly) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val axisLabels = remember(axis, labelStyle) {
        axis.ticks.map { textMeasurer.measure(formatProgressNumber(it, axis.decimals), labelStyle) }
    }
    val dates = remember(progress, labelStyle) {
        progress.points.map { textMeasurer.measure(progressPeriodLabel(it.periodStart, progress.period), labelStyle) }
    }
    val axisWidth = with(density) { axisLabels.maxOf { it.size.width }.toDp() + 8.dp }
    val spacing = maxOf(72.dp, with(density) { (dates.maxOfOrNull { it.size.width } ?: 0).toDp() + 16.dp })
    val plotHeight = 208.dp
    val labelsHeight = with(density) { (dates.maxOfOrNull { it.size.height } ?: 0).toDp() + 12.dp }
    val scroll = rememberScrollState()
    var positioned by rememberSaveable(progress.exerciseId, progress.period, selectionKey) { mutableStateOf(false) }
    val selectPoints by rememberUpdatedState(onPointsSelected)

    BoxWithConstraints(modifier.fillMaxWidth().testTag("$tagPrefix-chart")) {
        val viewport = (maxWidth - axisWidth).coerceAtLeast(1.dp)
        val width = maxOf(viewport, spacing * progress.points.size.coerceAtLeast(1))
        val widthPx = with(density) { width.toPx() }
        val heightPx = with(density) { plotHeight.toPx() }
        val paddingPx = with(density) { 12.dp.toPx() }
        val columnPx = widthPx / progress.points.size.coerceAtLeast(1)
        val touchRadius = with(density) { 24.dp.toPx() }
        fun y(value: Double): Float =
            (heightPx - paddingPx - (value / axis.top) * (heightPx - 2 * paddingPx)).toFloat()
        val indices = remember(progress) { progress.points.mapIndexed { index, point -> point.periodStart to index }.toMap() }
        val positions = remember(entries, widthPx, heightPx, paddingPx, columnPx, indices, axis) {
            entries.map { entry -> ProgressPlotPosition(
                entry, Offset(columnPx * (indices.getValue(entry.periodStart) + 0.5f), y(entry.value.toDouble())),
            ) }
        }
        val byPeriod = remember(positions) { positions.groupBy { it.entry.periodStart } }
        LaunchedEffect(progress.exerciseId, progress.period, selectionKey, positioned) {
            if (!positioned) {
                withFrameNanos { }
                scroll.scrollTo(scroll.maxValue)
                positioned = true
            }
        }
        Row {
            Canvas(Modifier.width(axisWidth).height(plotHeight)) {
                axis.ticks.forEachIndexed { index, tick ->
                    val label = axisLabels[index]
                    drawText(label, topLeft = Offset(
                        size.width - label.size.width - 6.dp.toPx(), y(tick.toDouble()) - label.size.height / 2f,
                    ))
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Box(Modifier.width(width).height(plotHeight + labelsHeight)) {
                    Canvas(Modifier.fillMaxSize().pointerInput(positions) {
                        detectTapGestures { selectPoints(hitProgressPoints(positions, it, touchRadius)) }
                    }) {
                        axis.ticks.forEach { tick ->
                            drawLine(grid, Offset(0f, y(tick.toDouble())), Offset(size.width, y(tick.toDouble())), 1.dp.toPx())
                        }
                        lines.forEachIndexed { index, line ->
                            var prior: Offset? = null
                            progress.points.forEach { period ->
                                val current = byPeriod[period.periodStart]?.firstOrNull { it.entry.line == index }?.offset
                                if (current != null && prior != null) drawLine(
                                    line.color, prior, current, 2.dp.toPx(), cap = StrokeCap.Round,
                                    pathEffect = if (line.dashed)
                                        PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null,
                                )
                                prior = current
                            }
                        }
                        positions.forEach { position ->
                            val line = lines[position.entry.line]
                            if (!line.diamond) drawCircle(line.color, 4.dp.toPx(), position.offset)
                            else {
                                val r = 6.dp.toPx()
                                val center = position.offset
                                val diamond = Path().apply {
                                    moveTo(center.x, center.y - r); lineTo(center.x + r, center.y)
                                    lineTo(center.x, center.y + r); lineTo(center.x - r, center.y); close()
                                }
                                drawPath(diamond, line.color, style = Stroke(2.dp.toPx()))
                            }
                        }
                        dates.forEachIndexed { index, label ->
                            drawText(label, topLeft = Offset(
                                columnPx * (index + 0.5f) - label.size.width / 2f, heightPx + 6.dp.toPx(),
                            ))
                        }
                    }
                    progress.points.forEachIndexed { index, period ->
                        val periodPositions = byPeriod[period.periodStart].orEmpty()
                        if (periodPositions.isNotEmpty()) Box(
                            Modifier.offset { IntOffset((columnPx * index).roundToInt(), 0) }
                                .width(with(density) { columnPx.toDp() }).height(plotHeight)
                                .testTag("$tagPrefix-period-${period.periodStart}")
                                .semantics {
                                    contentDescription = progressPeriodLabel(period.periodStart, progress.period).replace('\n', ' ') +
                                        ". " + periodPositions.joinToString { it.entry.description }
                                    role = Role.Button
                                    onClick("Consultar registros") { selectPoints(periodPositions.map { it.entry.source }); true }
                                    customActions = periodPositions.map { position ->
                                        CustomAccessibilityAction("Consultar ${position.entry.description}") {
                                            selectPoints(listOf(position.entry.source)); true
                                        }
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}
