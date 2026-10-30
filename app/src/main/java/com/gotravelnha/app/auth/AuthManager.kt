package com.gotravelnha.app.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gotravelnha.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.ResponseTypeValues
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Keycloak sign-in (realm gotravelnha, public client gotravelnha-mobile) with Authorization Code +
 * PKCE in a Custom Tab, through AppAuth. Access tokens (aud gotravelnha-api) go to the API as
 * `Authorization: Bearer`; the offline refresh token keeps the user signed in for weeks.
 */
class AuthManager(context: Context) {
    private val appContext = context.applicationContext
    private val store = TokenStore(appContext)
    private val service by lazy { AuthorizationService(appContext) }
    private val refreshLock = Mutex()

    private var state: AuthState = store.read()?.let { runCatching { AuthState.jsonDeserialize(it) }.getOrNull() } ?: AuthState()
    private val _signedIn = MutableStateFlow(state.isAuthorized)
    val signedIn: StateFlow<Boolean> = _signedIn

    private suspend fun configuration(): AuthorizationServiceConfiguration =
        state.authorizationServiceConfiguration ?: suspendCancellableCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(BuildConfig.OIDC_ISSUER)) { config, error ->
                if (config != null) cont.resume(config) else cont.resumeWithException(error ?: IllegalStateException("discovery failed"))
            }
        }

    /** Intent that opens the Keycloak page; hand its result to [completeSignIn]. */
    suspend fun signInIntent(register: Boolean, uiLocale: String): Intent {
        val config = configuration()
        val extras = buildMap {
            put("ui_locales", uiLocale)
            if (register) put("prompt", "create")
        }
        val request = AuthorizationRequest.Builder(config, BuildConfig.OIDC_CLIENT_ID, ResponseTypeValues.CODE, Uri.parse(BuildConfig.OIDC_REDIRECT_URI))
            .setScopes("openid", "profile", "email", "offline_access")
            .setAdditionalParameters(extras)
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    /** Exchanges the code from the Keycloak redirect. Returns false when the user cancelled. */
    suspend fun completeSignIn(data: Intent?): Boolean {
        val response = data?.let { AuthorizationResponse.fromIntent(it) }
        val error = data?.let { AuthorizationException.fromIntent(it) }
        if (response == null) {
            if (error == null || error == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW) return false
            throw error
        }
        val fresh = AuthState(response, null)
        val tokens = suspendCancellableCoroutine { cont ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
                if (tokenResponse != null) cont.resume(tokenResponse) else cont.resumeWithException(exception ?: IllegalStateException("token exchange failed"))
            }
        }
        fresh.update(tokens, null)
        state = fresh
        persist()
        return true
    }

    /** A current access token, refreshed when it is about to expire; null when signed out. */
    suspend fun accessToken(forceRefresh: Boolean = false): String? = refreshLock.withLock {
        if (!state.isAuthorized) return null
        if (forceRefresh) state.needsTokenRefresh = true
        try {
            suspendCancellableCoroutine { cont ->
                state.performActionWithFreshTokens(service) { token, _, error ->
                    if (error == null) cont.resume(token) else cont.resumeWithException(error)
                }
            }
        } catch (error: AuthorizationException) {
            // Refresh token expired or revoked (Keycloak offline session ended): sign out locally
            if (error.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR) signOutLocally()
            null
        } finally {
            persist()
        }
    }

    /** Intent that ends the Keycloak session in the browser too, or null when it cannot be built. */
    fun signOutIntent(): Intent? {
        val config = state.authorizationServiceConfiguration
        val idToken = state.idToken
        signOutLocally()
        if (config?.endSessionEndpoint == null || idToken == null) return null
        val request = EndSessionRequest.Builder(config)
            .setIdTokenHint(idToken)
            .setPostLogoutRedirectUri(Uri.parse(BuildConfig.OIDC_REDIRECT_URI))
            .build()
        return service.getEndSessionRequestIntent(request)
    }

    fun signOutLocally() {
        state = AuthState()
        store.clear()
        _signedIn.value = false
    }

    private fun persist() {
        if (state.isAuthorized) store.write(state.jsonSerializeString()) else store.clear()
        _signedIn.value = state.isAuthorized
    }
}
