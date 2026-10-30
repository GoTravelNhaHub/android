package com.gotravelnha.app.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gotravelnha.app.R
import com.gotravelnha.app.data.CommunityDestination
import com.gotravelnha.app.data.DealTemplate
import com.gotravelnha.app.ui.AppState
import com.gotravelnha.app.ui.Routes
import com.gotravelnha.app.ui.components.CoverImage
import com.gotravelnha.app.ui.components.Load
import com.gotravelnha.app.ui.components.LoadingState
import com.gotravelnha.app.ui.components.Pill
import com.gotravelnha.app.ui.components.SectionTitle
import com.gotravelnha.app.ui.components.TripCardView
import com.gotravelnha.app.ui.components.rememberLoader
import com.gotravelnha.app.ui.theme.Brand

@Composable
fun ExploreScreen(state: AppState, routes: Routes, onCommunityDestination: (String) -> Unit, onPlan: () -> Unit) {
    val trending = rememberLoader { state.api.communityTrips(mapOf("sort" to "trending"), null, limit = 6).data }
    val destinations = rememberLoader { state.api.communityDestinations() }
    val deals = rememberLoader { state.api.deals() }
    var openDeal by remember { mutableStateOf<DealTemplate?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Hero(onPlan) }

        (destinations.value as? Load.Ready)?.value?.takeIf { it.isNotEmpty() }?.let { list ->
            item { SectionTitle(stringResource(R.string.explore_destinations), Modifier.padding(16.dp, 20.dp, 16.dp, 10.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.destination }) { DestinationTile(it) { onCommunityDestination(it.destination) } }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.explore_trending), Modifier.padding(16.dp, 22.dp, 16.dp, 10.dp), subtitle = stringResource(R.string.explore_trending_subtitle)) }
        when (val load = trending.value) {
            Load.Loading -> item { LoadingState() }
            is Load.Failed -> item { Text(stringResource(R.string.error_generic), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            is Load.Ready -> items(load.value, key = { it.id }) { trip ->
                TripCardView(trip, saved = null, onClick = { routes.trip(trip.slug) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }

        (deals.value as? Load.Ready)?.value?.takeIf { it.isNotEmpty() }?.let { list ->
            item { SectionTitle(stringResource(R.string.explore_deals), Modifier.padding(16.dp, 22.dp, 16.dp, 10.dp), subtitle = stringResource(R.string.explore_deals_subtitle)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list, key = { it.slug }) { DealCard(it) { openDeal = it } }
                }
            }
        }
    }

    openDeal?.let { deal -> DealSheet(state, deal) { openDeal = null } }
}

@Composable
private fun Hero(onPlan: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(22.dp)).background(Brand.Primary).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.app_name), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.explore_hero_title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.explore_hero_text), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Button(onClick = onPlan, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.Active)) {
            Text(stringResource(R.string.explore_hero_cta), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DestinationTile(destination: CommunityDestination, onClick: () -> Unit) {
    Surface(
        Modifier.width(150.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(destination.destination, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.trips_count, destination.count, destination.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DealCard(deal: DealTemplate, onClick: () -> Unit) {
    Surface(
        Modifier.width(260.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            Box {
                CoverImage(deal.heroImage.ifBlank { null }, Modifier.fillMaxWidth().height(130.dp))
                if (deal.badge.isNotBlank()) Pill(deal.badge, Modifier.padding(10.dp), background = Color.White, color = Brand.Ink)
            }
            Column(Modifier.padding(12.dp)) {
                Text(deal.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(deal.destination, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DealSheet(state: AppState, deal: DealTemplate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(deal.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (deal.description.isNotBlank()) Text(deal.description, style = MaterialTheme.typography.bodyMedium)
            deal.summaryBullets.forEach { Text("✓ $it", style = MaterialTheme.typography.bodyMedium) }
            if (deal.primaryBaseUrl.isNotBlank()) {
                Button(
                    onClick = { state.openPartner(context, deal.primaryProvider, deal.primaryBaseUrl, deal.placementKey.ifBlank { "app_deal" }, "explore/${deal.slug}") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (deal.primaryProvider == "trip") Brand.Trip else Brand.Klook),
                ) { Text(deal.primaryCtaLabel.ifBlank { stringResource(R.string.booking_view) }) }
            }
            deal.comparison.filter { it.baseUrl != deal.primaryBaseUrl }.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(option.label, fontWeight = FontWeight.Medium)
                        if (option.highlight.isNotBlank()) Text(option.highlight, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { state.openPartner(context, option.provider, option.baseUrl, deal.placementKey.ifBlank { "app_deal" }, "explore/${deal.slug}") }) {
                        Text(stringResource(if (option.provider == "trip") R.string.booking_view_on_trip else R.string.booking_view_on_klook))
                    }
                }
            }
            if (deal.tips.isNotEmpty()) {
                Text(stringResource(R.string.trip_tips), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                deal.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            Text(stringResource(R.string.booking_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
