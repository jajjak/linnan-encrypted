package com.linnan.encrypted.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linnan.encrypted.oauth.AuthProvider
import com.linnan.encrypted.ui.theme.ErrorRed
import com.linnan.encrypted.ui.theme.Gold
import com.linnan.encrypted.ui.theme.SuccessGreen

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val states by viewModel.providerStates.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "設定",
            style = MaterialTheme.typography.titleLarge,
            color = Gold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "各サービスにログインすると、ログイン中の本人のコンテンツを取得できるようになります。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(states) { state ->
                ProviderCard(
                    state = state,
                    onLoginClick = { viewModel.login(state.provider) },
                    onLogoutClick = { viewModel.logout(state.provider) },
                    onDismissError = { viewModel.dismissError(state.provider) }
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    state: ProviderUiState,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onDismissError: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.provider == AuthProvider.INSTAGRAM) "Instagram" else state.provider.displayNameJa,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                LoginStatusBadge(isLoggedIn = state.isLoggedIn)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "ログイン状態: " + if (state.isLoggedIn) {
                    "ログイン済み" + (state.accountLabel?.let { " (@$it)" } ?: "")
                } else "未ログイン",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            when {
                state.isBusy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Gold, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("認証画面を開いています…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.isLoggedIn -> OutlinedButton(onClick = onLogoutClick) {
                    Text("ログアウト")
                }
                else -> Button(onClick = onLoginClick) {
                    Text(
                        if (state.provider == AuthProvider.INSTAGRAM) "Instagramにログイン"
                        else "${state.provider.displayNameJa}にログイン"
                    )
                }
            }

            state.errorJa?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .background(color = ErrorRed.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .clickable(onClick = onDismissError)
                )
            }
        }
    }
}

@Composable
private fun LoginStatusBadge(isLoggedIn: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isLoggedIn) Icons.Filled.CheckCircle else Icons.Filled.Circle,
            contentDescription = null,
            tint = if (isLoggedIn) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (isLoggedIn) "ログイン済み" else "未ログイン",
            color = if (isLoggedIn) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
