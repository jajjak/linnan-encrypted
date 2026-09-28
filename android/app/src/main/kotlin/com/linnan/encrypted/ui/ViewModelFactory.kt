package com.linnan.encrypted.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.linnan.encrypted.AppContainer
import com.linnan.encrypted.ui.home.HomeViewModel
import com.linnan.encrypted.ui.settings.SettingsViewModel

class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        SettingsViewModel::class.java -> SettingsViewModel(container.authRepository) as T
        HomeViewModel::class.java -> HomeViewModel(
            authRepository = container.authRepository,
            instagramRepository = container.instagramRepository,
            xRepository = container.xRepository,
            youTubeRepository = container.youTubeRepository,
            tikTokRepository = container.tikTokRepository
        ) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
