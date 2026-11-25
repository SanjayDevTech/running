package com.example.running.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LIVE_ZOOM = 17.0
private const val MAX_FIT_ZOOM = 17.5
private const val MIN_FIT_SPAN_METERS = 60.0
private const val MAX_DISPLAY_POINTS = 4000

/** The live map re-centres on every fix; 30 fps keeps that ease smooth without rendering at 60-120 Hz. */
private const val LIVE_MAX_FPS = 30

/** Draw-in frames are pushed to MapLibre at most this often (~25 Hz); each push re-tiles the line. */
private const val DRAW_IN_PUSH_MS = 40L
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
        // MapView drops its tile/glyph caches on low memory only when told.
        val memory = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                @Suppress("DEPRECATION") // TRIM_MEMORY_BACKGROUND is still delivered on current APIs
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) mapView.onLowMemory()
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = mapView.onLowMemory()
        }
        context.applicationContext.registerComponentCallbacks(memory)
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
            context.applicationContext.unregisterComponentCallbacks(memory)
            lifecycle.removeObserver(host)
            host.dispose()
        }
    }

    val ready = map != null && layers != null
    val live = mode == RouteMapMode.LIVE

    // Gestures follow the mode; LIVE caps the frame rate of its per-fix camera ease.
    LaunchedEffect(map, mode) {
        map?.uiSettings?.setAllGesturesEnabled(mode == RouteMapMode.INTERACTIVE)
        map?.uiSettings?.setTiltGesturesEnabled(false)
        if (map != null && live) mapView.setMaximumFps(LIVE_MAX_FPS)
    }

    // Keys by identity: a new live route arrives every few seconds, and MapRoute equality walks every point.
    val routeKey = Identity(route)

    // Camera: fit the route (STATIC / INTERACTIVE) once the view has a size. LIVE follows the user instead.
    val bounds = if (live) null else route?.bounds
    val padLeft = with(density) { (contentPadding.calculateStartPadding(layoutDirection) + FIT_MARGIN).roundToPx() }
    val padRight = with(density) { (contentPadding.calculateEndPadding(layoutDirection) + FIT_MARGIN).roundToPx() }
    val padTop = with(density) { (contentPadding.calculateTopPadding() + FIT_MARGIN).roundToPx() }
    val padBottom = with(density) { (contentPadding.calculateBottomPadding() + FIT_MARGIN).roundToPx() }
    LaunchedEffect(ready, mode, bounds, size, padLeft, padTop, padRight, padBottom) {
        val m = map ?: return@LaunchedEffect
        if (bounds == null || size.width == 0 || size.height == 0) return@LaunchedEffect
        if (size.width <= padLeft + padRight || size.height <= padTop + padBottom) return@LaunchedEffect
        m.moveCamera(CameraUpdateFactory.newLatLngBounds(fitBounds(bounds), padLeft, padTop, padRight, padBottom))
    }

    // Route lines, draw-in animation and markers. Colouring and GeoJSON are built off the main thread;
    // only the hand-over to MapLibre runs on it.
    LaunchedEffect(layers, routeKey, animateDrawIn, mode) {
        val l = layers ?: return@LaunchedEffect
        val r = route
        val drawing = r?.let { withContext(Dispatchers.Default) { RouteDrawing.of(it, MAX_DISPLAY_POINTS) } }
        if (drawing == null) {
            l.update(l.route, RouteGeoJson.EMPTY)
            l.update(l.markers, RouteGeoJson.EMPTY)
            return@LaunchedEffect
        }
        val startMarker = drawing.start to Success.toArgb()
        val startOnly = RouteGeoJson.points(listOf(startMarker))
        l.update(l.markers, startOnly)
        if (animateDrawIn && !live) drawIn(l, drawing)
        l.update(l.route, withContext(Dispatchers.Default) { RouteGeoJson.lines(drawing.lines) })
        l.update(l.markers, if (live) startOnly else RouteGeoJson.points(listOf(startMarker, drawing.end to Danger.toArgb())))
    }

    LaunchedEffect(layers, routeKey, highlightDistanceM) {
        val l = layers ?: return@LaunchedEffect
        val range = highlightDistanceM
        val r = route
        val json = if (r == null || range == null) RouteGeoJson.EMPTY else withContext(Dispatchers.Default) {
            val sliced = RouteSegments.slice(r.points, range)
            if (sliced.size >= 2) RouteGeoJson.tintedLines(listOf(sliced.map { GeoPoint(it.lat, it.lon) } to Accent.toArgb()))
            else RouteGeoJson.EMPTY
        }
        l.update(l.highlight, json)
    }

    LaunchedEffect(layers, comparison) {
        val l = layers ?: return@LaunchedEffect
        val json = withContext(Dispatchers.Default) {
            RouteGeoJson.tintedLines(
                comparison.map { c ->
                    RouteSegments.decimate(c.points, MAX_DISPLAY_POINTS).map { GeoPoint(it.lat, it.lon) } to (c.tintArgb ?: Walk.toArgb())
                },
            )
        }
        l.update(l.comparison, json)
    }

    // User dot, and follow camera in LIVE mode (north-up).
    LaunchedEffect(layers, userLocation) {
        val l = layers ?: return@LaunchedEffect
        l.update(l.user, userLocation?.let { RouteGeoJson.points(listOf(it to Accent.toArgb())) } ?: RouteGeoJson.EMPTY)
    }
    var followed by remember { mutableStateOf(false) }
    LaunchedEffect(ready, mode, userLocation) {
        val m = map ?: return@LaunchedEffect
        val u = userLocation ?: return@LaunchedEffect
        if (!live) return@LaunchedEffect
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

/**
 * Draws the route in over [Motion.DRAW_IN_MS]. The Animatable runs per frame (and honours the system
 * animation scale); the line follows it at ~25 Hz, each frame's GeoJSON built off the main thread from
 * the precomputed [RouteDrawing].
 */
private suspend fun drawIn(l: RouteLayers, drawing: RouteDrawing) = coroutineScope {
    val total = drawing.totalDistanceM
    var progress = 0f
    var done = false
    val pusher = launch {
        var pushed = -1f
        while (!done) {
            val f = progress
            if (f != pushed) {
                pushed = f
                l.update(l.route, withContext(Dispatchers.Default) { RouteGeoJson.lines(drawing.linesUpTo(total * f)) })
            }
            delay(DRAW_IN_PUSH_MS)
        }
    }
    l.update(l.route, RouteGeoJson.EMPTY)
    // Starts once the Run detail push slide has landed, then eases like the rest of the app.
    Animatable(0f).animateTo(1f, tween(Motion.DRAW_IN_MS, delayMillis = Motion.DRAW_IN_DELAY_MS, easing = Motion.Easing)) {
        progress = value
    }
    done = true
    pusher.cancel()
}

/** Equality by instance, for effect keys whose values are big and compared often. */
private class Identity<T>(val value: T) {
    override fun equals(other: Any?): Boolean = other is Identity<*> && other.value === value
    override fun hashCode(): Int = System.identityHashCode(value)
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
