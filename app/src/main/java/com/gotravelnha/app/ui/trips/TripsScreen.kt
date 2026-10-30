package com.gotravelnha.app.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gotravelnha.app.R
import com.gotravelnha.app.data.TripSummary
import com.gotravelnha.app.ui.AppState
import com.gotravelnha.app.ui.Routes
import com.gotravelnha.app.ui.components.EmptyState
import com.gotravelnha.app.ui.components.LoadContent
import com.gotravelnha.app.ui.components.TripCardView
import com.gotravelnha.app.ui.components.rememberLoader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(state: AppState, routes: Routes) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.tab_trips), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.trips_mine)) })
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.trips_saved)) })
        }
        if (state.signedIn) {
            if (tab == 0) MyTrips(state, routes) else SavedTrips(state, routes)
        }
    }
}

@Composable
private fun MyTrips(state: AppState, routes: Routes) {
    val trips = rememberLoader(state.user?.id) { state.api.myTrips() }
    LoadContent(trips.value, onRetry = trips::reload) { list ->
        if (list.isEmpty()) {
            EmptyState(stringResource(R.string.trips_empty_title), stringResource(R.string.trips_empty_text))
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { trip -> MyTripRow(trip) { routes.myTrip(trip.id) } }
            }
        }
    }
}

@Composable
private fun MyTripRow(trip: TripSummary, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(trip.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text("${trip.destination} · ${dateRange(trip.startDate, trip.endDate)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(pluralStringResource(R.plurals.travellers, trip.party.size, trip.party.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun dateRange(start: String, end: String): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return runCatching { "${LocalDate.parse(start.take(10)).format(format)} – ${LocalDate.parse(end.take(10)).format(format)}" }.getOrDefault("")
}

@Composable
private fun SavedTrips(state: AppState, routes: Routes) {
    val saved = rememberLoader(state.user?.id) { state.api.saved(null).data }
    LoadContent(saved.value, onRetry = saved::reload) { list ->
        if (list.isEmpty()) {
            EmptyState(stringResource(R.string.saved_empty_title), stringResource(R.string.saved_empty_text))
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { trip -> TripCardView(trip, saved = null, onClick = { routes.trip(trip.slug) }) }
            }
        }
    }
}
