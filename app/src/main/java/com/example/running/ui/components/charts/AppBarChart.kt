package com.example.running.ui.components.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Hairline
import com.example.running.ui.theme.Motion
import com.example.running.ui.theme.Raised4
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.TextSecondary
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

/**
 * The app's one bar chart (Vico). The selected bar is drawn in [color]; the others in a raised grey,
 * so the accent marks the bar being read (e.g. the current week).
 *
 * [yStep] is the y label step in the bars' unit, chosen by the caller so every label is distinct in
 * [yFormatter] (whole runs, whole minutes, ...). The axis then runs from 0 to the first step multiple
 * at or above the tallest bar, so no bar rises past the top label. Null leaves the range to Vico.
 */
@Composable
fun AppBarChart(
    bars: List<BarDatum>,
    modifier: Modifier = Modifier,
    yFormatter: (Double) -> String,
    color: Color = Accent,
    height: Dp = Sizes.chartHeight,
    selectedIndex: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    yStep: Double? = null,
) {
    if (bars.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(bars) {
        producer.runTransaction { columnModel { series(bars.indices.map { it }, bars.map { it.value }) } }
    }
    val yTop = remember(bars, yStep) { yStep?.let { barAxisTop(bars.maxOf { b -> b.value }, it) } }
    val labels = rememberUpdatedState(bars.map { it.label })
    val yFmt = rememberUpdatedState(yFormatter)
    val onSelectState = rememberUpdatedState(onSelect)
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
    val selectedColumn = rememberLineComponent(fill = Fill(color), thickness = 14.dp, shape = shape)
    val normalColumn = rememberLineComponent(
        fill = Fill(if (selectedIndex == null) color else Raised4), thickness = 14.dp, shape = shape,
    )
    val provider = remember(selectedColumn, normalColumn, selectedIndex) {
        object : ColumnCartesianLayer.ColumnProvider {
            override fun getColumn(entry: ColumnCartesianLayerModel.Entry, extraStore: ExtraStore) =
                if (selectedIndex != null && entry.x.toInt() == selectedIndex) selectedColumn else normalColumn

            override fun getWidestSeriesColumn(seriesKey: Any, seriesIndex: Int, extraStore: ExtraStore) = normalColumn
        }
    }
    val axisLabel = rememberAxisLabelComponent(style = AppText.label.copy(color = TextSecondary, fontSize = AppText.eyebrow.fontSize))
    val listener = remember {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                targets.firstOrNull()?.let { onSelectState.value?.invoke(it.x.toInt()) }
            }
        }
    }
    val tapMarker = remember { InvisibleMarker }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = provider,
                rangeProvider = remember(yTop) {
                    if (yTop == null) CartesianLayerRangeProvider.auto() else CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = yTop)
                },
            ),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabel,
                line = null,
                tick = null,
                guideline = rememberAxisGuidelineComponent(fill = Fill(Hairline)),
                valueFormatter = remember { CartesianValueFormatter { _, v, _ -> yFmt.value(v) } },
                itemPlacer = remember(yStep) {
                    if (yStep == null) VerticalAxis.ItemPlacer.step() else VerticalAxis.ItemPlacer.step(step = { yStep })
                },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel,
                line = null,
                tick = null,
                guideline = null,
                valueFormatter = remember {
                    CartesianValueFormatter { _, v, _ -> labels.value.getOrNull(v.toInt())?.takeIf { it.isNotBlank() } ?: "·" }
                },
                // Vico rejects blank labels, so thin them with the item placer: at most ~7 labels,
                // always including the last (current-period) bar.
                itemPlacer = remember(bars.size) {
                    val spacing = (bars.size + MAX_X_LABELS - 1) / MAX_X_LABELS
                    HorizontalAxis.ItemPlacer.aligned(spacing = { spacing.coerceAtLeast(1) }, offset = { (bars.size - 1) % spacing.coerceAtLeast(1) })
                },
            ),
            marker = if (onSelect != null) tapMarker else null,
            markerVisibilityListener = if (onSelect != null) listener else null,
        ),
        modelProducer = producer,
        // Value changes (another filter, metric or run) interpolate like a meter (Motion.VALUE_MS).
        animationSpec = Motion.value(),
        modifier = modifier.fillMaxWidth().height(height),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}

private const val MAX_X_LABELS = 7

/** Draws nothing: bar taps only need the visibility callback, not a tooltip. */
private object InvisibleMarker : CartesianMarker

/** Top of a bar chart's y axis: the first [step] multiple at or above [max], at least one step. */
internal fun barAxisTop(max: Double, step: Double): Double {
    val steps = Math.ceil(max.coerceAtLeast(0.0) / step - 1e-9).coerceAtLeast(1.0)
    return steps * step
}
