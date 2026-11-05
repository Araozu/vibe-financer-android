package com.vibefinancer.app.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.vibefinancer.app.R
import com.vibefinancer.app.data.ApiResult
import com.vibefinancer.app.data.FinancerRepository
import com.vibefinancer.app.data.SessionStore
import com.vibefinancer.app.data.parseWidgetCache
import java.text.NumberFormat
import java.util.Locale

private fun formatWidgetMoney(cents: Long, symbol: String): String {
    val number = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(cents / 100.0)
    return try {
        if (symbol.length == 3 && symbol.all { it.isLetter() }) {
            val nf = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
            nf.currency = java.util.Currency.getInstance(symbol)
            nf.format(cents / 100.0)
        } else {
            "$symbol$number"
        }
    } catch (_: Exception) {
        "$symbol$number"
    }
}

class BalanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Render from cache only: no network, no per-key DataStore roundtrips.
        // A single snapshot read keeps cold widget renders to one file read.
        val snap = SessionStore(context).snapshot()
        val cache = parseWidgetCache(snap.widgetCacheRaw)
        val accountId = cache?.accountId ?: snap.widgetAccountId
        provideContent {
            GlanceTheme(DynamicThemeColorProviders) {
                WidgetContent(
                    accountName = cache?.accountName ?: "VibeFinancer",
                    balanceText = if (cache != null) {
                        formatWidgetMoney(cache.balanceCents, cache.symbol)
                    } else null,
                    accountId = accountId
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(accountName: String, balanceText: String?, accountId: String?) {
        val context = LocalContext.current
        val openForm = accountId?.let {
            actionStartActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("vibefinancer://new-transaction?accountId=$it")
                ).apply {
                    component = ComponentName(context.packageName, "com.vibefinancer.app.MainActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
        }
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(16.dp)
                    .padding(horizontal = 12.dp)
                    .let { m -> if (openForm != null) m.clickable(openForm) else m },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    accountName,
                    style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 11.sp),
                    maxLines = 1
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    balanceText ?: "Open the app to pick an account",
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .width(44.dp)
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(16.dp)
                    .clickable(
                        actionRunCallback<RefreshAction>(
                            actionParametersOf(AccountKey to (accountId ?: ""))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                    modifier = GlanceModifier.size(18.dp)
                )
            }
        }
    }

    companion object {
        val AccountKey = ActionParameters.Key<String>("accountId")

        suspend fun refresh(context: Context) {
            BalanceWidget().updateAll(context)
        }

        suspend fun refreshWithBalance(
            context: Context,
            accountId: String,
            accountName: String,
            balanceCents: Int,
            symbol: String
        ) {
            val store = SessionStore(context)
            store.setWidgetCacheV2(accountId, accountName, balanceCents, symbol)
            BalanceWidget().updateAll(context)
        }
    }
}

/**
 * Re-fetch the selected account balance and push it to the widget. Uses the
 * lightweight account list (no chart data) so a tap refreshes in one small
 * request instead of the full detailed payload.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val store = SessionStore(context)
        val snap = store.snapshot()
        val accountId = parameters[BalanceWidget.AccountKey]?.ifBlank { null }
            ?: parseWidgetCache(snap.widgetCacheRaw)?.accountId
            ?: snap.widgetAccountId
            ?: return
        val repo = FinancerRepository(store)
        when (val res = repo.lightAccounts()) {
            is ApiResult.Ok -> {
                res.value.firstOrNull { it.id == accountId }?.let { acc ->
                    val symbol = acc.currencySymbol ?: acc.currencyCode ?: ""
                    store.setWidgetCacheV2(acc.id, acc.name, acc.currentBalance, symbol)
                    updateAppWidgetState(context, glanceId) {}
                    BalanceWidget().updateAll(context)
                }
            }
            is ApiResult.Err -> Unit
        }
    }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}
