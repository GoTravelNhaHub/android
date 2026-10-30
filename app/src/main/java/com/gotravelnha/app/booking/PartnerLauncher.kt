package com.gotravelnha.app.booking

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.gotravelnha.app.BuildConfig
import com.gotravelnha.app.data.ClickBody
import com.gotravelnha.app.data.GoTravelNhaApi

/**
 * Opens a partner page through /api/affiliate/click, so the click is recorded and the URL carries
 * our affiliate ids (added on the server). If the API cannot be reached, the partner URL still
 * opens with the ids added here, so the booking is not blocked.
 */
class PartnerLauncher(private val api: GoTravelNhaApi, private val sessionId: () -> String) {

    suspend fun open(
        context: Context,
        placement: String,
        screen: String,
        provider: String? = null,
        url: String? = null,
        offerId: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val tracked = runCatching {
            api.click(ClickBody(provider = provider, baseUrl = url, offerId = offerId, placementKey = placement, pagePath = "app:$screen", sessionId = sessionId(), metadata = metadata + ("platform" to "android")))
        }.getOrNull()
        val target = when {
            tracked != null && isApprovedPartnerUrl(tracked) -> tracked
            url != null && provider != null && isApprovedPartnerUrl(url) ->
                withFallbackAffiliateIds(url, provider, BuildConfig.KLOOK_AID, BuildConfig.TRIP_ALLIANCE_ID, BuildConfig.TRIP_SID)
            else -> return
        }
        openInBrowser(context, target)
    }
}

fun openInBrowser(context: Context, url: String) {
    val colors = CustomTabColorSchemeParams.Builder().setToolbarColor(0xFFFFFFFF.toInt()).build()
    runCatching {
        CustomTabsIntent.Builder().setDefaultColorSchemeParams(colors).setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    }.onFailure {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
