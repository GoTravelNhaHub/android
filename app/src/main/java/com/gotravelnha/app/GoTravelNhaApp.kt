package com.gotravelnha.app

import android.app.Application
import android.content.Context
import com.gotravelnha.app.auth.AuthManager
import com.gotravelnha.app.booking.PartnerLauncher
import com.gotravelnha.app.data.ApiClient
import com.gotravelnha.app.data.GoTravelNhaApi
import org.osmdroid.config.Configuration
import java.util.Locale
import java.util.UUID

class GoTravelNhaApp : Application() {
    lateinit var auth: AuthManager
        private set
    lateinit var api: GoTravelNhaApi
        private set
    lateinit var partners: PartnerLauncher
        private set

    /** Anonymous id for click attribution, like the site's visitor session */
    val sessionId: String by lazy {
        val prefs = getSharedPreferences("gotravelnha", MODE_PRIVATE)
        prefs.getString("session_id", null) ?: "app-${UUID.randomUUID()}".also { prefs.edit().putString("session_id", it).apply() }
    }

    override fun onCreate() {
        super.onCreate()
        auth = AuthManager(this)
        api = GoTravelNhaApi(ApiClient(auth))
        partners = PartnerLauncher(api) { sessionId }
        // OpenStreetMap tile policy: identify the app
        Configuration.getInstance().userAgentValue = "GoTravelNha-Android/${BuildConfig.VERSION_NAME}"
    }
}

val Context.app: GoTravelNhaApp get() = applicationContext as GoTravelNhaApp

/** The app's content language (en, vi or zh), from the per-app or system language */
fun appLanguage(context: Context): String {
    val locale = context.resources.configuration.locales.takeIf { !it.isEmpty }?.get(0) ?: Locale.getDefault()
    return when (locale.language) {
        "vi" -> "vi"
        "zh" -> "zh"
        else -> "en"
    }
}
