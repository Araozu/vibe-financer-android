package com.vibefinancer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vibefinancer.app.data.DetailedAccountDto
import com.vibefinancer.app.data.FinancerRepository
import com.vibefinancer.app.data.SessionStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repo: FinancerRepository,
    store: SessionStore,
    accounts: List<DetailedAccountDto>,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var server by remember { mutableStateOf("") }
    var widgetAccountId by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        server = store.baseUrl()
        widgetAccountId = store.widgetAccountId()
    }

    val widgetAccount = accounts.firstOrNull { it.id == widgetAccountId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Server", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = server, onValueChange = { server = it },
                label = { Text("Server URL") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = {
                    scope.launch {
                        store.setBaseUrl(server.ifBlank { SessionStore.DEFAULT_BASE_URL })
                        snackbar.showSnackbar("Saved. Sign in again if the server changed.")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save server URL") }

            Spacer(Modifier.height(8.dp))
            Text("Balance widget", style = MaterialTheme.typography.titleMedium)
            Text(
                "Shows one account's balance on your home screen. Tapping it opens the new-transaction form.",
                style = MaterialTheme.typography.bodySmall
            )
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = widgetAccount?.name ?: "Select account…",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Widget account") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    accounts.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text(acc.name) },
                            onClick = {
                                widgetAccountId = acc.id
                                scope.launch {
                                    store.setWidgetAccountId(acc.id)
                                    store.setWidgetCache(
                                        "${acc.name}|${acc.currentBalance}|${acc.currencySymbol ?: acc.currencyCode ?: ""}|now"
                                    )
                                    com.vibefinancer.app.widget.BalanceWidget.refresh(context)
                                    snackbar.showSnackbar("Widget will show ${acc.name}")
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        repo.logout()
                        onLoggedOut()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sign out") }
        }
    }
}
