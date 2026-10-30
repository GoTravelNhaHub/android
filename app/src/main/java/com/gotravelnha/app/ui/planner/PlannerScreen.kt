package com.gotravelnha.app.ui.planner

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gotravelnha.app.R
import com.gotravelnha.app.appLanguage
import com.gotravelnha.app.booking.SEARCH_CITIES
import com.gotravelnha.app.booking.daySuggestions
import com.gotravelnha.app.data.AdaptFrom
import com.gotravelnha.app.data.PartyMember
import com.gotravelnha.app.data.PlannerBody
import com.gotravelnha.app.data.PlannerResult
import com.gotravelnha.app.data.Revise
import com.gotravelnha.app.ui.AppState
import com.gotravelnha.app.ui.LocalSignIn
import com.gotravelnha.app.ui.PlannerSeed
import com.gotravelnha.app.ui.Routes
import com.gotravelnha.app.ui.components.ChoiceChip
import com.gotravelnha.app.ui.components.itineraryDays
import com.gotravelnha.app.ui.components.userMessage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

const val MAX_TRIP_DAYS = 14

class PlannerModel(application: Application) : AndroidViewModel(application) {
    var destination by mutableStateOf("")
    var start by mutableStateOf(LocalDate.now().plusDays(14))
    var end by mutableStateOf(LocalDate.now().plusDays(17))
    var adults by mutableStateOf(2)
    val childAges = mutableStateListOf(6)
    var pace by mutableStateOf("balanced")
    var budget by mutableStateOf("")
    var interests by mutableStateOf("")
    var adapt by mutableStateOf<PlannerSeed?>(null)

    var working by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var result by mutableStateOf<PlannerResult?>(null)
    var savedTripId by mutableStateOf<String?>(null)

    fun applySeed(seed: PlannerSeed) {
        destination = seed.destination
        seed.days?.let { end = start.plusDays((it.coerceIn(1, MAX_TRIP_DAYS) - 1).toLong()) }
        adapt = seed.takeIf { it.adaptFromId != null }
        result = null
        savedTripId = null
        error = null
    }

    private fun body(language: String, revise: Revise? = null) = PlannerBody(
        destination = destination.trim(),
        startDate = start.toString(),
        endDate = end.toString(),
        party = List(adults) { PartyMember(age = 35) } + childAges.map { PartyMember(age = it) },
        budget = budget.toDoubleOrNull()?.takeIf { it > 0 },
        pace = pace,
        interests = interests.trim().ifBlank { null },
        language = language,
        revise = revise,
        adaptFrom = if (revise == null) adapt?.adaptFromId?.let { AdaptFrom(it) } else null,
    )

    fun generate(app: AppState, language: String, change: String? = null) {
        val previous = result
        working = true
        error = null
        viewModelScope.launch {
            runCatching {
                app.api.plan(body(language, if (change != null && previous != null) Revise(previous.generationId, change) else null))
            }.onSuccess { result = it; savedTripId = null }
                .onFailure { error = it.userMessage() ?: getApplication<Application>().getString(R.string.planner_failed) }
            working = false
        }
    }

    fun save(app: AppState, onSaved: (String) -> Unit) {
        val generation = result ?: return
        working = true
        viewModelScope.launch {
            runCatching { app.api.saveGeneration(generation.generationId) }
                .onSuccess { savedTripId = it; onSaved(it) }
                .onFailure { error = it.userMessage() ?: getApplication<Application>().getString(R.string.error_generic) }
            working = false
        }
    }
}

@Composable
fun PlannerScreen(state: AppState, routes: Routes) {
    val model: PlannerModel = viewModel()
    val language = appLanguage(LocalContext.current)

    LaunchedEffect(state.plannerSeed) {
        state.plannerSeed?.let { model.applySeed(it); state.plannerSeed = null }
    }

    val result = model.result
    if (result == null) PlannerForm(model) { model.generate(state, language) }
    else PlannerResultView(state, routes, model, result, language)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlannerForm(model: PlannerModel, onGenerate: () -> Unit) {
    var pickDates by remember { mutableStateOf(false) }
    var childAge by remember { mutableStateOf("") }
    val days = (model.end.toEpochDay() - model.start.toEpochDay() + 1).toInt()
    val valid = model.destination.trim().length >= 2 && days in 1..MAX_TRIP_DAYS && !model.start.isBefore(LocalDate.now())

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(stringResource(R.string.planner_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.planner_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        model.adapt?.let { seed ->
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.planner_adapting, seed.adaptTitle.orEmpty()), Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        IconButton(onClick = { model.adapt = null }) { Icon(Icons.Filled.Close, stringResource(R.string.action_clear)) }
                    }
                }
            }
        }
        item {
            OutlinedTextField(model.destination, { model.destination = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.planner_destination)) }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SEARCH_CITIES.take(8).forEach { city -> ChoiceChip(city.name, model.destination == city.name, { model.destination = city.name }) }
            }
        }
        item {
            val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            OutlinedButton(onClick = { pickDates = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.planner_dates, model.start.format(format), model.end.format(format), days))
            }
            if (days > MAX_TRIP_DAYS) Text(stringResource(R.string.planner_too_long, MAX_TRIP_DAYS), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text(stringResource(R.string.planner_travellers), fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.planner_adults), Modifier.weight(1f))
                OutlinedButton(onClick = { if (model.adults > 1) model.adults-- }) { Text("−") }
                Text("${model.adults}", Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { if (model.adults + model.childAges.size < 10) model.adults++ }) { Text("+") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                model.childAges.forEachIndexed { index, age ->
                    InputChip(selected = false, onClick = { model.childAges.removeAt(index) }, label = { Text(stringResource(R.string.planner_child_age, age)) }, trailingIcon = { Icon(Icons.Filled.Close, null) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    childAge, { childAge = it.filter(Char::isDigit).take(2) }, Modifier.width(140.dp),
                    label = { Text(stringResource(R.string.planner_child_age_label)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { childAge.toIntOrNull()?.takeIf { it in 0..17 }?.let { model.childAges += it; childAge = "" } },
                    enabled = childAge.toIntOrNull()?.let { it in 0..17 } == true && model.adults + model.childAges.size < 10,
                ) {
                    Icon(Icons.Filled.Add, null)
                    Text(stringResource(R.string.planner_add_child))
                }
            }
        }
        item {
            Text(stringResource(R.string.planner_pace), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("relaxed" to R.string.pace_relaxed, "balanced" to R.string.pace_balanced, "packed" to R.string.pace_packed).forEach { (key, label) ->
                    ChoiceChip(stringResource(label), model.pace == key, { model.pace = key })
                }
            }
        }
        item {
            OutlinedTextField(
                model.budget, { model.budget = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.planner_budget)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        item {
            OutlinedTextField(
                model.interests, { model.interests = it.take(500) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.planner_interests)) }, minLines = 2,
            )
        }
        model.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(onClick = onGenerate, enabled = valid && !model.working, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (model.working) {
                    CircularProgressIndicator(Modifier.padding(end = 10.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Text(stringResource(R.string.planner_working))
                } else Text(stringResource(R.string.planner_generate))
            }
            if (model.working) Text(stringResource(R.string.planner_working_hint), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (pickDates) {
        val today = LocalDate.now()
        val picker = rememberDateRangePickerState(
            initialSelectedStartDateMillis = model.start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            initialSelectedEndDateMillis = model.end.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDates = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = picker.selectedStartDateMillis
                    val end = picker.selectedEndDateMillis ?: start
                    if (start != null && end != null) {
                        model.start = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate()
                        model.end = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickDates = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDates = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DateRangePicker(picker, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlannerResultView(state: AppState, routes: Routes, model: PlannerModel, result: PlannerResult, language: String) {
    val context = LocalContext.current
    val signIn = LocalSignIn.current
    var change by remember { mutableStateOf("") }
    val itinerary = result.itinerary
    val suggestions = remember(result) { daySuggestions(itinerary.days, result.activities, model.destination) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
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
                        enabled = !model.working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(if (state.signedIn) R.string.planner_save else R.string.planner_sign_in_to_save)) }
                }
                OutlinedTextField(change, { change = it.take(300) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.planner_change_label)) }, enabled = !model.working)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { model.generate(state, language, change.trim()); change = "" }, enabled = change.trim().length >= 2 && !model.working) {
                        Text(stringResource(if (model.working) R.string.planner_working else R.string.planner_change))
                    }
                    TextButton(onClick = { model.result = null }, enabled = !model.working) { Text(stringResource(R.string.planner_start_over)) }
                }
            }
        }
        itineraryDays(itinerary, result.activities, suggestions, onBook = { state.book(context, it, "planner", "planner_day") })
    }
}
