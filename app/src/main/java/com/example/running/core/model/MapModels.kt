package com.example.running.core.model

data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val distanceM: Double,
    val paceSecPerKm: Double?,
    val stepFilled: Boolean,
    val segmentKind: SegmentKind?,
)

enum class RouteColoring { PACE, SOLID, SEGMENT_KIND }

data class MapRoute(
    val points: List<RoutePoint>,
    val coloring: RouteColoring = RouteColoring.PACE,
    val tintArgb: Int? = null,
    val label: String? = null,
) {
    val bounds: GeoBounds? by lazy { GeoBounds.of(points.map { GeoPoint(it.lat, it.lon) }) }

    companion object {
        /**
         * Uses each point's own distanceM when set, otherwise a running haversine sum. Points recorded
         * while paused are dropped: movement during a pause is not part of the drawn route.
         */
        fun fromTrackPoints(points: List<TrackPoint>, coloring: RouteColoring = RouteColoring.PACE): MapRoute {
            var cumulative = 0.0
            var prev: TrackPoint? = null
            val out = points.filter { !it.paused }.map { p ->
                prev?.let { cumulative += com.example.running.core.util.haversineMeters(it.lat, it.lon, p.lat, p.lon) }
                prev = p
                RoutePoint(
                    lat = p.lat,
                    lon = p.lon,
                    distanceM = p.distanceM ?: cumulative,
                    paceSecPerKm = p.paceSecPerKm,
                    stepFilled = p.stepFilled,
                    segmentKind = p.segmentKind,
                )
            }
            return MapRoute(out, coloring)
        }
    }
}

/** STATIC: fit bounds, no gestures; LIVE: follow user, north-up. */
enum class RouteMapMode { STATIC, INTERACTIVE, LIVE }

enum class OfflineAreaStatus { NONE, DOWNLOADING, READY, FAILED }
