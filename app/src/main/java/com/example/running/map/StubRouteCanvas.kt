package com.example.running.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.running.core.model.MapRoute
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.Danger
import com.example.running.ui.theme.Raised1
import com.example.running.ui.theme.Success
import kotlin.math.cos
import kotlin.math.max

/** FOUNDATION stub renderer: equirectangular Canvas polyline. Track C replaces it with MapLibre. */
@Composable
internal fun StubRouteCanvas(route: MapRoute?, modifier: Modifier, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    Box(modifier.background(Raised1)) {
        val pts = route?.points.orEmpty()
        val bounds = route?.bounds
        if (pts.size >= 2 && bounds != null) {
            Canvas(Modifier.fillMaxSize().padding(contentPadding).padding(16.dp)) {
                val cosLat = cos(Math.toRadians(bounds.center.lat))
                val spanX = max((bounds.maxLon - bounds.minLon) * cosLat, 1e-9)
                val spanY = max(bounds.maxLat - bounds.minLat, 1e-9)
                val scale = minOf(size.width / spanX, size.height / spanY).toFloat()
                val offX = (size.width - spanX.toFloat() * scale) / 2f
                val offY = (size.height - spanY.toFloat() * scale) / 2f
                fun project(lat: Double, lon: Double) = Offset(
                    offX + ((lon - bounds.minLon) * cosLat).toFloat() * scale,
                    offY + (bounds.maxLat - lat).toFloat() * scale,
                )
                val path = Path()
                pts.forEachIndexed { i, p ->
                    val o = project(p.lat, p.lon)
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(path, Accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(Success, radius = 4.dp.toPx(), center = project(pts.first().lat, pts.first().lon))
                drawCircle(Danger, radius = 4.dp.toPx(), center = project(pts.last().lat, pts.last().lon))
            }
        }
    }
}
