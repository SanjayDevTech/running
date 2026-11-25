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
import com.example.running.core.model.GeoBounds
import com.example.running.core.model.MapRoute
import com.example.running.ui.theme.Danger
import com.example.running.ui.theme.Raised2
import com.example.running.ui.theme.Success

private const val THUMBNAIL_MAX_POINTS = 240

/**
 * What a thumbnail draws, small enough to cache per run: at most [THUMBNAIL_MAX_POINTS] points split
 * into lines coloured against the full route's pace range, and the full route's bounds.
 */
class ThumbnailRoute internal constructor(internal val lines: List<RouteLine>, internal val bounds: GeoBounds) {
    companion object {
        /** Null when there is nothing to draw. Heavy (colours the whole route): call off the main thread. */
        fun of(route: MapRoute): ThumbnailRoute? {
            val bounds = route.bounds ?: return null
            val lines = RouteSegments.build(route, RouteSegments.decimate(route.points, THUMBNAIL_MAX_POINTS), PaceNormalizer.of(route))
            return if (lines.isEmpty()) null else ThumbnailRoute(lines, bounds)
        }
    }
}

/**
 * CONTRACT(C). Cheap, list-safe: a plain Canvas, never a MapView. Same pace colours and dashed
 * step-filled parts as [RouteMap]. [cacheKey] (e.g. the run id) keys the remembered geometry.
 */
@Composable
fun RouteThumbnail(route: MapRoute, modifier: Modifier = Modifier, cacheKey: String? = null) {
    val thumbnail = remember(cacheKey ?: route) { ThumbnailRoute.of(route) }
    if (thumbnail != null) RouteThumbnail(thumbnail, modifier) else RouteThumbnailBox(modifier) {}
}

/** A thumbnail prepared ahead (e.g. off the main thread and cached by a list's ViewModel). */
@Composable
fun RouteThumbnail(thumbnail: ThumbnailRoute, modifier: Modifier = Modifier) {
    val lines = thumbnail.lines
    val bounds = thumbnail.bounds
    RouteThumbnailBox(modifier) {
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

@Composable
private fun RouteThumbnailBox(modifier: Modifier, content: @Composable () -> Unit) {
    val description = stringResource(R.string.map_thumbnail_description)
    Box(modifier.background(Raised2).semantics { contentDescription = description }) { content() }
}
