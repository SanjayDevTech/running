package com.example.running.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.running.R
import com.example.running.core.model.GeoBounds
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.MapRoute
import com.example.running.core.model.RouteMapMode
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.Danger
import com.example.running.ui.theme.Motion
import com.example.running.ui.theme.Raised1
import com.example.running.ui.theme.Success
import com.example.running.ui.theme.Walk
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val LIVE_ZOOM = 17.0
private const val MAX_FIT_ZOOM = 17.5
private const val MIN_FIT_SPAN_METERS = 60.0
private const val MAX_DISPLAY_POINTS = 4000
private val FIT_MARGIN = 32.dp

/** CONTRACT(C). Public signature frozen (architecture.md §3.4). */
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
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val description = stringResource(R.string.map_route_description)
    val currentOnClick by rememberUpdatedState(onClick)

    val mapView = remember {
        MapRuntime.initialize(context)
        MapView(
            context,
            MapLibreMapOptions.createFromAttributes(context)
                .textureMode(true)
                .logoEnabled(false)
                .compassEnabled(false)
                .attributionEnabled(true),
        )
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var layers by remember { mutableStateOf<RouteLayers?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(mapView, lifecycle) {
        val host = MapViewHost(mapView)
        lifecycle.addObserver(host)
        var disposed = false
        mapView.getMapAsync { m ->
            if (disposed) return@getMapAsync
            m.uiSettings.apply {
                setLogoEnabled(false)
                setCompassEnabled(false)
                setTiltGesturesEnabled(false)
                setAttributionGravity(android.view.Gravity.BOTTOM or android.view.Gravity.END)
            }
            m.setMaxZoomPreference(MAX_FIT_ZOOM + 0.5)
            m.addOnMapClickListener {
                val cb = currentOnClick
                cb?.invoke()
                cb != null
            }
            m.setStyle(Style.Builder().fromUri(MapRuntime.styleUri(context))) { style ->
                if (!disposed) {
                    layers = RouteLayers.install(style)
                    map = m
                }
            }
        }
        onDispose {
            disposed = true
            lifecycle.removeObserver(host)
            host.dispose()
        }
    }

    val ready = map != null && layers != null

    // Gestures follow the mode.
    LaunchedEffect(map, mode) {
        map?.uiSettings?.setAllGesturesEnabled(mode == RouteMapMode.INTERACTIVE)
        map?.uiSettings?.setTiltGesturesEnabled(false)
    }

    // Camera: fit the route (STATIC / INTERACTIVE) once the view has a size.
    val bounds = route?.bounds
    val padLeft = with(density) { (contentPadding.calculateStartPadding(layoutDirection) + FIT_MARGIN).roundToPx() }
    val padRight = with(density) { (contentPadding.calculateEndPadding(layoutDirection) + FIT_MARGIN).roundToPx() }
    val padTop = with(density) { (contentPadding.calculateTopPadding() + FIT_MARGIN).roundToPx() }
    val padBottom = with(density) { (contentPadding.calculateBottomPadding() + FIT_MARGIN).roundToPx() }
    LaunchedEffect(ready, mode, bounds, size, padLeft, padTop, padRight, padBottom) {
        val m = map ?: return@LaunchedEffect
        if (mode == RouteMapMode.LIVE || bounds == null || size.width == 0 || size.height == 0) return@LaunchedEffect
        if (size.width <= padLeft + padRight || size.height <= padTop + padBottom) return@LaunchedEffect
        m.moveCamera(CameraUpdateFactory.newLatLngBounds(fitBounds(bounds), padLeft, padTop, padRight, padBottom))
    }

    // Route lines, draw-in animation and markers.
    LaunchedEffect(layers, route, animateDrawIn, mode) {
        val l = layers ?: return@LaunchedEffect
        val r = route
        if (r == null || r.points.size < 2) {
            l.route.setGeoJson(RouteGeoJson.EMPTY)
            l.markers.setGeoJson(RouteGeoJson.EMPTY)
            return@LaunchedEffect
        }
        val points = RouteSegments.decimate(r.points, MAX_DISPLAY_POINTS)
        val normalizer = PaceNormalizer.of(r)
        val showEnd = mode != RouteMapMode.LIVE
        val startMarker = GeoPoint(points.first().lat, points.first().lon) to Success.toArgb()
        val endMarker = GeoPoint(points.last().lat, points.last().lon) to Danger.toArgb()

        fun push(visible: List<com.example.running.core.model.RoutePoint>) {
            l.route.setGeoJson(RouteGeoJson.lines(RouteSegments.build(r, visible, normalizer)))
        }

        l.markers.setGeoJson(RouteGeoJson.points(listOf(startMarker)))
        if (animateDrawIn && mode != RouteMapMode.LIVE) {
            val total = points.last().distanceM
            val anim = Animatable(0f)
            // Starts once the Run detail push slide has landed, then eases like the rest of the app.
            anim.animateTo(1f, tween(Motion.DRAW_IN_MS, delayMillis = Motion.DRAW_IN_DELAY_MS, easing = Motion.Easing)) {
                push(RouteSegments.truncateToDistance(points, total * value))
            }
        }
        push(points)
        l.markers.setGeoJson(RouteGeoJson.points(if (showEnd) listOf(startMarker, endMarker) else listOf(startMarker)))
    }

    LaunchedEffect(layers, route, highlightDistanceM) {
        val l = layers ?: return@LaunchedEffect
        val range = highlightDistanceM
        val sliced = if (route != null && range != null) RouteSegments.slice(route.points, range) else emptyList()
        l.highlight.setGeoJson(
            if (sliced.size >= 2) RouteGeoJson.tintedLines(listOf(sliced.map { GeoPoint(it.lat, it.lon) } to Accent.toArgb()))
            else RouteGeoJson.EMPTY,
        )
    }

    LaunchedEffect(layers, comparison) {
        val l = layers ?: return@LaunchedEffect
        l.comparison.setGeoJson(
            RouteGeoJson.tintedLines(
                comparison.map { c ->
                    RouteSegments.decimate(c.points, MAX_DISPLAY_POINTS).map { GeoPoint(it.lat, it.lon) } to (c.tintArgb ?: Walk.toArgb())
                },
            ),
        )
    }

    // User dot, and follow camera in LIVE mode (north-up).
    LaunchedEffect(layers, userLocation) {
        val l = layers ?: return@LaunchedEffect
        l.user.setGeoJson(userLocation?.let { RouteGeoJson.points(listOf(it to Accent.toArgb())) } ?: RouteGeoJson.EMPTY)
    }
    var followed by remember { mutableStateOf(false) }
    LaunchedEffect(ready, mode, userLocation) {
        val m = map ?: return@LaunchedEffect
        val u = userLocation ?: return@LaunchedEffect
        if (mode != RouteMapMode.LIVE) return@LaunchedEffect
        val target = CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(LatLng(u.lat, u.lon)).zoom(LIVE_ZOOM).bearing(0.0).tilt(0.0).build(),
        )
        if (followed) m.easeCamera(target, 900) else m.moveCamera(target)
        followed = true
    }

    // LIVE always shows the map, also with no position yet (location off, permission missing, no fix).
    val showPlaceholder = route == null && userLocation == null && mode != RouteMapMode.LIVE
    val clickable = if (onClick != null && mode != RouteMapMode.INTERACTIVE) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier
            .background(Raised1)
            .semantics { contentDescription = description }
            .onSizeChanged { size = it }
            .then(clickable),
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (showPlaceholder) Box(Modifier.fillMaxSize().background(Raised1))
    }
}

/** Fit box with a minimum extent so a very short or single-point run still gets a sensible zoom. */
private fun fitBounds(b: GeoBounds): LatLngBounds {
    val padded = b.padded(MIN_FIT_SPAN_METERS)
    return LatLngBounds.from(padded.maxLat, padded.maxLon, padded.minLat, padded.minLon)
}

/** Forwards lifecycle events to the MapView and tolerates being disposed in any state. */
private class MapViewHost(private val view: MapView) : LifecycleEventObserver {
    private var created = false
    private var started = false
    private var resumed = false
    private var destroyed = false

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> if (!created && !destroyed) { created = true; view.onCreate(null) }
            Lifecycle.Event.ON_START -> if (created && !started) { started = true; view.onStart() }
            Lifecycle.Event.ON_RESUME -> if (started && !resumed) { resumed = true; view.onResume() }
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_STOP -> stop()
            Lifecycle.Event.ON_DESTROY -> destroy()
            else -> Unit
        }
    }

    private fun pause() { if (resumed) { resumed = false; view.onPause() } }
    private fun stop() { if (started) { started = false; view.onStop() } }
    private fun destroy() { if (created && !destroyed) { destroyed = true; view.onDestroy() } }

    fun dispose() { pause(); stop(); destroy() }
}
