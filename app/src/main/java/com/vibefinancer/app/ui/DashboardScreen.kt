package com.vibefinancer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibefinancer.app.data.ApiResult
import com.vibefinancer.app.data.DetailedAccountDto
import com.vibefinancer.app.data.FinancerRepository
import com.vibefinancer.app.data.TransactionDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

data class DashboardData(
    val accounts: List<DetailedAccountDto> = emptyList(),
    /** Every transaction in the selected month, across accounts. */
    val transactions: List<TransactionDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class DashboardState(private val repo: FinancerRepository) {
    private val _data = MutableStateFlow(DashboardData(loading = true))
    val data: StateFlow<DashboardData> = _data.asStateFlow()

    /** Independent scope so the two parallel fetches in [load] aren't bound to a caller. */
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _month = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val month: StateFlow<Int> = _month.asStateFlow()
    private val _year = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val year: StateFlow<Int> = _year.asStateFlow()

    suspend fun load() {
        _data.value = _data.value.copy(loading = true, error = null)
        val m = _month.value
        val y = _year.value
        val tz = TimeZone.getDefault().id
        // The two reads are independent: run them in parallel to halve latency.
        val detailedDeferred = loadScope.async { repo.detailedAccounts(m, y) }
        val monthTxDeferred = loadScope.async { repo.monthTransactions(m, y, tz) }
        val detailed = detailedDeferred.await()
        val monthTx = monthTxDeferred.await()
        _data.value = when (detailed) {
            is ApiResult.Err -> DashboardData(error = detailed.message)
            is ApiResult.Ok -> when (monthTx) {
                is ApiResult.Ok -> DashboardData(accounts = detailed.value, transactions = monthTx.value.transactions)
                is ApiResult.Err -> DashboardData(accounts = detailed.value, error = monthTx.message)
            }
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

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private data class StatItem(
    val title: String,
    val value: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val valueColor: Color = Color.Unspecified
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    repo: FinancerRepository,
    onOpenSettings: () -> Unit,
    onSessionExpired: () -> Unit
) {
    val data by state.data.collectAsState()
    val month by state.month.collectAsState()
    val year by state.year.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var now by remember { mutableStateOf(Instant.now()) }

    fun reload() = scope.launch {
        now = Instant.now()
        state.load()
        state.data.value.error?.let { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(month, year) { reload() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Instant.now()
        }
    }
    LaunchedEffect(data.error) {
        if (data.error?.contains("Unauthorized", ignoreCase = true) == true) onSessionExpired()
    }

    val today = LocalDate.now()
    val isCurrentMonth = today.year == year && today.monthValue - 1 == month
    val monthName = monthLabel(month, year).substringBefore(" ")
    val periodLabel = if (isCurrentMonth) "${monthName.take(3)} 1 – today" else "All of $monthName"
    val summary = remember(data.accounts, data.transactions, now, isCurrentMonth) {
        summarize(data.accounts, data.transactions, now, isCurrentMonth)
    }
    val primary = data.accounts.firstOrNull()
    fun money(cents: Int): String = formatMoney(cents, primary?.currencyCode, primary?.currencySymbol)
    val savingsSubtitle = if (summary.income > 0) "Of income kept this month" else "No income yet"
    val stats = listOf(
        StatItem(
            "Current balance", money(summary.currentBalance),
            "Across ${data.accounts.size} ${if (data.accounts.size == 1) "account" else "accounts"}",
            Icons.Filled.AccountBalanceWallet, MaterialTheme.colorScheme.primary
        ),
        StatItem(
            "Projected balance", money(summary.projectedBalance),
            "End of $monthName · incl. upcoming",
            Icons.Filled.Event, MaterialTheme.colorScheme.tertiary
        ),
        StatItem("Income", money(summary.income), periodLabel, Icons.Filled.TrendingUp, incomeColor()),
        StatItem(
            "Expenses", money(summary.expenses), periodLabel,
            Icons.Filled.TrendingDown, MaterialTheme.colorScheme.error
        ),
        StatItem(
            "Savings rate", "%.1f%%".format(summary.savingsRate), savingsSubtitle,
            Icons.Filled.Savings, MaterialTheme.colorScheme.secondary,
            if (summary.savingsRate < 0) MaterialTheme.colorScheme.error else Color.Unspecified
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vibe Financer") },
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
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (data.loading && data.accounts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                MonthBar(month, year, onPrev = { state.prevMonth() }, onNext = { state.nextMonth() })
            }
            item {
                StatGrid(stats)
            }
            item {
                NewTransactionCard(repo, data.accounts, onSaved = { reload() })
            }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Balance overview", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "End-of-day balance for each account in ${monthLabel(month, year)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        BalanceOverviewChart(data.accounts)
                        AccountLegend(data.accounts)
                    }
                }
            }
            item {
                RecentTransactionsCard(
                    transactions = data.transactions,
                    accounts = data.accounts,
                    now = now,
                    monthName = monthName
                )
            }
            item {
                SavingsGoalCard(data.accounts, summary.accountBalances)
            }
        }
    }
}

@Composable
private fun incomeColor(): Color = if (isSystemInDarkTheme()) Color(0xFF7FD8A0) else Color(0xFF1B7A3D)

@Composable
private fun MonthBar(month: Int, year: Int, onPrev: () -> Unit, onNext: () -> Unit) {
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
private fun StatGrid(items: List<StatItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item -> StatCard(item, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCard(item: StatItem, modifier: Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, contentDescription = null, tint = item.tint, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                item.value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = item.valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NewTransactionCard(
    repo: FinancerRepository,
    accounts: List<DetailedAccountDto>,
    onSaved: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("New transaction", style = MaterialTheme.typography.titleMedium)
            if (accounts.isEmpty()) {
                Text(
                    "Create an account on the web app to start adding transactions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TransactionForm(repo, accounts, initialAccountId = null, onSaved = onSaved)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentTransactionsCard(
    transactions: List<TransactionDto>,
    accounts: List<DetailedAccountDto>,
    now: Instant,
    monthName: String
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var showUpcoming by rememberSaveable { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    val sorted = remember(transactions) { transactions.sortedByDescending { txInstant(it) } }
    val (upcomingAll, postedAll) = remember(sorted, now) { sorted.partition { txInstant(it).isAfter(now) } }
    val options = remember(transactions) { categoryOptions(transactions) }
    val hasUncategorized = remember(transactions) { transactions.any { it.category.isNullOrBlank() } }
    val accountsById = remember(accounts) { accounts.associateBy { it.id } }

    val filtersActive = query.isNotBlank() || category != null
    // Filtering + grouping re-parse every timestamp: keep them out of the
    // recomposition path for scrolling/typing.
    val posted = remember(postedAll, query, category) {
        postedAll.filter { matchesFilters(it, query, category) }
    }
    val upcoming = remember(upcomingAll, query, category) {
        upcomingAll.filter { matchesFilters(it, query, category) }
    }
    val shown = remember(posted, showAll, filtersActive) {
        when {
            filtersActive -> posted.take(500)
            showAll -> posted
            else -> posted.take(10)
        }
    }
    val upcomingGroups = remember(upcoming, showUpcoming) {
        if (showUpcoming) groupByDay(upcoming) else emptyList()
    }
    val shownGroups = remember(shown) { groupByDay(shown) }
    val summaryText = remember(postedAll, posted, shown, filtersActive, monthName) {
        when {
            filtersActive -> "${posted.size} ${if (posted.size == 1) "match" else "matches"} in $monthName"
            postedAll.isEmpty() -> "Nothing recorded in $monthName yet"
            shown.size < posted.size -> "Latest ${shown.size} of ${posted.size} in $monthName"
            else -> "${posted.size} ${if (posted.size == 1) "transaction" else "transactions"} in $monthName"
        }
    }
    val categoryLabel = remember(category) {
        when (val c = category) {
            null -> "All categories"
            "" -> "Uncategorized"
            else -> c
        }
    }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Recent transactions", style = MaterialTheme.typography.titleMedium)
                    Text(
                        summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (upcoming.isNotEmpty()) {
                    OutlinedButton(onClick = { showUpcoming = !showUpcoming }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Upcoming ${upcoming.size}")
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search name, payee, description…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
            )
            ExposedDropdownMenuBox(expanded = categoryMenuOpen, onExpandedChange = { categoryMenuOpen = it }) {
                OutlinedTextField(
                    value = categoryLabel,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenuOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("All categories") },
                        onClick = { category = null; categoryMenuOpen = false }
                    )
                    if (hasUncategorized) {
                        DropdownMenuItem(
                            text = { Text("Uncategorized") },
                            onClick = { category = ""; categoryMenuOpen = false }
                        )
                    }
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { category = option; categoryMenuOpen = false }
                        )
                    }
                }
            }

            if (showUpcoming && upcoming.isNotEmpty()) {
                DayGroups(upcomingGroups, accountsById, upcoming = true)
            }
            if (shown.isEmpty()) {
                val emptyText = when {
                    filtersActive -> "No transactions match your filters."
                    postedAll.isEmpty() -> "No transactions in $monthName yet."
                    else -> ""
                }
                if (emptyText.isNotEmpty()) {
                    Text(
                        emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                }
                if (filtersActive) {
                    TextButton(onClick = { query = ""; category = null }) { Text("Clear filters") }
                }
            } else {
                DayGroups(shownGroups, accountsById, upcoming = false)
            }
            if (!filtersActive && posted.size > 10) {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAll) "Show less" else "Show all ${posted.size}")
                }
            }
        }
    }
}

@Composable
private fun DayGroups(
    groups: List<DayGroup>,
    accountsById: Map<String, DetailedAccountDto>,
    upcoming: Boolean
) {
    Column {
        groups.forEach { group ->
            val account = group.transactions.firstNotNullOfOrNull { accountsById[it.accountId] }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    group.date.format(DAY_FORMAT),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                if (upcoming) {
                    Text(
                        "Upcoming · ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "Total spent ${formatMoney(-group.spent, account?.currencyCode, account?.currencySymbol)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            group.transactions.forEachIndexed { index, tx ->
                TransactionRow(tx, accountsById[tx.accountId])
                if (index < group.transactions.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionDto, account: DetailedAccountDto?) {
    val time = txInstant(tx).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
    val title = tx.name?.takeIf { it.isNotBlank() }
        ?: tx.category?.takeIf { it.isNotBlank() }
        ?: tx.type.replaceFirstChar { it.uppercase() }
    val details = listOfNotNull(time, account?.name, tx.category?.takeIf { it.isNotBlank() })
        .joinToString(" · ")
    val amount = formatMoney(tx.amount, account?.currencyCode, account?.currencySymbol)
    val (label, color) = when (tx.type) {
        "expense" -> "−$amount" to MaterialTheme.colorScheme.error
        "income" -> "+$amount" to incomeColor()
        else -> amount to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun SavingsGoalCard(accounts: List<DetailedAccountDto>, balances: Map<String, Int>) {
    val account = accounts.firstOrNull { it.goal != null }
    val goal = account?.goal
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (account == null || goal == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column {
                        Text("No savings goal", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Set a goal on a savings account in the web app to track it here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val current = balances[account.id] ?: account.currentBalance
                val remaining = (goal.targetAmount - current).coerceAtLeast(0)
                val progress = if (goal.targetAmount > 0) {
                    (current.toFloat() / goal.targetAmount).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Column {
                        Text(goal.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Savings goal · ${account.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatMoney(current, account.currencyCode, account.currencySymbol),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "of ${formatMoney(goal.targetAmount, account.currencyCode, account.currencySymbol)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "${(progress * 100).roundToInt()}% saved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (remaining > 0) {
                            "${formatMoney(remaining, account.currencyCode, account.currencySymbol)} to go"
                        } else {
                            "Goal reached"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
