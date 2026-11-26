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
import com.example.running.ui.theme.Motion
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
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
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
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import kotlin.math.abs

/**
 * The app's one line chart (Vico). D and E use this; nobody else sets up Vico.
 * [invertY] plots -y so that a smaller value (faster pace) sits higher; formatters still get the real y.
 * [breaks] are x values where the line is interrupted (drawn as separate series in the same style).
 * [onScrub] follows the finger: the x of the readout while the chart is pressed or dragged (each time it
 * moves to another point), then null when the finger lifts. It does not change the readout itself.
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
    minXLabelStep: Double = 0.0,
    minYStep: Double = 0.0,
    breaks: List<Double> = emptyList(),
    onScrub: ((x: Double?) -> Unit)? = null,
) {
    if (points.size < 2) return
    val sign = if (invertY) -1.0 else 1.0
    // Vico derives its x step from the data and rejects x values finer than four decimals, so arbitrary
    // distances (e.g. 1.23456 km) crash it. Snap x onto a regular grid and give Vico that grid as the step.
    val grid = remember(points, minXLabelStep) { ChartGrid.of(points, minXLabelStep) }
    val main = remember(points, grid) { grid.snap(points) }
    val second = remember(comparison, grid) { comparison?.let(grid::snap)?.takeIf { it.size >= 2 } }
    if (main.size < 2) return
    val sections = remember(main, breaks) { splitAtBreaks(main, breaks) }
    val yPlan = remember(main, second, sign, minYStep) { YAxisPlan.of(main + second.orEmpty(), sign, minYStep) }
    val yBounds = yPlan.bounds
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(sections, second, invertY) {
        producer.runTransaction {
            lineModel {
                sections.forEach { s -> series(s.map { it.x }, s.map { it.y * sign }) }
                second?.let { c -> series(c.map { it.x }, c.map { it.y * sign }) }
            }
        }
    }
    val yFmt = rememberUpdatedState(yFormatter)
    val xFmt = rememberUpdatedState(xFormatter)
    val onSelect = rememberUpdatedState(onSelectX)
    val scrub = rememberUpdatedState(onScrub)

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
    val lines = List(sections.size) { mainLine } + listOfNotNull(comparisonLine.takeIf { second != null })

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
            // The default marker controller shows on press, follows moves and hides on release.
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                onSelect.value?.invoke(targets.firstOrNull()?.x)
                scrub.value?.invoke(targets.firstOrNull()?.x)
            }
            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                onSelect.value?.invoke(targets.firstOrNull()?.x)
                scrub.value?.invoke(targets.firstOrNull()?.x)
            }
            override fun onHidden(marker: CartesianMarker) {
                onSelect.value?.invoke(null)
                scrub.value?.invoke(null)
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(lines),
                // Fit y to the data: the default range starts at 0, which for pace (plotted as -y) puts a
                // meaningless "--:--" label at the top and squashes the line.
                rangeProvider = remember(yBounds) { CartesianLayerRangeProvider.fixed(minY = yBounds.start, maxY = yBounds.endInclusive) },
            ),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabel,
                line = null,
                tick = null,
                guideline = guideline,
                valueFormatter = remember(sign) { CartesianValueFormatter { _, v, _ -> yFmt.value(v * sign) } },
                itemPlacer = remember(yPlan) { VerticalAxis.ItemPlacer.step(step = { yPlan.step }) },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel,
                line = null,
                tick = null,
                guideline = null,
                valueFormatter = remember { CartesianValueFormatter { _, v, _ -> xFmt.value(v) } },
                itemPlacer = remember(grid) { HorizontalAxis.ItemPlacer.aligned(spacing = { grid.labelSpacing }) },
            ),
            marker = marker,
            markerVisibilityListener = listener,
            persistentMarkers = if (selectedX != null) { _ -> marker at grid.snapX(selectedX) } else null,
            getXStep = { grid.step },
        ),
        modelProducer = producer,
        // Value changes (another filter, metric or run) interpolate like a meter (Motion.VALUE_MS).
        animationSpec = Motion.value(),
        modifier = modifier.fillMaxWidth().height(height),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}

/** Regular x grid for Vico: ~[TARGET_CELLS] cells over the data span, step a 1-2-5 value, 4 decimals max. */
internal class ChartGrid private constructor(val step: Double, private val origin: Double, val labelSpacing: Int) {
    fun snapX(x: Double): Double =
        Math.round((origin + Math.round((x - origin) / step) * step) * 10_000.0) / 10_000.0

    /** Points moved onto the grid; points landing on the same x are averaged. */
    fun snap(points: List<ChartPoint>): List<ChartPoint> =
        points.groupBy { snapX(it.x) }.map { (x, group) -> ChartPoint(x, group.sumOf { it.y } / group.size) }
            .sortedBy { it.x }

    companion object {
        private const val TARGET_CELLS = 200
        private const val TARGET_LABELS = 5
        private const val MIN_STEP = 0.0001

        /**
         * [minLabelStep] is the finest x interval the x formatter can show apart (0.1 for "1.2 km").
         * Labels are spaced on a 1-2-5 interval that is a whole number of grid steps; an arbitrary
         * interval such as 0.075 km would print "0.1" twice.
         */
        fun of(points: List<ChartPoint>, minLabelStep: Double = 0.0): ChartGrid {
            val min = points.minOf { it.x }
            val max = points.maxOf { it.x }
            val step = niceStep((max - min) / TARGET_CELLS)
            val origin = Math.round(Math.floor(min / step) * step * 10_000.0) / 10_000.0
            return ChartGrid(step, origin, labelSpacing(step, maxOf((max - min) / TARGET_LABELS, minLabelStep, step)))
        }

        private fun labelSpacing(step: Double, wanted: Double): Int {
            var candidate = niceY(wanted)
            repeat(6) {
                val ratio = candidate / step
                if (abs(ratio - Math.round(ratio)) < 1e-6) return Math.round(ratio).toInt().coerceAtLeast(1)
                candidate = niceY(candidate * 1.000001) // next 1-2-5 value up
            }
            return Math.round(candidate / step).toInt().coerceAtLeast(1)
        }

        /** 1-2-5 step of any size (y axis). */
        internal fun niceY(raw: Double): Double {
            if (!raw.isFinite() || raw <= 0.0) return 1.0
            val magnitude = Math.pow(10.0, Math.floor(Math.log10(raw)))
            return listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= raw - 1e-12 } * magnitude
        }

        internal fun niceStep(raw: Double): Double {
            if (!raw.isFinite() || raw <= MIN_STEP) return MIN_STEP
            val magnitude = Math.pow(10.0, Math.floor(Math.log10(raw)))
            val nice = listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= raw } * magnitude
            return (Math.round(nice * 10_000.0) / 10_000.0).coerceAtLeast(MIN_STEP)
        }
    }
}

/** Y axis: a label step and a range that starts on a step multiple, so labels land on round values. */
internal class YAxisPlan(val step: Double, val bounds: ClosedFloatingPointRange<Double>) {
    companion object {
        /** Pace labels are durations: whole 15 s / 30 s / minutes. */
        private val PACE_STEPS = listOf(15.0, 30.0, 60.0, 120.0, 180.0, 300.0, 600.0, 900.0)
        private const val TARGET_LABELS = 4

        /**
         * [points] carry the real y; [sign] -1 plots -y (pace). An inverted axis stays below 0.
         * [minStep] is the finest step the y formatter can show apart (1.0 for whole metres); a finer
         * step on flat data would print the same label twice (e.g. 4.5 / 5 / 5.5 m as "5 m, 5 m, 6 m").
         */
        fun of(points: List<ChartPoint>, sign: Double, minStep: Double = 0.0): YAxisPlan {
            val ys = points.map { it.y * sign }
            val lo = ys.min()
            val hi = ys.max()
            val span = (hi - lo).takeIf { it > 1e-9 } ?: maxOf(abs(hi) * 0.1, 1.0)
            val raw = span / TARGET_LABELS
            val step = if (sign < 0) PACE_STEPS.firstOrNull { it >= raw } ?: (Math.ceil(raw / 600.0) * 600.0)
            else ChartGrid.niceY(maxOf(raw, minStep))
            var bottom = Math.floor(lo / step) * step
            var top = Math.ceil(hi / step) * step
            if (top - bottom < step * 1.5) { // flat line: give it a step either side
                top += step
                bottom -= step
            }
            // Pace never reaches 0 (no "--:--" label at the top): in the rare case that rounding up hits 0,
            // stop just above the fastest value instead (labels then sit off the round values).
            if (sign < 0 && top >= 0.0) top = hi * 0.9
            return YAxisPlan(step, bottom..top)
        }
    }
}
