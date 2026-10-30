package com.gotravelnha.app.booking

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.gotravelnha.app.BuildConfig
import com.gotravelnha.app.data.ClickBody
import com.gotravelnha.app.data.GoTravelNhaApi

/**
 * Opens a partner page through /api/affiliate/click, so the click is recorded and the URL carries
 * our affiliate ids (added on the server). If the API cannot be reached, the partner URL still
 * opens with the ids added here, so the booking is not blocked.
 *
 * When the Klook or Trip.com app is installed and claims the link, it opens there, so travellers
 * keep their partner account and saved details; otherwise it opens in a Custom Tab.
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
        if (!openInPartnerApp(context, target)) openInBrowser(context, target)
    }
}

/** Android 8–10 cannot ask for "any app but a browser", so the partner apps are named (see <queries> in the manifest) */
private val PARTNER_APP_PACKAGES = mapOf(
    "klook.com" to "com.klook",
    "trip.com" to "ctrip.english",
)

/**
 * Opens [url] in an installed app that handles it (the Klook or Trip.com app), never a browser.
 * Returns false when no such app is installed or the app does not handle this page.
 */
fun openInPartnerApp(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)
    val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Fails with ActivityNotFoundException when only browsers can open the link
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
    val host = uri.host?.lowercase() ?: return false
    val pkg = PARTNER_APP_PACKAGES.entries.firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }?.value ?: return false
    val explicit = intent.setPackage(pkg)
    if (explicit.resolveActivity(context.packageManager) == null) return false
    return runCatching { context.startActivity(explicit) }.isSuccess
}

fun openInBrowser(context: Context, url: String) {
    val colors = CustomTabColorSchemeParams.Builder().setToolbarColor(0xFFFFFFFF.toInt()).build()
    runCatching {
        CustomTabsIntent.Builder().setDefaultColorSchemeParams(colors).setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    }.onFailure {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
