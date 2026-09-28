package com.linnan.encrypted

import android.app.Application
import com.linnan.encrypted.data.AuthRepository
import com.linnan.encrypted.media.InstagramRepository
import com.linnan.encrypted.media.TikTokRepository
import com.linnan.encrypted.media.XRepository
import com.linnan.encrypted.media.YouTubeRepository

/** Simple hand-rolled DI container - deliberately no framework, given the app's small size. */
class AppContainer(app: Application) {
    val authRepository = AuthRepository(app)
    val instagramRepository = InstagramRepository(authRepository)
    val xRepository = XRepository(authRepository)
    val youTubeRepository = YouTubeRepository(authRepository)
    val tikTokRepository = TikTokRepository()
}

class LinnanApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
