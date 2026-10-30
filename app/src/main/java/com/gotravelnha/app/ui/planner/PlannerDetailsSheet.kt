package com.gotravelnha.app.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gotravelnha.app.R
import com.gotravelnha.app.booking.SEARCH_CITIES
import com.gotravelnha.app.ui.components.ChoiceChip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Every brief field by hand, for people who would rather tap than type. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlannerDetailsSheet(model: PlannerModel, onDismiss: () -> Unit) {
    var pickDates by remember { mutableStateOf(false) }
    var childAge by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf(model.brief.budget?.toLong()?.toString().orEmpty()) }
    val brief = model.brief
    val start = brief.startDate
    val end = brief.endDate

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(stringResource(R.string.chat_edit_details), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
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
                OutlinedTextField(brief.destination, { model.brief = model.brief.copy(destination = it.take(80)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.planner_destination)) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SEARCH_CITIES.take(8).forEach { city -> ChoiceChip(city.name, brief.destination == city.name, { model.brief = model.brief.copy(destination = city.name) }) }
                }
            }
            item {
                OutlinedButton(onClick = { pickDates = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (start != null && end != null) dateRange(start, end) else stringResource(R.string.brief_add_dates))
                }
            }
            item {
                Text(stringResource(R.string.planner_travellers), fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.planner_adults), Modifier.weight(1f))
                    OutlinedButton(onClick = { if (brief.adults > 0) model.brief = brief.withAdults(brief.adults - 1) }) { Text("−") }
                    Text("${brief.adults}", Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { if (brief.party.size < 10) model.brief = brief.withAdults(brief.adults + 1) }) { Text("+") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    brief.children.forEachIndexed { index, child ->
                        InputChip(selected = false, onClick = { model.brief = brief.withoutChild(index) }, label = { Text(stringResource(R.string.planner_child_age, child.age)) }, trailingIcon = { Icon(Icons.Filled.Close, null) })
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
                        onClick = { childAge.toIntOrNull()?.takeIf { it in 0..17 }?.let { model.brief = brief.withChild(it); childAge = "" } },
                        enabled = childAge.toIntOrNull()?.let { it in 0..17 } == true && brief.party.size < 10,
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
                    listOf("relaxed", "balanced", "packed").forEach { key ->
                        ChoiceChip(stringResource(paceLabel(key)), brief.pace == key, { model.brief = brief.copy(pace = key) })
                    }
                }
            }
            item {
                OutlinedTextField(
                    budget,
                    {
                        budget = it.filter(Char::isDigit).take(7)
                        model.brief = model.brief.copy(budget = budget.toDoubleOrNull()?.takeIf { value -> value > 0 })
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.planner_budget)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            item {
                OutlinedTextField(
                    brief.interests, { model.brief = model.brief.copy(interests = it.take(500)) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.planner_interests)) }, minLines = 2,
                )
            }
            item { Button(onClick = onDismiss, Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_ok)) } }
        }
    }

    if (pickDates) {
        val today = LocalDate.now()
        val picker = rememberDateRangePickerState(
            initialSelectedStartDateMillis = start?.let(::utcMillis),
            initialSelectedEndDateMillis = end?.let(::utcMillis),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
            },
        )
        val from = picker.selectedStartDateMillis?.let(::utcDate)
        val to = (picker.selectedEndDateMillis ?: picker.selectedStartDateMillis)?.let(::utcDate)
        val tooLong = from != null && to != null && to.toEpochDay() - from.toEpochDay() + 1 > MAX_TRIP_DAYS
        DatePickerDialog(
            onDismissRequest = { pickDates = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (from != null && to != null) model.brief = model.brief.copy(startDate = from.toString(), endDate = to.toString())
                        pickDates = false
                    },
                    enabled = !tooLong,
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDates = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            if (tooLong) Text(stringResource(R.string.planner_too_long, MAX_TRIP_DAYS), Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            DateRangePicker(picker, Modifier.weight(1f))
        }
    }
}

private fun utcMillis(date: String) = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

private fun utcDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
