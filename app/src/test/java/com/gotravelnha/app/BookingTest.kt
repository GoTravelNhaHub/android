package com.gotravelnha.app

import com.gotravelnha.app.booking.Suggestion
import com.gotravelnha.app.booking.daySuggestions
import com.gotravelnha.app.booking.isApprovedPartnerUrl
import com.gotravelnha.app.booking.klookQuery
import com.gotravelnha.app.booking.localizedPartnerUrl
import com.gotravelnha.app.booking.withFallbackAffiliateIds
import com.gotravelnha.app.booking.withMatchedProducts
import com.gotravelnha.app.data.ItineraryDay
import com.gotravelnha.app.data.ItineraryItem
import com.gotravelnha.app.data.MatchedProduct
import com.gotravelnha.app.data.StopMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Same cases as backend/test/partner-link-resolver.test.ts, so the app builds the same keys
class BookingTest {
    @Test
    fun klookQueryKeepsTheBookablePlace() {
        assertEquals("Singapore Botanic Gardens", klookQuery("Jacob Ballas Children's Garden at Singapore Botanic Gardens", "Singapore"))
        assertEquals("Lau Pa Sat Singapore", klookQuery("Lunch at Lau Pa Sat", "Singapore"))
        assertEquals("Gardens by the Bay Singapore", klookQuery("Morning at Gardens by the Bay (Flower Dome)", "Singapore"))
    }

    private val days = listOf(
        ItineraryDay(theme = "Gardens", items = listOf(
            ItineraryItem(slot = "morning", title = "Jacob Ballas Children's Garden at Singapore Botanic Gardens"),
            ItineraryItem(slot = "afternoon", title = "Night Safari"),
            ItineraryItem(slot = "evening", title = "Dinner at the hotel"),
        ))
    )

    @Test
    fun suggestionsSkipMealsAndAddHotelsOnDayOne() {
        val result = daySuggestions(days, emptyMap(), "Singapore")
        assertEquals(2, result[0].activities.size)
        assertTrue(result[0].hotels!!.url.contains("city=73"))
    }

    @Test
    fun matchesReplaceDropOrKeepSearches() {
        val base = daySuggestions(days, emptyMap(), "Singapore")
        val product = MatchedProduct("klook", "https://www.klook.com/activity/123-night-safari/", "Night Safari")
        val result = withMatchedProducts(base, mapOf(
            "Singapore Botanic Gardens" to StopMatch(null, null),
            "Night Safari Singapore" to StopMatch(product, "Night Safari"),
        ))
        val entries = result[0].activities.filterIsInstance<Suggestion.Search>()
        assertEquals(1, entries.size)
        assertEquals(product, entries[0].link)
    }

    @Test
    fun partnerUrls() {
        val link = MatchedProduct("klook", "https://www.klook.com/activity/123-x/", null)
        assertEquals("https://www.klook.com/vi/activity/123-x/", localizedPartnerUrl(link, "vi"))
        assertTrue(isApprovedPartnerUrl("https://www.trip.com/hotels/list?city=73"))
        assertFalse(isApprovedPartnerUrl("http://www.trip.com/"))
        assertFalse(isApprovedPartnerUrl("https://evil-klook.com/"))
        assertEquals("https://www.klook.com/a?x=1&aid=1", withFallbackAffiliateIds("https://www.klook.com/a?x=1", "klook", "1", "2", "3"))
    }
}
