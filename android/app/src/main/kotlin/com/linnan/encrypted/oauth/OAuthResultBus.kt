package com.linnan.encrypted.oauth

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Hands the browser's OAuth redirect (captured by [OAuthRedirectActivity]) off to whichever
 * ViewModel is currently awaiting a login result. Process-local only; nothing sensitive is
 * persisted here.
 */
object OAuthResultBus {
    private val _redirects = MutableSharedFlow<Uri>(replay = 0, extraBufferCapacity = 4)
    val redirects = _redirects.asSharedFlow()

    fun emit(uri: Uri) {
        _redirects.tryEmit(uri)
    }
}
