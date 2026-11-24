package com.example.running.map

import androidx.compose.ui.graphics.toArgb
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.Black
import com.example.running.ui.theme.TextPrimary
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/** The GeoJSON sources and overlay layers RouteMap adds on top of the base style. */
internal class RouteLayers private constructor(
    val route: GeoJsonSource,
    val highlight: GeoJsonSource,
    val comparison: GeoJsonSource,
    val markers: GeoJsonSource,
    val user: GeoJsonSource,
) {
    companion object {
        private const val ROUTE = "route"
        private const val HIGHLIGHT = "route-highlight"
        private const val COMPARISON = "route-comparison"
        private const val MARKERS = "route-markers"
        private const val USER = "route-user"

        private val colorProp = Expression.toColor(Expression.get("c"))

        /** Adds sources and layers (bottom to top) to a freshly loaded [style]. */
        fun install(style: Style): RouteLayers {
            val route = GeoJsonSource(ROUTE, RouteGeoJson.EMPTY)
            val highlight = GeoJsonSource(HIGHLIGHT, RouteGeoJson.EMPTY)
            val comparison = GeoJsonSource(COMPARISON, RouteGeoJson.EMPTY)
            val markers = GeoJsonSource(MARKERS, RouteGeoJson.EMPTY)
            val user = GeoJsonSource(USER, RouteGeoJson.EMPTY)
            listOf(route, highlight, comparison, markers, user).forEach(style::addSource)

            style.addLayer(
                LineLayer("$ROUTE-casing", ROUTE).withProperties(
                    PropertyFactory.lineColor(Black.toArgb()),
                    PropertyFactory.lineOpacity(0.55f),
                    PropertyFactory.lineWidth(8f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            style.addLayer(
                LineLayer("$HIGHLIGHT-line", HIGHLIGHT).withProperties(
                    PropertyFactory.lineColor(TextPrimary.toArgb()),
                    PropertyFactory.lineWidth(12f),
                    PropertyFactory.lineOpacity(0.9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            style.addLayer(
                LineLayer("$ROUTE-solid", ROUTE).withProperties(
                    PropertyFactory.lineColor(colorProp),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ).also { it.setFilter(Expression.eq(Expression.get("sf"), Expression.literal(false))) },
            )
            style.addLayer(
                LineLayer("$ROUTE-dashed", ROUTE).withProperties(
                    PropertyFactory.lineColor(colorProp),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineDasharray(arrayOf(1.2f, 1.4f)),
                    PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ).also { it.setFilter(Expression.eq(Expression.get("sf"), Expression.literal(true))) },
            )
            style.addLayer(
                LineLayer("$COMPARISON-line", COMPARISON).withProperties(
                    PropertyFactory.lineColor(colorProp),
                    PropertyFactory.lineWidth(2.5f),
                    PropertyFactory.lineOpacity(0.95f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            style.addLayer(
                CircleLayer("$MARKERS-dot", MARKERS).withProperties(
                    PropertyFactory.circleColor(colorProp),
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleStrokeColor(TextPrimary.toArgb()),
                    PropertyFactory.circleStrokeWidth(2.5f),
                ),
            )
            style.addLayer(
                CircleLayer("$USER-halo", USER).withProperties(
                    PropertyFactory.circleColor(Accent.toArgb()),
                    PropertyFactory.circleOpacity(0.25f),
                    PropertyFactory.circleRadius(16f),
                ),
            )
            style.addLayer(
                CircleLayer("$USER-dot", USER).withProperties(
                    PropertyFactory.circleColor(Accent.toArgb()),
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleStrokeColor(TextPrimary.toArgb()),
                    PropertyFactory.circleStrokeWidth(2.5f),
                ),
            )
            return RouteLayers(route, highlight, comparison, markers, user)
        }
    }
}
