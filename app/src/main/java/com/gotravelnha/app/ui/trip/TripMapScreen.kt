package com.gotravelnha.app.ui.trip

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gotravelnha.app.R
import com.gotravelnha.app.data.MapDay
import com.gotravelnha.app.data.TripMap
import com.gotravelnha.app.ui.AppState
import com.gotravelnha.app.ui.Routes
import com.gotravelnha.app.ui.components.BackTopBar
import com.gotravelnha.app.ui.components.ChoiceChip
import com.gotravelnha.app.ui.components.EmptyState
import com.gotravelnha.app.ui.components.LoadContent
import com.gotravelnha.app.ui.components.rememberLoader
import com.gotravelnha.app.ui.components.slotLabel
import com.gotravelnha.app.ui.theme.Brand
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Each stop pinned (places named by AI, coordinates from OpenStreetMap; api: /community/trips/:slug/map) */
@Composable
fun TripMapScreen(state: AppState, routes: Routes, slug: String, initialDay: Int?) {
    // The first request for a trip may take a while: places are geocoded one a second
    val map = rememberLoader(slug) { state.api.tripMap(slug) }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.trip_map_title), onBack = { routes.back() })
        LoadContent(map.value, onRetry = map::reload) { data ->
            if (data.days.none { it.stops.isNotEmpty() }) {
                EmptyState(stringResource(R.string.trip_map_title), stringResource(R.string.trip_map_failed))
            } else {
                MapBody(data, initialDay)
            }
        }
    }
}

@Composable
private fun MapBody(data: TripMap, initialDay: Int?) {
    var selectedDay by remember { mutableStateOf(initialDay?.takeIf { day -> data.days.any { it.day == day } }) }
    var focus by remember { mutableStateOf<GeoPoint?>(null) }
    val visible = data.days.filter { selectedDay == null || it.day == selectedDay }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ChoiceChip(stringResource(R.string.trip_map_all_days), selectedDay == null, { selectedDay = null }) }
            data.days.forEach { day ->
                item { ChoiceChip(stringResource(R.string.day_number, day.day), selectedDay == day.day, { selectedDay = day.day }, dot = Brand.day(day.day)) }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            OsmMap(context, visible, data, focus)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(0.8f)) {
            visible.forEach { day ->
                item(key = "h${day.day}") {
                    Text(
                        stringResource(R.string.day_title, day.day, day.theme),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                    )
                }
                if (day.stops.isEmpty()) item(key = "e${day.day}") { Text(stringResource(R.string.trip_map_no_stops), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                day.stops.forEachIndexed { index, stop ->
                    item(key = "${day.day}-$index") {
                        Row(
                            Modifier.fillMaxWidth().clickable { focus = GeoPoint(stop.lat, stop.lon) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(Brand.day(day.day)), contentAlignment = Alignment.Center) {
                                Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text(stop.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    slotLabel(stop.slot) + if (stop.place != stop.title) " · ${stop.place}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            if (data.unmapped > 0) {
                item { Text(pluralStringResource(R.plurals.trip_map_unmapped, data.unmapped, data.unmapped), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun OsmMap(context: Context, days: List<MapDay>, data: TripMap, focus: GeoPoint?) {
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }
    AndroidView({ mapView }, Modifier.fillMaxSize()) { view ->
        view.overlays.clear()
        val points = mutableListOf<GeoPoint>()
        days.forEach { day ->
            val colour = Brand.day(day.day).toArgb()
            val route = day.stops.map { GeoPoint(it.lat, it.lon) }
            if (route.size > 1) {
                view.overlays += Polyline(view).apply {
                    setPoints(route)
                    outlinePaint.color = colour
                    outlinePaint.strokeWidth = 7f
                    outlinePaint.alpha = 180
                }
            }
            day.stops.forEachIndexed { index, stop ->
                view.overlays += Marker(view).apply {
                    position = GeoPoint(stop.lat, stop.lon)
                    title = stop.title
                    snippet = stop.place
                    icon = numberedPin(context, index + 1, colour)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
            }
            points += route
        }
        view.invalidate()
        view.post {
            when {
                focus != null -> view.controller.animateTo(focus, 16.0, 600L)
                points.size > 1 -> view.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points).increaseByScale(1.3f), false)
                points.size == 1 -> { view.controller.setZoom(14.0); view.controller.setCenter(points[0]) }
                data.centre != null -> { view.controller.setZoom(11.0); view.controller.setCenter(GeoPoint(data.centre.lat, data.centre.lon)) }
            }
        }
    }
}

private fun numberedPin(context: Context, number: Int, colour: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (30 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = colour
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2 * density, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 12 * density
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText("$number", size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
    return BitmapDrawable(context.resources, bitmap)
}
