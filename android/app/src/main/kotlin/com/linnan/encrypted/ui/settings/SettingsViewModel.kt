package com.linnan.encrypted.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linnan.encrypted.data.AuthRepository
import com.linnan.encrypted.data.LoginOutcome
import com.linnan.encrypted.oauth.AuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProviderUiState(
    val provider: AuthProvider,
    val isLoggedIn: Boolean,
    val accountLabel: String?,
    val isBusy: Boolean,
    val errorJa: String?
)

class SettingsViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val busyProviders = MutableStateFlow<Set<AuthProvider>>(emptySet())
    private val errors = MutableStateFlow<Map<AuthProvider, String>>(emptyMap())

    val providerStates: StateFlow<List<ProviderUiState>> = combine(
        authRepository.loginStates, busyProviders, errors
    ) { logins, busy, errs ->
        AuthProvider.entries.map { p ->
            ProviderUiState(
                provider = p,
                isLoggedIn = logins[p] == true,
                accountLabel = authRepository.accountLabel(p),
                isBusy = busy.contains(p),
                errorJa = errs[p]
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AuthProvider.entries.map {
            ProviderUiState(it, authRepository.loginStates.value[it] == true, null, false, null)
        }
    )

    fun login(provider: AuthProvider) {
        if (busyProviders.value.contains(provider)) return
        busyProviders.value = busyProviders.value + provider
        errors.value = errors.value - provider
        viewModelScope.launch {
            when (val outcome = authRepository.login(provider)) {
                is LoginOutcome.Success -> {
                    // loginStates flips automatically; nothing else to do.
                }
                is LoginOutcome.Failure -> {
                    errors.value = errors.value + (provider to outcome.messageJa)
                }
                LoginOutcome.Cancelled -> {
                    errors.value = errors.value + (provider to "ログインがタイムアウト、またはキャンセルされました。")
                }
            }
            busyProviders.value = busyProviders.value - provider
        }
    }

    fun logout(provider: AuthProvider) {
        authRepository.logout(provider)
        errors.value = errors.value - provider
    }

    fun dismissError(provider: AuthProvider) {
        errors.value = errors.value - provider
    }
}
