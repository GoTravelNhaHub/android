package com.gotravelnha.app.ui.trip

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.gotravelnha.app.booking.withMatchedProducts
import com.gotravelnha.app.data.Comment
import com.gotravelnha.app.data.StopMatch
import com.gotravelnha.app.data.TripResponse
import com.gotravelnha.app.ui.AppState
import com.gotravelnha.app.ui.LocalSignIn
import com.gotravelnha.app.ui.PlannerSeed
import com.gotravelnha.app.ui.Routes
import com.gotravelnha.app.ui.components.Avatar
import com.gotravelnha.app.ui.components.BackTopBar
import com.gotravelnha.app.ui.components.CoverImage
import com.gotravelnha.app.ui.components.LoadContent
import com.gotravelnha.app.ui.components.Pill
import com.gotravelnha.app.ui.components.daysLabel
import com.gotravelnha.app.ui.components.itineraryDays
import com.gotravelnha.app.ui.components.rememberLoader
import com.gotravelnha.app.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TripDetailScreen(state: AppState, routes: Routes, slug: String, onCustomize: (PlannerSeed) -> Unit) {
    val trip = rememberLoader(slug, state.signedIn) { state.api.communityTrip(slug) }
    val title = (trip.value as? com.gotravelnha.app.ui.components.Load.Ready)?.value?.data?.title.orEmpty()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title, onBack = { routes.back() }, actions = {
            IconButton(onClick = {
                val url = "${BuildConfig.SITE_URL.trimEnd('/')}/community/trips/$slug"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$title\n$url"), null))
            }) { Icon(Icons.Filled.Share, stringResource(R.string.action_share)) }
        })
        LoadContent(trip.value, onRetry = trip::reload) { response -> TripBody(state, routes, slug, response, onCustomize) }
    }
}

@Composable
private fun TripBody(state: AppState, routes: Routes, slug: String, response: TripResponse, onCustomize: (PlannerSeed) -> Unit) {
    val view = response.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signIn = LocalSignIn.current
    val links = remember { mutableStateMapOf<String, StopMatch>() }
    var saved by remember { mutableStateOf(false) }
    var saveCount by remember { mutableStateOf(view.saveCount) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }

    LaunchedEffect(view.id, state.signedIn) {
        if (state.signedIn) saved = runCatching { view.id in state.api.savedState(listOf(view.id)) }.getOrDefault(false)
    }
    LaunchedEffect(view.slug) {
        comments = runCatching { state.api.comments(view.slug).data }.getOrDefault(emptyList())
    }
    // Partner product pages found by AI web search; the first visit may still be looking them up
    LaunchedEffect(view.slug) {
        if (view.visibility == "PRIVATE") return@LaunchedEffect
        repeat(2) { attempt ->
            val result = runCatching { state.api.partnerLinks(view.slug) }.getOrNull() ?: return@LaunchedEffect
            links.putAll(result.data)
            if (!result.pending || attempt == 1) return@LaunchedEffect
            delay(20_000)
        }
    }

    val suggestions = remember(response, links.toMap()) {
        withMatchedProducts(daySuggestions(view.itinerary.days, response.activities, view.destination), links.toMap())
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            CoverImage(view.coverImage, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(view.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(view.destination, view.country?.takeIf { !view.destination.contains(it) }).joinToString(", ") + " · " + daysLabel(view.durationDays),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (view.familyFriendly) Pill(stringResource(R.string.trip_family_friendly), background = MaterialTheme.colorScheme.primaryContainer, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    view.travelStyle?.let { Pill(paceLabel(it)) }
                    view.budgetLevel?.let { Pill(budgetLabel(it)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(view.author.displayName, view.author.avatarUrl, 36)
                    Column(Modifier.weight(1f)) {
                        Text(view.author.displayName, fontWeight = FontWeight.SemiBold)
                        Text(pluralStringResource(R.plurals.trips_count, view.authorStats.publicTrips, view.authorStats.publicTrips), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                (view.description ?: view.itinerary.summary).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (!state.signedIn) return@OutlinedButton signIn(false)
                        scope.launch {
                            val target = !saved
                            saved = target
                            runCatching { state.api.setSaved(view.slug, target) }
                                .onSuccess { saveCount = it.saveCount; saved = it.saved }
                                .onFailure { saved = !target }
                        }
                    }) {
                        Icon(if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (saved) Brand.Heart else MaterialTheme.colorScheme.onSurface)
                        Text(" " + pluralStringResource(R.plurals.saves, saveCount, saveCount))
                    }
                    OutlinedButton(onClick = { routes.tripMap(slug, null) }) {
                        Icon(Icons.Filled.LocationOn, null)
                        Text(" " + stringResource(R.string.trip_view_map))
                    }
                }
                if (!response.viewerIsAuthor) {
                    Button(onClick = {
                        onCustomize(PlannerSeed(destination = view.destination, days = view.durationDays, adaptFromId = view.id, adaptTitle = view.title))
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.trip_customize)) }
                }
            }
        }
        itineraryDays(
            view.itinerary,
            response.activities,
            if (view.visibility == "PRIVATE") null else suggestions,
            onBook = { state.book(context, it, "community/trips/$slug", "community_trip_day") },
            onMapDay = { routes.tripMap(slug, it) },
        )
        if (comments.isNotEmpty()) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(pluralStringResource(R.plurals.comments, view.commentCount, view.commentCount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    comments.forEach { comment ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Avatar(comment.author?.displayName ?: "?", comment.author?.avatarUrl, 30)
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(comment.author?.displayName ?: stringResource(R.string.comment_deleted_author), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Pill(stringResource(if (comment.kind == "QUESTION") R.string.comment_question else R.string.comment_tip))
                                }
                                Text(comment.body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun paceLabel(pace: String) = stringResource(
    when (pace) {
        "relaxed" -> R.string.pace_relaxed
        "packed" -> R.string.pace_packed
        else -> R.string.pace_balanced
    }
)

@Composable
fun budgetLabel(budget: String) = stringResource(
    when (budget) {
        "budget" -> R.string.budget_budget
        "premium" -> R.string.budget_premium
        else -> R.string.budget_mid
    }
)
