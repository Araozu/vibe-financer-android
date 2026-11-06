package com.vibefinancer.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vibefinancer.app.data.FinancerRepository
import com.vibefinancer.app.data.SessionStore
import com.vibefinancer.app.ui.DashboardScreen
import com.vibefinancer.app.ui.DashboardState
import com.vibefinancer.app.ui.LoginScreen
import com.vibefinancer.app.ui.SettingsScreen
import com.vibefinancer.app.ui.TxnFormScreen
import com.vibefinancer.app.ui.theme.VibeFinancerTheme
import com.vibefinancer.app.widget.BalanceWidget
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Loading : Screen
    data object Login : Screen
    data object Dashboard : Screen
    data class Form(val accountId: String?) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    private var deepLinkAccount = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        setContent {
            VibeFinancerTheme {
                val context = LocalContext.current
                val store = remember { SessionStore(context) }
                val repo = remember { FinancerRepository(store) }
                val dashState = remember { DashboardState(repo) }
                var screen by remember { mutableStateOf<Screen>(Screen.Loading) }
                val scope = rememberCoroutineScope()
                val pendingDeepLink = deepLinkAccount.value
                // Subscribe here too: Form/Settings receive the account list as a
                // parameter, so this scope must recompose when fresh data arrives
                // (cold start straight into a deep link).
                val dashData by dashState.data.collectAsState()

                // Single routing effect: initial launch AND warm deep links
                // (widget taps while alive). Keyed on the deep-link state so a
                // slow DataStore read can't overwrite a navigation twice.
                LaunchedEffect(pendingDeepLink) {
                    val snap = store.snapshot()
                    val dl = deepLinkAccount.value
                    deepLinkAccount.value = null
                    screen = when {
                        snap.token == null -> Screen.Login
                        dl != null -> Screen.Form(dl.ifBlank { null })
                        screen is Screen.Loading -> Screen.Dashboard
                        else -> screen
                    }
                }

                // Keep the widget cache in sync with fresh dashboard data.
                // Balances come from the server summary (as-of-now), same as web.
                val accounts = dashData.accounts
                val serverBalances = dashData.summary?.accountBalances ?: emptyMap()
                LaunchedEffect(accounts, serverBalances) {
                    if (accounts.isNotEmpty()) {
                        val snap = store.snapshot()
                        val wanted = snap.widgetAccountId
                            ?: store.let {
                                // Default the widget to the first account on first run.
                                accounts.first().id.also { id -> it.setWidgetAccountId(id) }
                            }
                        accounts.firstOrNull { it.id == wanted }?.let { acc ->
                            BalanceWidget.refreshWithBalance(
                                context,
                                acc.id,
                                acc.name,
                                serverBalances[acc.id] ?: acc.currentBalance,
                                acc.currencySymbol ?: acc.currencyCode ?: ""
                            )
                        }
                    }
                }

                // Form/Settings need the account list; it may be empty on a
                // cold start straight into a deep link.
                LaunchedEffect(screen) {
                    if ((screen is Screen.Form || screen is Screen.Settings) &&
                        dashData.accounts.isEmpty()
                    ) {
                        dashState.load()
                    }
                }

                when (val s = screen) {
                    Screen.Loading -> {}
                    Screen.Login -> LoginScreen(repo, store, onLoggedIn = {
                        scope.launch { dashState.load() }
                        screen = Screen.Dashboard
                    })
                    Screen.Dashboard -> DashboardScreen(
                        state = dashState,
                        repo = repo,
                        onOpenSettings = { screen = Screen.Settings },
                        onSessionExpired = {
                            scope.launch { repo.logout(); screen = Screen.Login }
                        }
                    )
                    is Screen.Form -> TxnFormScreen(
                        repo = repo,
                        accounts = dashData.accounts,
                        balances = dashData.summary?.accountBalances ?: emptyMap(),
                        initialAccountId = s.accountId,
                        onSaved = {
                            scope.launch { dashState.load() }
                            screen = Screen.Dashboard
                        },
                        onBack = { screen = Screen.Dashboard }
                    )
                    Screen.Settings -> SettingsScreen(
                        repo = repo,
                        store = store,
                        accounts = dashData.accounts,
                        balances = dashData.summary?.accountBalances ?: emptyMap(),
                        onBack = { screen = Screen.Dashboard },
                        onLoggedOut = { screen = Screen.Login }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data
        if (uri?.scheme == "vibefinancer" && uri.host == "new-transaction") {
            deepLinkAccount.value = uri.getQueryParameter("accountId")
        }
    }
}
