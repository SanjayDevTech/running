package com.example.running.ui.components.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Hairline
import com.example.running.ui.theme.Raised3
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.TextPrimary
import com.example.running.ui.theme.TextSecondary
import com.example.running.ui.theme.TextTertiary
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.Insets

/**
 * The app's one line chart (Vico). D and E use this; nobody else sets up Vico.
 * [invertY] plots -y so that a smaller value (faster pace) sits higher; formatters still get the real y.
 */
@Composable
fun AppLineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    yFormatter: (Double) -> String,
    xFormatter: (Double) -> String,
    filled: Boolean = true,
    invertY: Boolean = false,
    color: Color = Accent,
    height: Dp = Sizes.chartHeight,
    selectedX: Double? = null,
    onSelectX: ((Double?) -> Unit)? = null,
    comparison: List<ChartPoint>? = null,
) {
    if (points.size < 2) return
    val sign = if (invertY) -1.0 else 1.0
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points, comparison, invertY) {
        producer.runTransaction {
            lineModel {
                series(points.map { it.x }, points.map { it.y * sign })
                comparison?.takeIf { it.size >= 2 }?.let { c -> series(c.map { it.x }, c.map { it.y * sign }) }
            }
        }
    }
    val yFmt = rememberUpdatedState(yFormatter)
    val xFmt = rememberUpdatedState(xFormatter)
    val onSelect = rememberUpdatedState(onSelectX)

    val mainLine = LineCartesianLayer.rememberLine(
        fill = remember(color) { LineCartesianLayer.LineFill.single(Fill(color)) },
        areaFill = if (filled) {
            remember(color) {
                LineCartesianLayer.AreaFill.single(
                    Fill(Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f))))
                )
            }
        } else null,
        stroke = remember { LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp) },
    )
    val comparisonLine = LineCartesianLayer.rememberLine(
        fill = remember { LineCartesianLayer.LineFill.single(Fill(TextTertiary)) },
        stroke = remember { LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp) },
    )
    val lines = if (comparison != null && comparison.size >= 2) listOf(mainLine, comparisonLine) else listOf(mainLine)

    val axisLabel = rememberAxisLabelComponent(style = AppText.label.copy(color = TextSecondary, fontSize = AppText.eyebrow.fontSize))
    val guideline = rememberAxisGuidelineComponent(fill = Fill(Hairline))
    val markerLabel = rememberTextComponent(
        style = AppText.label.copy(color = TextPrimary),
        padding = Insets(8.dp, 4.dp),
        background = rememberShapeComponent(fill = Fill(Raised3)),
    )
    val marker = rememberDefaultCartesianMarker(
        label = markerLabel,
        valueFormatter = remember {
            com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker.ValueFormatter { _, targets ->
                val t = targets.firstOrNull() as? com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
                val y = t?.points?.firstOrNull()?.entry?.y
                if (y == null) "" else yFmt.value(y * sign)
            }
        },
        guideline = rememberLineComponent(fill = Fill(TextTertiary), thickness = 1.dp),
    )
    val listener = remember {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                onSelect.value?.invoke(targets.firstOrNull()?.x)
            }
            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                onSelect.value?.invoke(targets.firstOrNull()?.x)
            }
            override fun onHidden(marker: CartesianMarker) {
                onSelect.value?.invoke(null)
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(lineProvider = LineCartesianLayer.LineProvider.series(lines)),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabel,
                line = null,
                tick = null,
                guideline = guideline,
                valueFormatter = remember(sign) { CartesianValueFormatter { _, v, _ -> yFmt.value(v * sign) } },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel,
                line = null,
                tick = null,
                guideline = null,
                valueFormatter = remember { CartesianValueFormatter { _, v, _ -> xFmt.value(v) } },
            ),
            marker = marker,
            markerVisibilityListener = listener,
            persistentMarkers = if (selectedX != null) { _ -> marker at selectedX } else null,
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(height),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}
