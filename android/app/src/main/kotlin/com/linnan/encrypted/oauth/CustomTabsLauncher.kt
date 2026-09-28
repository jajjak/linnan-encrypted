package com.linnan.encrypted.oauth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import com.linnan.encrypted.ui.theme.ObsidianBlack

/**
 * Opens a login URL in Chrome Custom Tabs instead of an in-app WebView.
 *
 * This is the actual fix for the black-screen bug: a WebView renders Instagram's own login
 * page inside the app's process, and Instagram/Meta's client actively detects and blocks
 * "embedded browser" user agents for OAuth (their stated anti-phishing policy) - the page
 * frequently returns an empty/blocked response, which a bare WebView shows as a blank black
 * surface if the app's background is black and no error UI was wired up. Custom Tabs run the
 * real, fully up-to-date Chrome (or the user's default browser) as a separate, trusted
 * process: it shares the user's existing browser cookies/session, is not blocked by any
 * embedded-browser detection, and cannot be intercepted by the host app - which is also why
 * Google, Meta and TikTok all *require* it (or an equivalent system browser flow) for OAuth
 * login instead of a WebView.
 */
object CustomTabsLauncher {

    fun launch(context: Context, url: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(
                androidx.browser.customtabs.CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(ObsidianBlack.toArgb())
                    .setNavigationBarColor(ObsidianBlack.toArgb())
                    .build()
            )
            .setShowTitle(true)
            .build()
        try {
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (_: ActivityNotFoundException) {
            // No browser at all on the device (extremely rare). Fall back to a generic
            // ACTION_VIEW so the OS can offer whatever it has, rather than crashing.
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
