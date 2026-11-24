package com.example.running.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.running.R
import com.example.running.core.model.MapRoute
import com.example.running.ui.theme.Danger
import com.example.running.ui.theme.Raised2
import com.example.running.ui.theme.Success

private const val THUMBNAIL_MAX_POINTS = 240

/**
 * CONTRACT(C). Cheap, list-safe: a plain Canvas, never a MapView. Same pace colours and dashed
 * step-filled parts as [RouteMap]. [cacheKey] (e.g. the run id) keys the remembered geometry.
 */
@Composable
fun RouteThumbnail(route: MapRoute, modifier: Modifier = Modifier, cacheKey: String? = null) {
    val description = stringResource(R.string.map_thumbnail_description)
    val lines = remember(cacheKey ?: route) {
        RouteSegments.build(route, RouteSegments.decimate(route.points, THUMBNAIL_MAX_POINTS), PaceNormalizer.of(route))
    }
    val bounds = route.bounds
    Box(modifier.background(Raised2).semantics { contentDescription = description }) {
        if (bounds != null && lines.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val projector = RouteProjector(
                    bounds.minLat, bounds.minLon, bounds.maxLat, bounds.maxLon,
                    size.width, size.height, padding = 12.dp.toPx(),
                )
                val strokeWidth = 3.dp.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 1.2f, strokeWidth * 1.4f))
                lines.forEach { line ->
                    val path = Path()
                    line.coords.forEachIndexed { i, p ->
                        val x = projector.x(p.lon)
                        val y = projector.y(p.lat)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path,
                        Color(line.argb),
                        style = Stroke(
                            width = strokeWidth,
                            cap = if (line.stepFilled) StrokeCap.Butt else StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = if (line.stepFilled) dash else null,
                        ),
                    )
                }
                val first = lines.first().coords.first()
                val last = lines.last().coords.last()
                drawCircle(Success, 3.5.dp.toPx(), Offset(projector.x(first.lon), projector.y(first.lat)))
                drawCircle(Danger, 3.5.dp.toPx(), Offset(projector.x(last.lon), projector.y(last.lat)))
            }
        }
    }
}
