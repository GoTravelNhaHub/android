package com.gotravelnha.app.booking

import com.gotravelnha.app.data.ItineraryDay
import com.gotravelnha.app.data.LiveActivity
import com.gotravelnha.app.data.MatchedProduct
import com.gotravelnha.app.data.StopMatch
import java.net.URLEncoder

// Per-day booking suggestions on a trip, ported from web/utils/community-booking.ts. Keep the
// two in step: the partner-links API keys its answers by the exact query klookQuery() builds.
// Honesty rules as on the site: a price or a specific product appears only for a verified
// catalogue activity or a product page found by search; everything else is a partner search.

data class SearchCity(val name: String, val hotelCityId: Int)

/** Trip.com hotel city ids (web/utils/trip-search.ts) */
val SEARCH_CITIES = listOf(
    SearchCity("Singapore", 73), SearchCity("Bangkok", 359), SearchCity("Kuala Lumpur", 315),
    SearchCity("Hong Kong", 58), SearchCity("Bali", 723), SearchCity("Phuket", 725),
    SearchCity("Ho Chi Minh City", 301), SearchCity("Hanoi", 286), SearchCity("Taipei", 617),
    SearchCity("Seoul", 274), SearchCity("Tokyo", 228), SearchCity("Osaka", 219), SearchCity("Sydney", 501),
)

sealed interface Suggestion {
    data class Offer(val activity: LiveActivity) : Suggestion
    /** `link`: a product page found for the stop; `search`: better Klook search wording */
    data class Search(val query: String, val label: String, val link: MatchedProduct? = null, val search: String? = null) : Suggestion
}

data class DaySuggestions(val day: Int, val hotels: HotelSearch?, val activities: List<Suggestion>)

data class HotelSearch(val city: String, val url: String)

private const val MAX_ACTIVITIES_PER_DAY = 3
private const val MAX_QUERY_WORDS = 6

// Stops that are not things to book: meals, rest, hotel, transfers, free time
private val NOT_BOOKABLE = Regex(
    "\\b(breakfast|brunch|lunch|dinner|supper|meal|snack|coffee|caf[eé]|hawker|food court|rest|nap|relax|hotel|check[- ]?in|check[- ]?out|airport|arriv|depart|transfer|flight|free time|downtime|stroll|walk around|pack)\\b",
    RegexOption.IGNORE_CASE,
)

// Words that describe the visit rather than name the place
private val LEADING_WORDS = Regex(
    "^(?:(?:morning|afternoon|evening|half[- ]day|full[- ]day|day trip|explore|visit|discover|tour|see|enjoy|experience|stroll|relax)\\b[\\s:,-]*(?:at|in|around|to|the)?\\s+)+",
    RegexOption.IGNORE_CASE,
)

private fun cityOf(destination: String): SearchCity? {
    val needle = destination.trim().lowercase()
    return SEARCH_CITIES.firstOrNull { needle.contains(it.name.lowercase()) }
}

fun encodeQuery(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

fun klookSearchUrl(localePath: String, query: String) =
    "https://www.klook.com/$localePath/search/result/?query=${encodeQuery(query)}"

fun tripHotelUrl(cityId: Int) =
    "https://www.trip.com/hotels/list?city=$cityId&adult=2&children=0&crn=1&locale=en-SG&curr=SGD"

/**
 * Klook search wording for a stop. Klook's search matches short names, so keep the place after
 * the last "at", drop notes in brackets and visit words, then add the city when missing.
 */
fun klookQuery(title: String, destination: String): String {
    var clean = title.replace(Regex("\\([^)]*\\)"), " ").replace(Regex("\\s+"), " ").trim()
    clean = clean.split(Regex("\\s+[–—-]\\s+"))[0]
    if (clean.contains(':')) clean = clean.substring(clean.lastIndexOf(':') + 1)
    Regex("^.*\\s(?:at|@)\\s+(.+)$", RegexOption.IGNORE_CASE).find(clean)?.let { clean = it.groupValues[1] }
    clean = clean.replace(LEADING_WORDS, "").trim().ifEmpty { title.trim() }
    clean = clean.split(" ").take(MAX_QUERY_WORDS).joinToString(" ").take(80)
    return if (clean.lowercase().contains(destination.lowercase())) clean else "$clean $destination"
}

fun daySuggestions(days: List<ItineraryDay>, activities: Map<String, LiveActivity>, destination: String): List<DaySuggestions> {
    val city = cityOf(destination)
    val seenQueries = mutableSetOf<String>()
    val seenOffers = mutableSetOf<String>()

    return days.mapIndexed { index, day ->
        val offers = mutableListOf<Suggestion>()
        val searches = mutableListOf<Suggestion>()
        for (item in day.items) {
            val activity = item.activityId?.let { activities[it] }
            if (activity != null && activity.verified && (activity.offerId != null || activity.href != null) && activity.id !in seenOffers) {
                seenOffers += activity.id
                offers += Suggestion.Offer(activity)
                continue
            }
            if (NOT_BOOKABLE.containsMatchIn(item.title)) continue
            val query = klookQuery(item.title, destination)
            if (!seenQueries.add(query.lowercase())) continue
            searches += Suggestion.Search(query, item.title)
        }
        DaySuggestions(
            day = index + 1,
            hotels = if (index == 0 && city != null) HotelSearch(city.name, tripHotelUrl(city.hotelCityId)) else null,
            activities = (offers + searches).take(MAX_ACTIVITIES_PER_DAY),
        )
    }
}

/**
 * Apply what AI web search found: a product page replaces the search, a better keyword replaces
 * the search text, and a stop Klook has nothing for loses its button. Stops not looked up yet keep their search.
 */
fun withMatchedProducts(days: List<DaySuggestions>, links: Map<String, StopMatch>): List<DaySuggestions> =
    days.map { day ->
        day.copy(activities = day.activities.flatMap { entry ->
            val match = (entry as? Suggestion.Search)?.let { links[it.query] }
            when {
                entry !is Suggestion.Search || match == null -> listOf(entry)
                match.product != null -> listOf(entry.copy(link = match.product))
                match.search != null -> listOf(entry.copy(search = match.search))
                else -> emptyList()
            }
        })
    }

/** Klook product pages are stored without a locale; open them in the user's language */
fun localizedPartnerUrl(link: MatchedProduct, klookLocalePath: String): String =
    if (link.provider == "klook") link.url.replaceFirst("https://www.klook.com/", "https://www.klook.com/$klookLocalePath/") else link.url

/** App language → Klook locale path, as on the site */
fun klookLocalePath(language: String) = when (language) {
    "vi" -> "vi"
    "zh" -> "zh-CN"
    else -> "en-SG"
}

private val APPROVED_PARTNER_HOSTS = listOf("trip.com", "klook.com", "agoda.com", "booking.com")

fun isApprovedPartnerUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    val host = uri.host?.lowercase() ?: return false
    uri.scheme == "https" && APPROVED_PARTNER_HOSTS.any { host == it || host.endsWith(".$it") }
}.getOrDefault(false)

/** Affiliate ids added on the device only when /api/affiliate/click could not be reached */
fun withFallbackAffiliateIds(url: String, provider: String, klookAid: String, tripAllianceId: String, tripSid: String): String {
    val separator = if (url.contains('?')) "&" else "?"
    return when (provider) {
        "klook" -> if (url.contains("aid=")) url else "$url${separator}aid=$klookAid"
        "trip" -> if (url.contains("Allianceid=")) url else "$url${separator}Allianceid=$tripAllianceId&SID=$tripSid"
        else -> url
    }
}
