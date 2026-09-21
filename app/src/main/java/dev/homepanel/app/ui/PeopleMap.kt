package dev.homepanel.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.homepanel.app.R
import dev.homepanel.app.network.HomeZoneLocation
import dev.homepanel.app.network.OsmTileClient
import dev.homepanel.app.network.PersonLocation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

private const val TILE_SIZE = 256.0
private const val MIN_ZOOM = 2
private const val MAX_ZOOM = 18
private const val EARTH_RADIUS_METERS = 6_378_137.0
private const val PINCH_ZOOM_IN_THRESHOLD = 1.18f
private const val PINCH_ZOOM_OUT_THRESHOLD = 0.84f

private data class GeoPoint(val latitude: Double, val longitude: Double)

private data class MapViewport(
    val zoom: Int,
    val centerWorldX: Double,
    val centerWorldY: Double,
    val leftWorld: Double,
    val topWorld: Double,
    val widthPx: Int,
    val heightPx: Int
)

private data class RenderTile(
    val zoom: Int,
    val serverX: Int,
    val y: Int,
    val worldTileX: Int,
    val worldTileY: Int
) {
    val id: String = "$zoom/$serverX/$y"
}

/**
 * Interactive people map.
 *
 * Online mode downloads visible OpenStreetMap tiles and stores them persistently.
 * Offline mode performs no network calls and renders the same map from previously cached tiles.
 */
@Composable
fun OsmPeopleMap(
    persons: List<PersonLocation>,
    zones: List<HomeZoneLocation>,
    modifier: Modifier = Modifier,
    allowNetwork: Boolean = true
) {
    val context = LocalContext.current
    val tileClient = remember { OsmTileClient(context.applicationContext) }
    val tiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var zoomAdjustment by remember { mutableIntStateOf(0) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var pinchAccumulator by remember { mutableStateOf(1f) }
    var failedTiles by remember { mutableIntStateOf(0) }

    val coordinates = remember(persons, zones) {
        buildList {
            persons.forEach { person ->
                val lat = person.latitude
                val lon = person.longitude
                if (lat != null && lon != null) add(GeoPoint(lat, lon))
            }
            zones.forEach { add(GeoPoint(it.latitude, it.longitude)) }
        }
    }

    val viewport = remember(coordinates, containerSize, zoomAdjustment, panOffset) {
        if (coordinates.isEmpty() || containerSize.width <= 0 || containerSize.height <= 0) null
        else calculateViewport(coordinates, containerSize, zoomAdjustment, panOffset)
    }
    val visibleTiles = remember(viewport) { viewport?.let(::visibleTiles).orEmpty() }

    LaunchedEffect(visibleTiles, allowNetwork) {
        failedTiles = 0
        val missing = visibleTiles.filterNot { tiles.containsKey(it.id) }
        missing.chunked(6).forEach { batch ->
            val results = coroutineScope {
                batch.map { tile ->
                    async {
                        val bitmap = runCatching {
                            tileClient.loadTile(tile.zoom, tile.serverX, tile.y, allowNetwork = allowNetwork)
                        }.getOrNull()
                        tile to bitmap
                    }
                }.awaitAll()
            }
            results.forEach { (tile, bitmap) ->
                if (bitmap != null) tiles[tile.id] = bitmap.asImageBitmap() else failedTiles++
            }
        }
    }

    fun changeZoom(delta: Int) {
        val currentZoom = viewport?.zoom ?: return
        if (delta > 0 && currentZoom >= MAX_ZOOM) return
        if (delta < 0 && currentZoom <= MIN_ZOOM) return
        zoomAdjustment += delta
        panOffset = if (delta > 0) {
            Offset(panOffset.x * 2f, panOffset.y * 2f)
        } else {
            Offset(panOffset.x / 2f, panOffset.y / 2f)
        }
        pinchAccumulator = 1f
    }

    val surface = MaterialTheme.colorScheme.surfaceVariant
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
    val zoneFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    val zoneStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
    val personFill = MaterialTheme.colorScheme.tertiary
    val personStroke = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(coordinates, allowNetwork) {
                detectTransformGestures { _, pan, zoom, _ ->
                    panOffset = panOffset + pan
                    pinchAccumulator *= zoom
                    when {
                        pinchAccumulator >= PINCH_ZOOM_IN_THRESHOLD -> changeZoom(+1)
                        pinchAccumulator <= PINCH_ZOOM_OUT_THRESHOLD -> changeZoom(-1)
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(surface)
            for (i in 1..4) {
                val x = size.width * i / 5f
                val y = size.height * i / 5f
                drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            val current = viewport ?: return@Canvas
            visibleTiles.forEach { tile ->
                val image = tiles[tile.id] ?: return@forEach
                val left = tile.worldTileX * TILE_SIZE - current.leftWorld
                val top = tile.worldTileY * TILE_SIZE - current.topWorld
                drawImage(image, topLeft = Offset(left.toFloat(), top.toFloat()))
            }

            fun point(latitude: Double, longitude: Double): Offset {
                val world = worldPixel(latitude, longitude, current.zoom)
                return Offset(
                    (world.first - current.leftWorld).toFloat(),
                    (world.second - current.topWorld).toFloat()
                )
            }

            zones.forEach { zone ->
                val center = point(zone.latitude, zone.longitude)
                val metersPerPixel = cos(Math.toRadians(zone.latitude)).coerceAtLeast(0.05) *
                    2.0 * PI * EARTH_RADIUS_METERS /
                    (TILE_SIZE * 2.0.pow(current.zoom.toDouble()))
                val radiusPx = (zone.radiusMeters / metersPerPixel).toFloat().coerceIn(7f, 120f)
                drawCircle(zoneFill, radiusPx, center)
                drawCircle(
                    zoneStroke,
                    radiusPx,
                    center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
            }

            persons.forEachIndexed { index, person ->
                val lat = person.latitude ?: return@forEachIndexed
                val lon = person.longitude ?: return@forEachIndexed
                val base = point(lat, lon)
                val duplicateCount = persons.count { it.latitude == lat && it.longitude == lon }
                val angle = (index % 8) * PI / 4.0
                val displacement = if (duplicateCount > 1) {
                    Offset((cos(angle) * 12.0).toFloat(), (sin(angle) * 12.0).toFloat())
                } else Offset.Zero
                val center = base + displacement
                drawCircle(personStroke, radius = 13f, center = center)
                drawCircle(personFill, radius = 9f, center = center)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            FilledTonalButton(
                onClick = { changeZoom(+1) },
                modifier = Modifier.size(48.dp)
            ) { Text("+") }
            FilledTonalButton(
                onClick = { changeZoom(-1) },
                modifier = Modifier.size(48.dp)
            ) { Text("−") }
            FilledTonalButton(
                onClick = {
                    zoomAdjustment = 0
                    panOffset = Offset.Zero
                    pinchAccumulator = 1f
                },
                modifier = Modifier.size(48.dp)
            ) { Text("⌂") }
        }

        if (failedTiles > 0 && visibleTiles.none { tiles.containsKey(it.id) }) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(18.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    stringResource(
                        if (allowNetwork) R.string.people_map_osm_unavailable
                        else R.string.people_map_offline_cache_empty
                    ),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

private fun calculateViewport(
    points: List<GeoPoint>,
    size: IntSize,
    zoomAdjustment: Int,
    panOffset: Offset
): MapViewport {
    val autoZoom = if (points.size <= 1) 14 else {
        (MAX_ZOOM downTo MIN_ZOOM).firstOrNull { zoom ->
            val pixels = points.map { worldPixel(it.latitude, it.longitude, zoom) }
            val spanX = pixels.maxOf { it.first } - pixels.minOf { it.first }
            val spanY = pixels.maxOf { it.second } - pixels.minOf { it.second }
            spanX <= size.width * 0.72 && spanY <= size.height * 0.72
        } ?: MIN_ZOOM
    }
    val zoom = (autoZoom + zoomAdjustment).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val pixels = points.map { worldPixel(it.latitude, it.longitude, zoom) }
    val centerX = (pixels.minOf { it.first } + pixels.maxOf { it.first }) / 2.0
    val centerY = (pixels.minOf { it.second } + pixels.maxOf { it.second }) / 2.0
    return MapViewport(
        zoom = zoom,
        centerWorldX = centerX,
        centerWorldY = centerY,
        leftWorld = centerX - size.width / 2.0 - panOffset.x,
        topWorld = centerY - size.height / 2.0 - panOffset.y,
        widthPx = size.width,
        heightPx = size.height
    )
}

private fun visibleTiles(viewport: MapViewport): List<RenderTile> {
    val right = viewport.leftWorld + viewport.widthPx
    val bottom = viewport.topWorld + viewport.heightPx
    val startX = floor(viewport.leftWorld / TILE_SIZE).toInt() - 1
    val endX = floor(right / TILE_SIZE).toInt() + 1
    val startY = floor(viewport.topWorld / TILE_SIZE).toInt() - 1
    val endY = floor(bottom / TILE_SIZE).toInt() + 1
    val tileCount = 1 shl viewport.zoom

    return buildList {
        for (worldX in startX..endX) {
            val serverX = ((worldX % tileCount) + tileCount) % tileCount
            for (worldY in startY..endY) {
                if (worldY !in 0 until tileCount) continue
                add(RenderTile(viewport.zoom, serverX, worldY, worldX, worldY))
            }
        }
    }
}

private fun worldPixel(latitude: Double, longitude: Double, zoom: Int): Pair<Double, Double> {
    val clampedLat = latitude.coerceIn(-85.05112878, 85.05112878)
    val worldSize = TILE_SIZE * 2.0.pow(zoom.toDouble())
    val x = (longitude + 180.0) / 360.0 * worldSize
    val sinLatitude = sin(Math.toRadians(clampedLat))
    val y = (0.5 - ln((1.0 + sinLatitude) / (1.0 - sinLatitude)) / (4.0 * PI)) * worldSize
    return x to y
}
