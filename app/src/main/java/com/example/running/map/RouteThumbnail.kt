package com.example.running.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.running.core.model.MapRoute

/** CONTRACT(C). Cheap, list-safe (no live MapView per row). Stub: Canvas polyline. */
@Composable
fun RouteThumbnail(route: MapRoute, modifier: Modifier = Modifier, cacheKey: String? = null) {
    StubRouteCanvas(route = route, modifier = modifier)
}
