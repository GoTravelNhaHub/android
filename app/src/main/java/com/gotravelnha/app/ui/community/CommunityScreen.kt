package com.gotravelnha.app.ui.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gotravelnha.app.R
import com.gotravelnha.app.data.CommunityTripCard
import com.gotravelnha.app.ui.AppState
import com.gotravelnha.app.ui.LocalSignIn
import com.gotravelnha.app.ui.Routes
import com.gotravelnha.app.ui.components.ChoiceChip
import com.gotravelnha.app.ui.components.EmptyState
import com.gotravelnha.app.ui.components.ErrorState
import com.gotravelnha.app.ui.components.LoadingState
import com.gotravelnha.app.ui.components.TripCardView
import com.gotravelnha.app.ui.components.userMessage
import kotlinx.coroutines.launch

private data class Filters(
    val q: String? = null,
    val destination: String? = null,
    val sort: String = "recent",
    val duration: String? = null,
    val family: Boolean = false,
) {
    fun query() = mapOf(
        "q" to q,
        "destination" to destination,
        "sort" to sort.takeIf { it != "recent" },
        "duration" to duration,
        "family" to if (family) "1" else null,
    )
}

/** Shared toggle for the save heart on trip cards: optimistic, signs in first when needed */
@Composable
fun rememberSaveToggle(state: AppState, saved: MutableSet<String>): (CommunityTripCard) -> Unit {
    val scope = rememberCoroutineScope()
    val signIn = LocalSignIn.current
    return { trip ->
        if (!state.signedIn) signIn(false)
        else scope.launch {
            val now = trip.id !in saved
            if (now) saved += trip.id else saved -= trip.id
            runCatching { state.api.setSaved(trip.slug, now) }.onFailure { if (now) saved -= trip.id else saved += trip.id }
        }
    }
}

@Composable
fun CommunityScreen(state: AppState, routes: Routes) {
    var filters by remember { mutableStateOf(Filters(destination = state.communityDestination)) }
    var search by rememberSaveable { mutableStateOf(state.communityDestination ?: "") }
    val trips = remember { mutableStateListOf<CommunityTripCard>() }
    val saved = remember { mutableStateListOf<String>().let { list -> SnapshotSet(list) } }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val toggleSave = rememberSaveToggle(state, saved)

    // A destination tapped on Explore narrows the list once
    LaunchedEffect(state.communityDestination) {
        state.communityDestination?.let {
            filters = Filters(destination = it)
            search = it
            state.communityDestination = null
        }
    }

    suspend fun loadPage(reset: Boolean) {
        loading = true
        failed = false
        runCatching { state.api.communityTrips(filters.query(), if (reset) null else cursor) }
            .onSuccess { page ->
                if (reset) trips.clear()
                trips += page.data
                cursor = page.nextCursor
                if (state.signedIn) runCatching { state.api.savedState(page.data.map { it.id }) }.onSuccess { saved.addAll(it) }
            }
            .onFailure { error = it.userMessage(); failed = true }
        loading = false
    }

    LaunchedEffect(filters, attempt, state.signedIn) { loadPage(reset = true) }

    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 3 } }
    LaunchedEffect(nearEnd, cursor) { if (nearEnd && cursor != null && !loading && !failed) loadPage(reset = false) }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(stringResource(R.string.community_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
            Text(stringResource(R.string.community_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp),
                placeholder = { Text(stringResource(R.string.community_search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) IconButton(onClick = { search = ""; filters = filters.copy(q = null, destination = null) }) {
                        Icon(Icons.Filled.Clear, stringResource(R.string.action_clear))
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    filters = filters.copy(q = search.trim().ifBlank { null }, destination = null)
                }),
            )
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { ChoiceChip(stringResource(R.string.community_sort_recent), filters.sort == "recent", { filters = filters.copy(sort = "recent") }) }
                item { ChoiceChip(stringResource(R.string.community_sort_trending), filters.sort == "trending", { filters = filters.copy(sort = "trending") }) }
                item { ChoiceChip(stringResource(R.string.community_sort_saved), filters.sort == "saved", { filters = filters.copy(sort = "saved") }) }
                item { ChoiceChip(stringResource(R.string.trip_family_friendly), filters.family, { filters = filters.copy(family = !filters.family) }) }
                listOf("short" to R.string.community_duration_short, "medium" to R.string.community_duration_medium, "long" to R.string.community_duration_long).forEach { (key, label) ->
                    item { ChoiceChip(stringResource(label), filters.duration == key, { filters = filters.copy(duration = if (filters.duration == key) null else key) }) }
                }
            }
        }
        items(trips, key = { it.id }) { trip ->
            TripCardView(
                trip,
                saved = trip.id in saved,
                onClick = { routes.trip(trip.slug) },
                onToggleSave = { toggleSave(trip) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        when {
            loading -> item { LoadingState() }
            failed && trips.isEmpty() -> item { ErrorState(error, { attempt++ }) }
            failed -> item { OutlinedButton(onClick = { scope.launch { loadPage(reset = false) } }, modifier = Modifier.padding(16.dp)) { Text(stringResource(R.string.action_retry)) } }
            trips.isEmpty() -> item {
                EmptyState(stringResource(R.string.community_empty_title), stringResource(R.string.community_empty_text))
            }
        }
    }
}

/** A MutableSet view over a snapshot list, so Compose sees changes to the saved ids */
class SnapshotSet(private val list: MutableList<String>) : AbstractMutableSet<String>() {
    override val size get() = list.size
    override fun iterator() = list.iterator()
    override fun add(element: String) = if (element in list) false else list.add(element)
    override fun contains(element: String) = list.contains(element)
}
