package com.gotravelnha.app.data

import com.gotravelnha.app.BuildConfig
import com.gotravelnha.app.auth.AuthManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** An API error: `message` is the backend's user-facing text when it sent one. */
class ApiException(val status: Int, override val message: String) : Exception(message)

/**
 * api.gotravelnha.com client. Paths are the same `/api/...` the website calls. Signed-in requests
 * carry the Keycloak access token; a 401 refreshes it once and retries.
 */
class ApiClient(private val auth: AuthManager) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val base = BuildConfig.API_BASE_URL.trimEnd('/')
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // The AI planner answers in up to two minutes
        .readTimeout(150, TimeUnit.SECONDS)
        .callTimeout(160, TimeUnit.SECONDS)
        .build()

    suspend inline fun <reified T> get(path: String, query: Map<String, String?> = emptyMap()): T =
        request("GET", path, query, null, serializer<T>())

    suspend inline fun <reified B, reified T> send(method: String, path: String, body: B): T =
        request(method, path, emptyMap(), json.encodeToString(serializer<B>(), body), serializer<T>())

    suspend inline fun <reified T> send(method: String, path: String): T =
        request(method, path, emptyMap(), null, serializer<T>())

    suspend fun <T> request(method: String, path: String, query: Map<String, String?>, body: String?, deserializer: KSerializer<T>): T {
        val url = "$base$path".toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> if (!value.isNullOrBlank()) addQueryParameter(key, value) }
        }.build()

        suspend fun attempt(forceRefresh: Boolean): Response {
            val token = auth.accessToken(forceRefresh)
            val request = Request.Builder().url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "GoTravelNha-Android/${BuildConfig.VERSION_NAME}")
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .method(method, body?.toRequestBody(JSON) ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(JSON))
                .build()
            return http.newCall(request).await()
        }

        var response = attempt(false)
        if (response.code == 401 && auth.signedIn.value) {
            response.close()
            response = attempt(true)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw ApiException(it.code, errorMessage(text) ?: "HTTP ${it.code}")
            return json.decodeFromString(deserializer, text.ifBlank { "{}" })
        }
    }

    private fun errorMessage(text: String): String? = runCatching {
        json.parseToJsonElement(text).let { it as JsonObject }["message"]?.jsonPrimitive?.content
    }.getOrNull()

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

@Serializable
data class Ok(val success: Boolean = true)

@Serializable
data class ClickBody(
    val provider: String? = null,
    val baseUrl: String? = null,
    val offerId: String? = null,
    val placementKey: String,
    val pagePath: String,
    val sessionId: String,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class ShareBody(val enabled: Boolean)

@Serializable
data class SaveTripBody(val generationId: String)

/** Every endpoint the app uses. */
class GoTravelNhaApi(val client: ApiClient) {
    suspend fun me(): AuthUser = client.get<MeResponse>("/api/auth/me").user

    suspend fun communityTrips(filters: Map<String, String?>, cursor: String?, limit: Int = 12): TripPage =
        client.get("/api/community/trips", filters + mapOf("cursor" to cursor, "limit" to limit.toString()))

    suspend fun communityDestinations(): List<CommunityDestination> =
        client.get<ListResponse<CommunityDestination>>("/api/community/destinations").data

    suspend fun communityTrip(slug: String): TripResponse = client.get("/api/community/trips/${slug.encoded()}")

    suspend fun partnerLinks(slug: String): PartnerLinksResponse = client.get("/api/community/trips/${slug.encoded()}/partner-links")

    suspend fun tripMap(slug: String): TripMap = client.get<DataResponse<TripMap>>("/api/community/trips/${slug.encoded()}/map").data

    suspend fun comments(slug: String): CommentPage = client.get("/api/community/trips/${slug.encoded()}/comments")

    suspend fun setSaved(slug: String, saved: Boolean): SaveState =
        client.send<DataResponse<SaveState>>(if (saved) "PUT" else "DELETE", "/api/community/trips/${slug.encoded()}/save").data

    /** Which of these community trip ids the user saved (empty when signed out) */
    suspend fun savedState(tripIds: List<String>): Set<String> =
        if (tripIds.isEmpty()) emptySet()
        else client.get<ListResponse<String>>("/api/community/saved/state", mapOf("ids" to tripIds.joinToString(","))).data.toSet()

    suspend fun saved(cursor: String?): TripPage = client.get("/api/community/saved", mapOf("cursor" to cursor))

    suspend fun deals(): List<DealTemplate> = client.get<ListResponse<DealTemplate>>("/api/deal-templates").data

    suspend fun myTrips(): List<TripSummary> = client.get<ListResponse<TripSummary>>("/api/trips").data

    suspend fun myTrip(id: String): SavedTripResponse = client.get("/api/trips/${id.encoded()}")

    suspend fun deleteTrip(id: String) = client.send<Ok>("DELETE", "/api/trips/${id.encoded()}")

    suspend fun shareTrip(id: String, enabled: Boolean): String? =
        client.send<ShareBody, ShareResponse>("POST", "/api/trips/${id.encoded()}/share", ShareBody(enabled)).shareId

    suspend fun plan(body: PlannerBody): PlannerResult = client.send("POST", "/api/planner/itinerary", body)

    suspend fun saveGeneration(generationId: String): String =
        client.send<SaveTripBody, IdResponse>("POST", "/api/trips", SaveTripBody(generationId)).data.id

    suspend fun click(body: ClickBody): String? = client.send<ClickBody, ClickResponse>("POST", "/api/affiliate/click", body).outboundUrl
}

private fun String.encoded(): String = java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
