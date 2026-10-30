package com.gotravelnha.app.ui.trips

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gotravelnha.app.BuildConfig
import com.gotravelnha.app.R
import com.gotravelnha.app.booking.daySuggestions
import com.gotravelnha.app.ui.AppState
import com.gotravelnha.app.ui.Routes
import com.gotravelnha.app.ui.components.BackTopBar
import com.gotravelnha.app.ui.components.Load
import com.gotravelnha.app.ui.components.LoadContent
import com.gotravelnha.app.ui.components.itineraryDays
import com.gotravelnha.app.ui.components.rememberLoader
import kotlinx.coroutines.launch

@Composable
fun MyTripDetailScreen(state: AppState, routes: Routes, id: String) {
    val trip = rememberLoader(id) { state.api.myTrip(id) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var shareId by remember(trip.value) { mutableStateOf((trip.value as? Load.Ready)?.value?.data?.shareId) }
    val title = (trip.value as? Load.Ready)?.value?.data?.title.orEmpty()

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title, onBack = { routes.back() }, actions = {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.mytrip_delete)) }
        })
        LoadContent(trip.value, onRetry = trip::reload) { response ->
            val saved = response.data
            val suggestions = remember(response) { daySuggestions(saved.itinerary.days, response.activities, saved.destination) }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(saved.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${saved.destination} · ${dateRange(saved.startDate, saved.endDate)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(pluralStringResource(R.plurals.travellers, saved.party.size, saved.party.size), style = MaterialTheme.typography.bodySmall)
                        if (saved.itinerary.summary.isNotBlank()) Text(saved.itinerary.summary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.mytrip_share), fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.mytrip_share_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = shareId != null, onCheckedChange = { enabled ->
                                scope.launch { runCatching { state.api.shareTrip(saved.id, enabled) }.onSuccess { shareId = it } }
                            })
                        }
                        shareId?.let { code ->
                            OutlinedButton(onClick = {
                                val url = "${BuildConfig.SITE_URL.trimEnd('/')}/share/trip/$code"
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${saved.title}\n$url"), null))
                            }) { Text(stringResource(R.string.mytrip_share_link)) }
                        }
                        saved.published?.takeIf { it.status == "ACTIVE" }?.let { published ->
                            OutlinedButton(onClick = { routes.trip(published.slug) }) { Text(stringResource(R.string.mytrip_view_published)) }
                        }
                    }
                }
                itineraryDays(saved.itinerary, response.activities, suggestions, onBook = { state.book(context, it, "mytrip", "mytrip_day") })
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.mytrip_delete_title)) },
            text = { Text(stringResource(R.string.mytrip_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { runCatching { state.api.deleteTrip(id) }.onSuccess { routes.back() } }
                }) { Text(stringResource(R.string.mytrip_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
