package com.gotravelnha.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gotravelnha.app.BuildConfig
import com.gotravelnha.app.app
import com.gotravelnha.app.appLanguage
import com.gotravelnha.app.booking.klookLocalePath
import com.gotravelnha.app.booking.klookSearchUrl
import com.gotravelnha.app.booking.localizedPartnerUrl
import com.gotravelnha.app.booking.openInBrowser
import com.gotravelnha.app.data.AuthUser
import com.gotravelnha.app.ui.components.BookingAction
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** What the planner starts from: "Customize with AI" on a community trip, or a destination */
data class PlannerSeed(val destination: String, val days: Int? = null, val adaptFromId: String? = null, val adaptTitle: String? = null)

/** Activity-wide state: the signed-in user and hand-offs between tabs. */
class AppState(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    val api get() = app.api

    var user by mutableStateOf<AuthUser?>(null)
        private set
    var signedIn by mutableStateOf(app.auth.signedIn.value)
        private set
    var authError by mutableStateOf<String?>(null)

    var plannerSeed by mutableStateOf<PlannerSeed?>(null)
    var communityDestination by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            app.auth.signedIn.collectLatest { now ->
                signedIn = now
                user = if (now) runCatching { api.me() }.getOrNull() else null
            }
        }
    }

    suspend fun signInIntent(register: Boolean): Intent = app.auth.signInIntent(register, uiLocale(getApplication()))

    fun completeSignIn(data: Intent?) {
        viewModelScope.launch {
            runCatching { app.auth.completeSignIn(data) }
                .onFailure { authError = it.message ?: "sign-in failed" }
        }
    }

    /** Ends the local session; returns the intent that also ends the Keycloak session, if any */
    fun signOut(): Intent? = app.auth.signOutIntent()

    fun book(context: Context, action: BookingAction, screen: String, placement: String) {
        val language = appLanguage(context)
        viewModelScope.launch {
            val launcher = app.partners
            when (action) {
                is BookingAction.Offer -> {
                    val activity = action.activity
                    if (activity.offerId != null) {
                        launcher.open(context, placement, screen, provider = activity.provider, url = activity.partnerUrl, offerId = activity.offerId, metadata = mapOf("match" to "offer"))
                    } else if (activity.href != null) {
                        openInBrowser(context, BuildConfig.SITE_URL.trimEnd('/') + activity.href)
                    }
                }
                is BookingAction.Search -> {
                    val entry = action.entry
                    val link = entry.link
                    if (link != null) {
                        launcher.open(context, placement, screen, provider = link.provider, url = localizedPartnerUrl(link, klookLocalePath(language)), metadata = mapOf("match" to "product", "searchType" to "activity"))
                    } else {
                        val query = entry.search ?: entry.query
                        launcher.open(context, placement, screen, provider = "klook", url = klookSearchUrl(klookLocalePath(language), query), metadata = mapOf("match" to if (entry.search != null) "keyword" else "search", "searchType" to "activity"))
                    }
                }
                is BookingAction.Hotels -> launcher.open(context, placement, screen, provider = "trip", url = action.hotels.url, metadata = mapOf("searchType" to "hotel"))
            }
        }
    }

    fun openPartner(context: Context, provider: String, url: String, placement: String, screen: String) {
        viewModelScope.launch { app.partners.open(context, placement, screen, provider = provider, url = url) }
    }

    private fun uiLocale(context: Context) = when (appLanguage(context)) {
        "vi" -> "vi"
        "zh" -> "zh-CN"
        else -> "en"
    }
}

/** Starts the Keycloak sign-in (register = open the sign-up page) */
val LocalSignIn = compositionLocalOf<(Boolean) -> Unit> { {} }
