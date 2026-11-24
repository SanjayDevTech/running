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
import com.example.running.ui.theme.Raised4
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.TextSecondary
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent

/**
 * The app's one bar chart (Vico). The selected bar is drawn in [color]; the others in a raised grey,
 * so the accent marks the bar being read (e.g. the current week).
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
) {
    if (bars.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(bars) {
        producer.runTransaction { columnModel { series(bars.indices.map { it }, bars.map { it.value }) } }
    }
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
            rememberColumnCartesianLayer(columnProvider = provider),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabel,
                line = null,
                tick = null,
                guideline = rememberAxisGuidelineComponent(fill = Fill(Hairline)),
                valueFormatter = remember { CartesianValueFormatter { _, v, _ -> yFmt.value(v) } },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel,
                line = null,
                tick = null,
                guideline = null,
                valueFormatter = remember {
                    CartesianValueFormatter { _, v, _ -> labels.value.getOrNull(v.toInt()) ?: " " }
                },
            ),
            marker = if (onSelect != null) tapMarker else null,
            markerVisibilityListener = if (onSelect != null) listener else null,
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(height),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}

/** Draws nothing: bar taps only need the visibility callback, not a tooltip. */
private object InvisibleMarker : CartesianMarker
