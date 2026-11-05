package com.vibefinancer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.vibefinancer.app.data.ApiResult
import com.vibefinancer.app.data.DetailedAccountDto
import com.vibefinancer.app.data.FinancerRepository
import com.vibefinancer.app.data.NetWorthTrendPointDto
import com.vibefinancer.app.data.TransactionDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar
import java.util.TimeZone

data class DashboardData(
    val accounts: List<DetailedAccountDto> = emptyList(),
    /** Full month transaction list per account (for month totals + per-account history). */
    val monthTx: Map<String, List<TransactionDto>> = emptyMap(),
    val trend: List<NetWorthTrendPointDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class DashboardState(private val repo: FinancerRepository) {
    private val _data = MutableStateFlow(DashboardData(loading = true))
    val data: StateFlow<DashboardData> = _data.asStateFlow()

    private val _month = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val month: StateFlow<Int> = _month.asStateFlow()
    private val _year = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val year: StateFlow<Int> = _year.asStateFlow()

    suspend fun load() {
        _data.value = _data.value.copy(loading = true, error = null)
        val m = _month.value
        val y = _year.value
        val tz = TimeZone.getDefault().id
        val detailed = repo.detailedAccounts(m, y)
        val monthTx = repo.monthTransactions(m, y, tz)
        // 30-day net-worth trend ending today.
        val end = LocalDate.now()
        val start = end.minusDays(30)
        val trend = repo.netWorthTrend(
            start.atStartOfDay().toInstant(ZoneOffset.UTC).toString(),
            end.atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toString(),
            1
        )
        _data.value = when (detailed) {
            is ApiResult.Err -> DashboardData(error = detailed.message)
            is ApiResult.Ok -> DashboardData(
                accounts = detailed.value,
                monthTx = (monthTx as? ApiResult.Ok)?.value?.transactions
                    ?.groupBy { it.accountId } ?: emptyMap(),
                trend = (trend as? ApiResult.Ok)?.value?.trend ?: emptyList()
            )
        }
    }

    fun prevMonth() {
        val c = Calendar.getInstance().apply { set(_year.value, _month.value, 1); add(Calendar.MONTH, -1) }
        _month.value = c.get(Calendar.MONTH)
        _year.value = c.get(Calendar.YEAR)
    }

    fun nextMonth() {
        val c = Calendar.getInstance().apply { set(_year.value, _month.value, 1); add(Calendar.MONTH, 1) }
        _month.value = c.get(Calendar.MONTH)
        _year.value = c.get(Calendar.YEAR)
    }
}

private fun void(@Suppress("UNUSED_PARAMETER") x: Any?) {}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    onAddTransaction: (accountId: String?) -> Unit,
    onOpenSettings: () -> Unit,
    onPickWidgetAccount: (DetailedAccountDto) -> Unit,
    onSessionExpired: () -> Unit
) {
    val data by state.data.collectAsState()
    val month by state.month.collectAsState()
    val year by state.year.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun reload() = scope.launch {
        state.load()
        state.data.value.error?.let { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(month, year) { reload() }
    LaunchedEffect(data.error) {
        if (data.error?.contains("Unauthorized", ignoreCase = true) == true) onSessionExpired()
    }

    val netWorth = data.accounts.sumOf { it.currentBalance }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VibeFinancer") },
                actions = {
                    IconButton(onClick = { reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddTransaction(data.accounts.firstOrNull()?.id) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Transaction") }
            )
        }
    ) { padding ->
        if (data.loading && data.accounts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                MonthNav(month, year, onPrev = { state.prevMonth() }, onNext = { state.nextMonth() })
            }
            item {
                NetWorthCard(netWorth, data.trend)
            }
            item {
                Text("Accounts", style = MaterialTheme.typography.titleMedium)
            }
            items(data.accounts, key = { it.id }) { acc ->
                AccountCard(
                    acc = acc,
                    monthTx = data.monthTx[acc.id] ?: acc.last10Transactions,
                    onAddTransaction = { onAddTransaction(acc.id) },
                    onSetAsWidget = { onPickWidgetAccount(acc) }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun MonthNav(month: Int, year: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month") }
        Text(monthLabel(month, year), style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next month") }
    }
}

@Composable
private fun NetWorthCard(netWorthCents: Int, trend: List<NetWorthTrendPointDto>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Net worth", style = MaterialTheme.typography.labelLarge)
            val first = trend.firstOrNull()?.netWorth
            val last = trend.lastOrNull()?.netWorth
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatMoney(netWorthCents, null, null),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.width(8.dp))
                if (first != null && last != null && trend.size > 1) {
                    val up = last >= first
                    Icon(
                        if (up) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        contentDescription = null,
                        tint = if (up) Color(0xFF1B7A3D) else Color(0xFFB3261E),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (trend.size > 1) {
                Spacer(Modifier.height(8.dp))
                TrendChart(trend.map { it.netWorth / 100.0 })
            } else {
                Text(
                    "Add transactions to see the 30-day graph.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun TrendChart(values: List<Double>, height: androidx.compose.ui.unit.Dp = 120.dp) {
    val line = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    Canvas(
        modifier = Modifier.fillMaxWidth().height(height)
    ) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it != 0.0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v ->
            Offset(
                i * stepX,
                (size.height - ((v - min) / span * (size.height - 8.dp.toPx()) + 4.dp.toPx())).toFloat()
            )
        }
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val fillPath = Path().apply {
            addPath(path)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(fillPath, Brush.verticalGradient(listOf(fill, Color.Transparent)))
        drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
        drawCircle(line, 5.dp.toPx(), pts.last())
    }
}

@Composable
private fun AccountCard(
    acc: DetailedAccountDto,
    monthTx: List<TransactionDto>,
    onAddTransaction: () -> Unit,
    onSetAsWidget: () -> Unit
) {
    // Signed deltas from this account's perspective (transfers included).
    fun delta(tx: TransactionDto): Int = when {
        tx.type == "transfer" && tx.accountId == acc.id -> -tx.amount
        tx.type == "transfer" -> tx.destinationAmount ?: tx.amount
        tx.type == "income" -> tx.amount
        else -> -tx.amount
    }
    val income = monthTx.filter { delta(it) > 0 }.sumOf { delta(it) }
    val expense = monthTx.filter { delta(it) < 0 }.sumOf { -delta(it) }
    val sorted = monthTx.sortedByDescending { it.createdAt }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(acc.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(
                            acc.type.replaceFirstChar { c -> c.uppercase() },
                            acc.currencyCode
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    formatMoney(acc.currentBalance, acc.currencyCode, acc.currencySymbol),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            if (acc.chartData.size > 1) {
                TrendChart(acc.chartData.map { it.balance }, height = 72.dp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "+${formatMoney(income, acc.currencyCode, acc.currencySymbol)}",
                    color = Color(0xFF1B7A3D),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "−${formatMoney(expense, acc.currencyCode, acc.currencySymbol)}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (sorted.isEmpty()) {
                Text("No transactions this month.", style = MaterialTheme.typography.bodySmall)
            } else {
                sorted.take(5).forEach { tx ->
                    TransactionRow(tx, acc, delta(tx) < 0)
                }
                if (sorted.size > 5) {
                    Text(
                        "+${sorted.size - 5} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAddTransaction) { Text("Add transaction") }
                TextButton(onClick = onSetAsWidget) { Text("Show in widget") }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionDto, acc: DetailedAccountDto, expense: Boolean) {
    val amountCents = if (tx.type == "transfer" && tx.accountId == acc.id) {
        -tx.amount
    } else if (tx.type == "income") {
        tx.amount
    } else {
        -tx.amount
    }
    void(expense)
    ListItem(
        headlineContent = { Text(tx.name ?: tx.category ?: tx.type) },
        supportingContent = {
            Text(
                listOfNotNull(
                    acc.name,
                    tx.category,
                    formatTxDate(tx.createdAt)
                ).joinToString(" · ")
            )
        },
        trailingContent = {
            Text(
                (if (amountCents < 0) "−" else "+") +
                    formatMoney(kotlin.math.abs(amountCents), acc.currencyCode, acc.currencySymbol),
                color = if (amountCents < 0) MaterialTheme.colorScheme.error
                else Color(0xFF1B7A3D),
                style = MaterialTheme.typography.titleSmall
            )
        }
    )
}
