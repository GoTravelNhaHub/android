package com.gotravelnha.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gotravelnha.app.R
import com.gotravelnha.app.booking.DaySuggestions
import com.gotravelnha.app.booking.HotelSearch
import com.gotravelnha.app.booking.Suggestion
import com.gotravelnha.app.data.Itinerary
import com.gotravelnha.app.data.ItineraryItem
import com.gotravelnha.app.data.LiveActivity
import com.gotravelnha.app.ui.theme.Brand

/** What a booking button asks the screen to open */
sealed interface BookingAction {
    data class Offer(val activity: LiveActivity) : BookingAction
    data class Search(val entry: Suggestion.Search) : BookingAction
    data class Hotels(val hotels: HotelSearch) : BookingAction
}

@Composable
fun slotLabel(slot: String) = stringResource(
    when (slot) {
        "afternoon" -> R.string.slot_afternoon
        "evening" -> R.string.slot_evening
        else -> R.string.slot_morning
    }
)

fun LiveActivity.priceLabel(): String? =
    priceText ?: priceFrom?.let { "$currency ${if (it % 1.0 == 0.0) it.toLong().toString() else "%.2f".format(it)}" }

/**
 * Day-by-day plan with the booking suggestions under each day. `suggestions` is null when the
 * trip has no booking buttons (e.g. a plan the user has not saved yet still gets them).
 */
fun LazyListScope.itineraryDays(
    itinerary: Itinerary,
    activities: Map<String, LiveActivity>,
    suggestions: List<DaySuggestions>?,
    onBook: (BookingAction) -> Unit,
    onMapDay: ((Int) -> Unit)? = null,
) {
    itinerary.days.forEachIndexed { index, day ->
        val number = index + 1
        item(key = "day-$number") {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Brand.day(number)), contentAlignment = Alignment.Center) {
                            Text("$number", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(stringResource(R.string.day_number, number), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(day.theme, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (onMapDay != null) {
                            OutlinedButton(onClick = { onMapDay(number) }) { Text(stringResource(R.string.trip_map_short)) }
                        }
                    }
                    day.items.forEach { ItemRow(it, it.activityId?.let(activities::get)) }
                    suggestions?.getOrNull(index)?.let { DayBooking(it, onBook) }
                }
            }
        }
    }
    if (itinerary.tips.isNotEmpty()) {
        item(key = "tips") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.trip_tips), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                itinerary.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ItineraryItem, activity: LiveActivity?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(slotLabel(item.slot).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
        item.familyTip?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.trip_family_tip, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (activity != null && !activity.verified) {
            Text(stringResource(R.string.trip_not_listed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayBooking(day: DaySuggestions, onBook: (BookingAction) -> Unit) {
    if (day.activities.isEmpty() && day.hotels == null) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.booking_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        day.hotels?.let { hotels ->
            BookingRow(
                title = stringResource(R.string.booking_hotels, hotels.city),
                hint = null,
                button = stringResource(R.string.booking_view_on_trip),
                trip = true,
            ) { onBook(BookingAction.Hotels(hotels)) }
        }
        day.activities.forEach { entry ->
            when (entry) {
                is Suggestion.Offer -> BookingRow(
                    title = entry.activity.title,
                    hint = entry.activity.priceLabel()?.let { stringResource(R.string.booking_from_price, it) },
                    button = stringResource(R.string.booking_view),
                    trip = entry.activity.provider == "trip",
                ) { onBook(BookingAction.Offer(entry.activity)) }
                is Suggestion.Search -> {
                    val link = entry.link
                    BookingRow(
                        title = link?.title ?: entry.label,
                        hint = if (link != null) stringResource(R.string.booking_matched_hint) else null,
                        button = stringResource(
                            when {
                                link?.provider == "trip" -> R.string.booking_view_on_trip
                                link != null -> R.string.booking_view_on_klook
                                else -> R.string.booking_search_klook
                            }
                        ),
                        trip = link?.provider == "trip",
                    ) { onBook(BookingAction.Search(entry)) }
                }
            }
        }
        Text(stringResource(R.string.booking_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BookingRow(title: String, hint: String?, button: String, trip: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = if (trip) Brand.Trip else Brand.Klook, contentColor = androidx.compose.ui.graphics.Color.White),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) { Text(button, style = MaterialTheme.typography.labelLarge) }
    }
}
