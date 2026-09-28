package com.linnan.encrypted.oauth

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Declared in AndroidManifest for the com.linnan.encrypted "oauth" scheme redirects. The system
 * browser (Chrome Custom Tabs) lands here after the user finishes logging in - this activity
 * has no layout of its own, so there is nothing that can render as a black screen. It just
 * forwards the redirect Uri to whoever is waiting and immediately closes itself, returning
 * the user to MainActivity underneath.
 */
class OAuthRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        OAuthResultBus.emit(uri)
    }
}
