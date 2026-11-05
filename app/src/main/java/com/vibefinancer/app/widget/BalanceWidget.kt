package com.vibefinancer.app.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.Button
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.GlanceModifier as GModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.vibefinancer.app.data.ApiResult
import com.vibefinancer.app.data.FinancerRepository
import com.vibefinancer.app.data.SessionStore
import java.text.NumberFormat
import java.util.Calendar
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
        val store = SessionStore(context)
        val accountId = store.widgetAccountId()
        val cache = store.widgetCache()?.split("|")
        provideContent {
            GlanceTheme {
                WidgetContent(
                    accountName = cache?.getOrNull(0) ?: "VibeFinancer",
                    balanceText = if (cache != null && cache.size >= 3) {
                        formatWidgetMoney(cache[1].toLongOrNull() ?: 0L, cache[2])
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
        Column(
            modifier = GModifier
                .fillMaxSize()
                .padding(8.dp)
                .let { m -> if (openForm != null) m.clickable(openForm) else m },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GModifier.defaultWeight()) {
                    Text(
                        accountName,
                        style = TextStyle(fontSize = 12.sp),
                        maxLines = 1
                    )
                    if (balanceText != null) {
                        Text(
                            balanceText,
                            style = TextStyle(fontSize = 20.sp),
                            maxLines = 1
                        )
                    } else {
                        Text("Open the app to pick an account", style = TextStyle(fontSize = 13.sp))
                    }
                }
                Spacer(GModifier.width(8.dp))
                Button(
                    text = "Refresh",
                    onClick = actionRunCallback<RefreshAction>(
                        actionParametersOf(AccountKey to (accountId ?: ""))
                    )
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
            accountName: String,
            balanceCents: Int,
            symbol: String
        ) {
            val store = SessionStore(context)
            store.setWidgetCache("$accountName|$balanceCents|$symbol|now")
            BalanceWidget().updateAll(context)
        }
    }
}

/** Re-fetch the selected account balance and push it to the widget. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val store = SessionStore(context)
        val accountId = parameters[BalanceWidget.AccountKey]?.ifBlank { null }
            ?: store.widgetAccountId()
            ?: return
        val repo = FinancerRepository(store)
        val now = Calendar.getInstance()
        when (val res = repo.detailedAccounts(now.get(Calendar.MONTH), now.get(Calendar.YEAR))) {
            is ApiResult.Ok -> {
                res.value.firstOrNull { it.id == accountId }?.let { acc ->
                    val symbol = acc.currencySymbol ?: acc.currencyCode ?: ""
                    store.setWidgetCache("${acc.name}|${acc.currentBalance}|$symbol|now")
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
