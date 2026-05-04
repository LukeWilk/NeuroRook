package io.github.lukewilk.ui.graphs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/** Fixed chart height avoids layout jitter while new payloads stream into the page. */
private val graphSurfaceHeight = 164.dp

/** Compact spacing keeps supporting labels readable without stealing graph real estate. */
private val graphSurfaceLabelSpacing = 6.dp

/** Reserved width for vertical-axis values so the plot stays aligned with its bottom labels. */
private val graphAxisValueLabelWidth = 40.dp

/** Small gap between axis labels and the plotted graph surface. */
private val graphAxisLabelSpacing = 8.dp

/** Temporary datapoint marker size to help judge graph resolution during development. */
private val graphPointMarkerRadius = 3.dp

/** Slightly larger marker keeps the latest sample visually anchored on the right edge. */
private val graphLatestPointMarkerRadius = 5.dp

/** Accessible description exposed by every rendered graph surface. */
internal fun renderedGraphContentDescription(title: String): String = "$title graph"

/**
 * Reusable graph surface that renders the provided shared graph model using a lightweight Compose canvas.
 *
 * The implementation keeps a fixed size and relies on compact immutable render models so callers can reuse
 * it for multiple graph datasets without introducing separate widget stacks per chart type.
 */
@Composable
internal fun GraphSurface(
    renderModel: GraphRenderModel,
    title: String,
    accentColor: Color,
    viewOptions: GraphViewOptions,
    modifier: Modifier = Modifier
) {
    val graphBackgroundColor = if (viewOptions.useBlackBackground) Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f)
    val gridColor = if (viewOptions.useBlackBackground) Color.White.copy(alpha = 0.24f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val zeroLineColor = if (viewOptions.useBlackBackground) Color.White.copy(alpha = 0.48f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val labelColor = if (viewOptions.useBlackBackground) Color.White.copy(alpha = 0.84f) else MaterialTheme.colorScheme.onSurfaceVariant
    val yAxisLabels = when (renderModel) {
        is LineGraphRenderModel -> renderModel.yAxisLabels
        is BarGraphRenderModel -> renderModel.yAxisLabels
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(graphSurfaceLabelSpacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(graphAxisLabelSpacing),
            verticalAlignment = androidx.compose.ui.Alignment.Top
        ) {
            GraphValueAxisLabelsColumn(
                labels = yAxisLabels,
                labelColor = labelColor,
                modifier = Modifier
                    .width(graphAxisValueLabelWidth)
                    .height(graphSurfaceHeight)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(graphSurfaceHeight)
                    .semantics { contentDescription = renderedGraphContentDescription(title) }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = graphBackgroundColor)
                    when (renderModel) {
                        is LineGraphRenderModel -> drawLineGraph(
                            model = renderModel,
                            accentColor = accentColor,
                            gridColor = gridColor,
                            zeroLineColor = zeroLineColor,
                            viewOptions = viewOptions
                        )

                        is BarGraphRenderModel -> drawBarGraph(
                            model = renderModel,
                            accentColor = accentColor,
                            gridColor = gridColor,
                            zeroLineColor = zeroLineColor,
                            showGridLines = viewOptions.showGridLines
                        )
                    }
                }
            }
        }

        when (renderModel) {
            is LineGraphRenderModel -> {
                val xAxisLabels = renderModel.xAxisLabels
                if (xAxisLabels != null && xAxisLabels.ticks.isNotEmpty()) {
                    GraphLineAxisLabelsRow(
                        labels = xAxisLabels,
                        labelColor = labelColor
                    )
                }
            }

            is BarGraphRenderModel -> {
                if (renderModel.bars.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Spacer(modifier = Modifier.width(graphAxisValueLabelWidth + graphAxisLabelSpacing))
                        renderModel.bars.forEach { bar ->
                            Text(
                                text = bar.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Aligns vertical-axis values beside the graph surface and shows the optional unit/title label. */
@Composable
private fun GraphValueAxisLabelsColumn(
    labels: GraphAxisLabels?,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    if (labels == null) {
        Spacer(modifier = modifier)
        return
    }

    Column(modifier = modifier) {
        labels.unitLabel?.let { unitLabel ->
            Text(
                text = unitLabel,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = androidx.compose.ui.Alignment.End
        ) {
            labels.ticks.forEach { tickLabel ->
                Text(
                    text = tickLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Renders one bottom axis row with multiple evenly spaced labels for line graphs such as FFT. */
@Composable
private fun GraphLineAxisLabelsRow(labels: GraphAxisLabels, labelColor: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        labels.unitLabel?.let { unitLabel ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(graphAxisValueLabelWidth + graphAxisLabelSpacing))
                Text(
                    text = unitLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(graphAxisValueLabelWidth + graphAxisLabelSpacing))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = androidx.compose.ui.Alignment.Top
            ) {
                labels.ticks.forEachIndexed { index, tickLabel ->
                    Text(
                        text = tickLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                        textAlign = when (index) {
                            0 -> TextAlign.Start
                            labels.ticks.lastIndex -> TextAlign.End
                            else -> TextAlign.Center
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Draws the shared graph frame and subtle horizontal guides used by all graph variants. */
private fun DrawScope.drawGraphFrame(
    gridColor: Color,
    horizontalGuideLineCount: Int,
    verticalGuideLineCount: Int,
    showGridLines: Boolean
): GraphFrame {
    val outerPadding = 6.dp.toPx()
    val chartLeft = outerPadding
    val chartTop = outerPadding
    val chartRight = size.width - outerPadding
    val chartBottom = size.height - outerPadding
    val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
    val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

    if (showGridLines) {
        drawRect(
            color = gridColor,
            topLeft = Offset(chartLeft, chartTop),
            size = Size(chartWidth, chartHeight),
            style = Stroke(width = 1.dp.toPx())
        )

        repeat(horizontalGuideLineCount.coerceAtLeast(0)) { index ->
            val y = chartTop + chartHeight * ((index + 1) / (horizontalGuideLineCount + 1).toFloat())
            drawLine(
                color = gridColor.copy(alpha = 0.75f),
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        repeat(verticalGuideLineCount.coerceAtLeast(0)) { index ->
            val x = chartLeft + chartWidth * ((index + 1) / (verticalGuideLineCount + 1).toFloat())
            drawLine(
                color = gridColor.copy(alpha = 0.55f),
                start = Offset(x, chartTop),
                end = Offset(x, chartBottom),
                strokeWidth = 1.dp.toPx()
            )
        }
    }

    return GraphFrame(
        left = chartLeft,
        top = chartTop,
        right = chartRight,
        bottom = chartBottom,
        width = chartWidth,
        height = chartHeight
    )
}

/** Renders a continuous line/spectrum graph with an optional filled area. */
private fun DrawScope.drawLineGraph(
    model: LineGraphRenderModel,
    accentColor: Color,
    gridColor: Color,
    zeroLineColor: Color,
    viewOptions: GraphViewOptions
) {
    val horizontalGuideLineCount = (model.yAxisLabels?.ticks?.size ?: 2) - 2
    val verticalGuideLineCount = (model.xAxisLabels?.ticks?.size ?: 2) - 2
    val frame = drawGraphFrame(
        gridColor = gridColor,
        horizontalGuideLineCount = horizontalGuideLineCount,
        verticalGuideLineCount = verticalGuideLineCount,
        showGridLines = viewOptions.showGridLines
    )
    if (model.points.isEmpty()) return

    val zeroLineY = frame.yForValue(value = 0f, minY = model.minY, maxY = model.maxY)
    if (model.showZeroLine && model.minY < 0f && model.maxY > 0f) {
        drawLine(
            color = zeroLineColor,
            start = Offset(frame.left, zeroLineY),
            end = Offset(frame.right, zeroLineY),
            strokeWidth = 1.dp.toPx()
        )
    }

    val plottedPoints = model.points.map { point ->
        Offset(
            x = frame.left + frame.width * point.x.coerceIn(0f, 1f),
            y = frame.yForValue(point.y, model.minY, model.maxY)
        )
    }

    if (model.fillArea && viewOptions.fillFilteredArea) {
        val areaPath = Path().apply {
            val first = plottedPoints.first()
            val baselineY = if (model.showZeroLine) zeroLineY else frame.bottom
            moveTo(first.x, baselineY)
            lineTo(first.x, first.y)
            plottedPoints.drop(1).forEach { point ->
                lineTo(point.x, point.y)
            }
            val last = plottedPoints.last()
            lineTo(last.x, baselineY)
            close()
        }
        drawPath(path = areaPath, color = accentColor.copy(alpha = 0.12f), style = Fill)
    }

    if (plottedPoints.size == 1) {
        drawCircle(
            color = accentColor,
            radius = 3.dp.toPx(),
            center = plottedPoints.first()
        )
        return
    }

    val linePath = Path().apply {
        val first = plottedPoints.first()
        moveTo(first.x, first.y)
        plottedPoints.drop(1).forEach { point ->
            lineTo(point.x, point.y)
        }
    }

    drawPath(
        path = linePath,
        color = accentColor,
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    if (viewOptions.showDataPoints) {
        val markerRadius = graphPointMarkerRadius.toPx()
        plottedPoints.dropLast(1).forEach { point ->
            drawCircle(
                color = accentColor.copy(alpha = 0.82f),
                radius = markerRadius,
                center = point
            )
        }

        drawCircle(
            color = accentColor,
            radius = graphLatestPointMarkerRadius.toPx(),
            center = plottedPoints.last()
        )
    }
}

/** Renders a compact categorical bar graph used for band-power style data. */
private fun DrawScope.drawBarGraph(
    model: BarGraphRenderModel,
    accentColor: Color,
    gridColor: Color,
    zeroLineColor: Color,
    showGridLines: Boolean
) {
    val horizontalGuideLineCount = (model.yAxisLabels?.ticks?.size ?: 2) - 2
    val frame = drawGraphFrame(
        gridColor = gridColor,
        horizontalGuideLineCount = horizontalGuideLineCount,
        verticalGuideLineCount = 0,
        showGridLines = showGridLines
    )
    if (model.bars.isEmpty()) return

    val zeroLineY = frame.yForValue(value = 0f, minY = model.minY, maxY = model.maxY)
    if (model.minY < 0f && model.maxY > 0f) {
        drawLine(
            color = zeroLineColor,
            start = Offset(frame.left, zeroLineY),
            end = Offset(frame.right, zeroLineY),
            strokeWidth = 1.dp.toPx()
        )
    }

    val slotWidth = frame.width / model.bars.size
    val gap = min(slotWidth * 0.22f, 10.dp.toPx())
    val barWidth = (slotWidth - gap).coerceAtLeast(4.dp.toPx())
    val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

    model.bars.forEachIndexed { index, bar ->
        val centerX = frame.left + slotWidth * index + slotWidth / 2f
        val left = centerX - barWidth / 2f
        val valueY = frame.yForValue(bar.value, model.minY, model.maxY)
        val top = min(zeroLineY, valueY)
        val height = abs(zeroLineY - valueY).coerceAtLeast(1f)

        drawRoundRect(
            color = accentColor.copy(alpha = 0.86f),
            topLeft = Offset(left, top),
            size = Size(barWidth, height),
            cornerRadius = cornerRadius
        )
    }
}

/** Compact graph frame geometry reused across the shared canvas renderers. */
private data class GraphFrame(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val width: Float,
    val height: Float
) {
    fun yForValue(value: Float, minY: Float, maxY: Float): Float {
        val range = (maxY - minY).takeIf { it > 0f } ?: 1f
        val normalized = ((value - minY) / range).coerceIn(0f, 1f)
        return bottom - (height * normalized)
    }
}




