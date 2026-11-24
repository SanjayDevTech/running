package com.example.running.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.MapRoute
import com.example.running.core.model.RouteMapMode

/** CONTRACT(C). Public signature frozen (architecture.md §3.4). Stub: Canvas polyline. */
@Composable
fun RouteMap(
    route: MapRoute?,
    modifier: Modifier = Modifier,
    mode: RouteMapMode = RouteMapMode.STATIC,
    animateDrawIn: Boolean = false,
    comparison: List<MapRoute> = emptyList(),
    highlightDistanceM: ClosedFloatingPointRange<Double>? = null,
    userLocation: GeoPoint? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
) {
    StubRouteCanvas(
        route = route,
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        contentPadding = contentPadding,
    )
}
