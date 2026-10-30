package com.gotravelnha.app.data

import kotlinx.serialization.Serializable

// Shapes of the api.gotravelnha.com responses the app reads (see ../../backend and web/types).
// Unknown fields are ignored, so the API can grow without breaking older app versions.

@Serializable
data class ItineraryItem(
    val slot: String = "morning",
    val activityId: String? = null,
    val title: String = "",
    val description: String = "",
    val familyTip: String? = null,
)

@Serializable
data class ItineraryDay(
    val date: String? = null,
    val theme: String = "",
    val items: List<ItineraryItem> = emptyList(),
)

@Serializable
data class Itinerary(
    val title: String = "",
    val summary: String = "",
    val days: List<ItineraryDay> = emptyList(),
    val tips: List<String> = emptyList(),
)

/** Live supplier data for a catalogue id. Prices never come from the model. */
@Serializable
data class LiveActivity(
    val id: String,
    val kind: String = "offer",
    val title: String = "",
    val image: String? = null,
    val category: String? = null,
    val available: Boolean = false,
    val priceFrom: Double? = null,
    val currency: String = "SGD",
    val priceText: String? = null,
    val href: String? = null,
    val offerId: String? = null,
    val partnerUrl: String? = null,
    val provider: String? = null,
    val verified: Boolean = false,
    val location: String? = null,
)

@Serializable
data class CommunityAuthor(
    val handle: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
)

@Serializable
data class CommunityTripCard(
    val id: String,
    val slug: String,
    val title: String,
    val destination: String,
    val country: String? = null,
    val durationDays: Int = 0,
    val travelStyle: String? = null,
    val budgetLevel: String? = null,
    val familyFriendly: Boolean = false,
    val coverImage: String? = null,
    val highlights: List<String> = emptyList(),
    val description: String? = null,
    val saveCount: Int = 0,
    val commentCount: Int = 0,
    val adaptCount: Int = 0,
    val author: CommunityAuthor = CommunityAuthor(),
)

@Serializable
data class TripPage(
    val data: List<CommunityTripCard> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class CommunityDestination(
    val destination: String,
    val country: String? = null,
    val count: Int = 0,
)

@Serializable
data class AuthorStats(val publicTrips: Int = 0, val followers: Int = 0)

@Serializable
data class PublishedTripView(
    val id: String,
    val slug: String,
    val visibility: String = "PUBLIC",
    val title: String,
    val description: String? = null,
    val destination: String,
    val country: String? = null,
    val durationDays: Int = 0,
    val travelStyle: String? = null,
    val budgetLevel: String? = null,
    val familyFriendly: Boolean = false,
    val childAgeBands: List<String> = emptyList(),
    val itinerary: Itinerary = Itinerary(),
    val coverImage: String? = null,
    val saveCount: Int = 0,
    val commentCount: Int = 0,
    val adaptCount: Int = 0,
    val author: CommunityAuthor = CommunityAuthor(),
    val authorStats: AuthorStats = AuthorStats(),
)

@Serializable
data class TripResponse(
    val data: PublishedTripView,
    val viewerIsAuthor: Boolean = false,
    val activities: Map<String, LiveActivity> = emptyMap(),
)

@Serializable
data class SaveState(val saved: Boolean, val saveCount: Int)

@Serializable
data class MatchedProduct(val provider: String, val url: String, val title: String? = null)

/** What AI web search found for a stop's Klook search query */
@Serializable
data class StopMatch(val product: MatchedProduct? = null, val search: String? = null)

@Serializable
data class PartnerLinksResponse(val data: Map<String, StopMatch> = emptyMap(), val pending: Boolean = false)

@Serializable
data class MapStop(val slot: String, val title: String, val place: String, val lat: Double, val lon: Double)

@Serializable
data class MapDay(val day: Int, val theme: String = "", val stops: List<MapStop> = emptyList())

@Serializable
data class LatLon(val lat: Double, val lon: Double)

@Serializable
data class TripMap(val centre: LatLon? = null, val days: List<MapDay> = emptyList(), val unmapped: Int = 0)

@Serializable
data class CommentAuthor(val handle: String, val displayName: String, val avatarUrl: String? = null)

@Serializable
data class Comment(
    val id: String,
    val body: String,
    val kind: String = "TIP",
    val createdAt: String,
    val author: CommentAuthor? = null,
    val mine: Boolean = false,
)

@Serializable
data class CommentPage(val data: List<Comment> = emptyList(), val nextCursor: String? = null)

@Serializable
data class DealComparisonOption(
    val provider: String,
    val label: String = "",
    val priceNote: String = "",
    val highlight: String = "",
    val baseUrl: String,
)

@Serializable
data class DealTemplate(
    val slug: String,
    val title: String,
    val description: String = "",
    val destination: String = "",
    val heroImage: String = "",
    val badge: String = "",
    val category: String = "activity",
    val primaryProvider: String = "klook",
    val primaryCtaLabel: String = "",
    val primaryBaseUrl: String = "",
    val placementKey: String = "",
    val summaryBullets: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    val comparison: List<DealComparisonOption> = emptyList(),
)

@Serializable
data class PartyMember(val name: String? = null, val age: Int)

@Serializable
data class TripSummary(
    val id: String,
    val title: String,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val party: List<PartyMember> = emptyList(),
)

@Serializable
data class PublishedRef(val id: String, val slug: String, val visibility: String, val status: String)

@Serializable
data class SavedTrip(
    val id: String,
    val title: String,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val party: List<PartyMember> = emptyList(),
    val itinerary: Itinerary = Itinerary(),
    val shareId: String? = null,
    val published: PublishedRef? = null,
)

@Serializable
data class SavedTripResponse(val data: SavedTrip, val activities: Map<String, LiveActivity> = emptyMap())

@Serializable
data class TripRequest(
    val destination: String,
    val startDate: String,
    val endDate: String,
    val party: List<PartyMember>,
    val budget: Double? = null,
    val currency: String = "SGD",
    val pace: String = "balanced",
    val interests: String? = null,
    val language: String = "en",
)

@Serializable
data class Revise(val generationId: String, val change: String)

@Serializable
data class AdaptFrom(val tripId: String)

@Serializable
data class PlannerBody(
    val destination: String,
    val startDate: String,
    val endDate: String,
    val party: List<PartyMember>,
    val budget: Double? = null,
    val currency: String = "SGD",
    val pace: String = "balanced",
    val interests: String? = null,
    val language: String = "en",
    val revise: Revise? = null,
    val adaptFrom: AdaptFrom? = null,
)

@Serializable
data class PlannerResult(
    val generationId: String,
    val sourceTripId: String? = null,
    val itinerary: Itinerary,
    val activities: Map<String, LiveActivity> = emptyMap(),
)

@Serializable
data class ChatMessage(val role: String, val text: String)

@Serializable
data class BriefTraveller(val age: Int)

@Serializable
data class AgentBrief(
    val destination: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val travellers: List<BriefTraveller> = emptyList(),
    val budget: Double? = null,
    val pace: String? = null,
    val interests: String? = null,
)

@Serializable
data class ChatPlan(val title: String, val days: List<String>)

@Serializable
data class ChatBody(
    val messages: List<ChatMessage>,
    val brief: AgentBrief,
    val language: String,
    val plan: ChatPlan? = null,
)

@Serializable
data class AgentTraveller(val age: Int, val label: String? = null)

@Serializable
data class AgentUpdates(
    val destination: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val travellers: List<AgentTraveller>? = null,
    val budget: Double? = null,
    val pace: String? = null,
    val interests: String? = null,
)

@Serializable
data class AgentTurn(
    val reply: String = "",
    val updates: AgentUpdates = AgentUpdates(),
    val action: String = "none",
    val change: String? = null,
    val quickReplies: List<String> = emptyList(),
)

@Serializable
data class IdRef(val id: String)

@Serializable
data class IdResponse(val data: IdRef)

@Serializable
data class ShareResponse(val shareId: String? = null)

@Serializable
data class AuthUser(
    val id: String,
    val username: String = "",
    val email: String = "",
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String = "USER",
) {
    val displayName: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { username.ifBlank { email } }
}

@Serializable
data class MeResponse(val user: AuthUser)

@Serializable
data class ClickResponse(val outboundUrl: String? = null)

@Serializable
data class ListResponse<T>(val data: List<T> = emptyList())

@Serializable
data class DataResponse<T>(val data: T)
