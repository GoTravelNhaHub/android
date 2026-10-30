package com.gotravelnha.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.gotravelnha.app.ui.AppNav
import com.gotravelnha.app.ui.theme.GoTravelNhaTheme

class MainActivity : ComponentActivity() {
    /** Community trip slug from a shared https://www.gotravelnha.com/community/trips/<slug> link */
    private val deepLinkSlug = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) deepLinkSlug.value = slugOf(intent)
        setContent {
            GoTravelNhaTheme {
                AppNav(deepLinkSlug = deepLinkSlug.value, onDeepLinkHandled = { deepLinkSlug.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        slugOf(intent)?.let { deepLinkSlug.value = it }
    }

    private fun slugOf(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        val segments = uri.pathSegments
        return if (segments.size >= 3 && segments[0] == "community" && segments[1] == "trips") segments[2].takeIf { Regex("^[a-z0-9-]{3,80}$").matches(it) } else null
    }
}
