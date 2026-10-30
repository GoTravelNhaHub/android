package com.gotravelnha.app.ui.profile

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gotravelnha.app.BuildConfig
import com.gotravelnha.app.R
import com.gotravelnha.app.appLanguage
import com.gotravelnha.app.booking.klookLocalePath
import com.gotravelnha.app.booking.openInBrowser
import com.gotravelnha.app.ui.AppState
import com.gotravelnha.app.ui.LocalSignIn
import com.gotravelnha.app.ui.components.Avatar

@Composable
fun ProfileScreen(state: AppState) {
    val context = LocalContext.current
    val signIn = LocalSignIn.current
    val site = BuildConfig.SITE_URL.trimEnd('/')

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tab_profile), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        val user = state.user
        if (state.signedIn) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(user?.displayName ?: "?", null, 52)
                Column {
                    Text(user?.displayName ?: stringResource(R.string.profile_loading), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    user?.email?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            OutlinedButton(onClick = { openInBrowser(context, "${BuildConfig.OIDC_ISSUER.trimEnd('/')}/account") }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.profile_manage_account))
            }
            OutlinedButton(onClick = { state.signOut()?.let(context::startActivity) }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_sign_out))
            }
        } else {
            Text(stringResource(R.string.profile_signed_out), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { signIn(false) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_sign_in)) }
            OutlinedButton(onClick = { signIn(true) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_register)) }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.profile_partners), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        LinkRow(stringResource(R.string.profile_trip_hotels)) {
            state.openPartner(context, "trip", "https://www.trip.com/hotels/?locale=en-SG&curr=SGD", "app_profile_partner", "profile")
        }
        LinkRow(stringResource(R.string.profile_klook_activities)) {
            state.openPartner(context, "klook", "https://www.klook.com/${klookLocalePath(appLanguage(context))}/", "app_profile_partner", "profile")
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        LinkRow(stringResource(R.string.profile_language)) {
            // Android 13+: per-app language in system settings (res/xml/locales_config)
            val intent = if (android.os.Build.VERSION.SDK_INT >= 33) {
                Intent(Settings.ACTION_APP_LOCALE_SETTINGS).setData(android.net.Uri.fromParts("package", context.packageName, null))
            } else {
                Intent(Settings.ACTION_LOCALE_SETTINGS)
            }
            runCatching { context.startActivity(intent) }
        }
        LinkRow(stringResource(R.string.profile_help)) { openInBrowser(context, "$site/help") }
        LinkRow(stringResource(R.string.profile_privacy)) { openInBrowser(context, "$site/privacy") }
        LinkRow(stringResource(R.string.profile_terms)) { openInBrowser(context, "$site/terms") }
        LinkRow(stringResource(R.string.profile_website)) { openInBrowser(context, site) }
        Text(stringResource(R.string.profile_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Text(label, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
}
