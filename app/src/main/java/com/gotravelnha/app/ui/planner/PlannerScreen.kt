package com.gotravelnha.app.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gotravelnha.app.R
import com.gotravelnha.app.appLanguage
import com.gotravelnha.app.booking.daySuggestions
import com.gotravelnha.app.data.PlannerResult
import com.gotravelnha.app.ui.AppState
import com.gotravelnha.app.ui.LocalSignIn
import com.gotravelnha.app.ui.Routes
import com.gotravelnha.app.ui.components.ChoiceChip
import com.gotravelnha.app.ui.components.itineraryDays

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(state: AppState, routes: Routes) {
    val model: PlannerModel = viewModel()
    val language = appLanguage(LocalContext.current)
    var editing by remember { mutableStateOf(false) }
    var confirmStartOver by remember { mutableStateOf(false) }

    LaunchedEffect(state.plannerSeed) {
        state.plannerSeed?.let { model.applySeed(it); state.plannerSeed = null }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_planner), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (model.started) {
                IconButton(onClick = { confirmStartOver = true }, enabled = !model.busy) { Icon(Icons.Filled.Refresh, stringResource(R.string.planner_start_over)) }
            }
        }
        BriefStrip(model.brief) { editing = true }

        val result = model.result
        if (result != null) {
            PrimaryTabRow(selectedTabIndex = model.view.ordinal) {
                Tab(model.view == PlannerView.Chat, { model.view = PlannerView.Chat }, text = { Text(stringResource(R.string.chat_tab_chat)) })
                Tab(model.view == PlannerView.Plan, { model.view = PlannerView.Plan }, text = { Text(stringResource(R.string.chat_tab_plan)) })
            }
        }
        if (result != null && model.view == PlannerView.Plan) {
            PlanView(state, routes, model, result)
        } else {
            ChatView(model, onSend = { model.send(state, language, it) }, onPlan = { model.plan(state, language) })
        }
    }

    if (editing) PlannerDetailsSheet(model) { editing = false }

    if (confirmStartOver) {
        AlertDialog(
            onDismissRequest = { confirmStartOver = false },
            title = { Text(stringResource(R.string.planner_start_over)) },
            text = { Text(stringResource(R.string.chat_start_over_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStartOver = false
                    model.startOver()
                }) { Text(stringResource(R.string.planner_start_over)) }
            },
            dismissButton = { TextButton(onClick = { confirmStartOver = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** The trip so far. The agent fills it in; tapping any part opens the details sheet. */
@Composable
private fun BriefStrip(brief: PlannerBrief, onEdit: () -> Unit) {
    val start = brief.startDate
    val end = brief.endDate
    val destination = brief.destination.ifBlank { null }
    val dates = if (start != null && end != null) dateRange(start, end) else null
    val travellers = if (brief.party.isNotEmpty()) pluralStringResource(R.plurals.travellers, brief.party.size, brief.party.size) else null
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChoiceChip(destination ?: stringResource(R.string.brief_add_destination), destination != null, onEdit)
        ChoiceChip(dates ?: stringResource(R.string.brief_add_dates), dates != null, onEdit)
        ChoiceChip(travellers ?: stringResource(R.string.brief_add_travellers), travellers != null, onEdit)
        ChoiceChip(brief.budget?.let(::budgetLabel) ?: stringResource(R.string.brief_add_budget), brief.budget != null, onEdit)
        ChoiceChip(stringResource(paceLabel(brief.pace)), false, onEdit)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ChatView(model: PlannerModel, onSend: (String) -> Unit, onPlan: () -> Unit) {
    val list = rememberLazyListState()
    val notes = model.notes
    LaunchedEffect(notes.size, model.busy) {
        val lastIndex = list.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) list.animateScrollToItem(lastIndex)
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(notes, key = { it.id }) { note ->
            Bubble(
                note,
                showReplies = note.id == notes.lastOrNull()?.id && !model.busy,
                onReply = onSend,
                onViewPlan = { model.view = PlannerView.Plan },
            )
        }
        if (model.busy) item(key = "busy") { BusyBubble(model.generating) }
    }
    if (model.result == null && model.brief.missing.isEmpty() && !model.busy) {
        Button(onClick = onPlan, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(stringResource(R.string.planner_generate)) }
    }
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            model.draft, { model.draft = it.take(800) }, Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.chat_placeholder)) }, maxLines = 4, shape = RoundedCornerShape(24.dp),
        )
        FilledIconButton(onClick = { onSend(model.draft) }, enabled = model.draft.isNotBlank() && !model.busy, modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.chat_send))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bubble(note: ChatNote, showReplies: Boolean, onReply: (String) -> Unit, onViewPlan: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (note.fromUser) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (note.text.isNotBlank()) {
            Surface(
                color = if (note.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (note.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.widthIn(max = 310.dp),
            ) { Text(note.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
        }
        note.changes.forEach { change ->
            Text("✓ $change", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (note.showsPlan) OutlinedButton(onClick = onViewPlan) { Text(stringResource(R.string.chat_view_plan)) }
        if (showReplies && note.replies.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                note.replies.forEach { reply -> ChoiceChip(reply, false, { onReply(reply) }) }
            }
        }
    }
}

@Composable
private fun BusyBubble(generating: Boolean) {
    Row(
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(16.dp)) { CircularProgressIndicator(strokeWidth = 2.dp) }
        Text(stringResource(if (generating) R.string.planner_working_hint else R.string.chat_thinking))
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PlanView(state: AppState, routes: Routes, model: PlannerModel, result: PlannerResult) {
    val context = LocalContext.current
    val signIn = LocalSignIn.current
    val itinerary = result.itinerary
    val suggestions = remember(result) { daySuggestions(itinerary.days, result.activities, model.brief.destination) }

    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(itinerary.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(itinerary.summary)
                Text(stringResource(R.string.planner_ai_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val savedId = model.savedTripId
                if (savedId != null) {
                    Button(onClick = { routes.myTrip(savedId) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.planner_open_saved)) }
                } else {
                    Button(
                        onClick = { if (state.signedIn) model.save(state) { routes.myTrip(it) } else signIn(false) },
                        enabled = !model.working && !model.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(if (state.signedIn) R.string.planner_save else R.string.planner_sign_in_to_save)) }
                }
                OutlinedButton(onClick = { model.view = PlannerView.Chat }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.chat_change_plan)) }
            }
        }
        itineraryDays(itinerary, result.activities, suggestions, onBook = { state.book(context, it, "planner", "planner_day") })
    }
}
