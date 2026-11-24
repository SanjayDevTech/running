package com.example.running.ui.components

import com.example.running.ui.components.charts.ChartGrid
import com.example.running.ui.components.charts.ChartPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs
import kotlin.math.round

class ChartGridTest {
    private fun fourDecimals(v: Double) = abs(v * 10_000 - round(v * 10_000)) < 1e-6

    @Test fun snapsArbitraryDistancesToAFourDecimalGrid() {
        // Run-detail pace points: x in km with many decimals (this crashed Vico before snapping).
        val pts = (0 until 300).map { ChartPoint(0.0123457 + it * 0.00345679, 300.0 + it % 7) }
        val grid = ChartGrid.of(pts)
        val snapped = grid.snap(pts)
        assertThat(fourDecimals(grid.step)).isTrue()
        assertThat(snapped.size).isAtLeast(2)
        snapped.forEach { assertThat(fourDecimals(it.x)).isTrue() }
        snapped.zipWithNext { a, b ->
            val cells = (b.x - a.x) / grid.step
            assertThat(abs(cells - round(cells))).isLessThan(1e-6)
            assertThat(b.x).isGreaterThan(a.x)
        }
        assertThat(grid.labelSpacing).isAtLeast(1)
    }

    @Test fun niceStepIsOneTwoFive() {
        assertThat(ChartGrid.niceStep(0.0042)).isEqualTo(0.005)
        assertThat(ChartGrid.niceStep(0.013)).isEqualTo(0.02)
        assertThat(ChartGrid.niceStep(0.0)).isEqualTo(0.0001)
        assertThat(ChartGrid.niceStep(1e-7)).isEqualTo(0.0001)
    }
}
