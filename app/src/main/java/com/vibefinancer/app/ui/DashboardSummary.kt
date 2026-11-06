package com.vibefinancer.app.ui

import com.vibefinancer.app.data.DetailedAccountDto
import com.vibefinancer.app.data.TransactionDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Signed change (cents) that [tx] makes to the balance of [accountId]. */
fun balanceDelta(tx: TransactionDto, accountId: String): Int = when {
    tx.type == "transfer" && tx.accountId == accountId -> -tx.amount
    tx.type == "transfer" && tx.toAccountId == accountId -> tx.destinationAmount ?: tx.amount
    tx.accountId != accountId -> 0
    tx.type == "income" -> tx.amount
    else -> -tx.amount
}

fun txInstant(tx: TransactionDto): Instant =
    runCatching { Instant.parse(tx.createdAt) }.getOrDefault(Instant.EPOCH)

data class DashboardSummary(
    /** Cents, as of now. Future-dated transactions are not counted. */
    val currentBalance: Int,
    /** Cents, end of the selected month including upcoming transactions. */
    val projectedBalance: Int,
    val income: Int,
    val expenses: Int,
    /** Cents, as of now, keyed by account id. */
    val accountBalances: Map<String, Int>
) {
    val savingsRate: Double
        get() = if (income > 0) (income - expenses) * 100.0 / income else 0.0
}

fun summarize(
    accounts: List<DetailedAccountDto>,
    transactions: List<TransactionDto>,
    now: Instant,
    isCurrentMonth: Boolean,
    defaultAccountId: String? = null
): DashboardSummary {
    val (upcoming, posted) = transactions.partition { txInstant(it).isAfter(now) }
    val pending = if (isCurrentMonth) upcoming else emptyList()
    // Accumulate pending deltas per account in one pass instead of re-summing
    // per account (O(accounts × transactions)). The widget's main account
    // carries the most movements, so this is the hot path.
    val pendingDelta = mutableMapOf<String, Int>()
    for (tx in pending) {
        when {
            tx.type == "transfer" -> {
                pendingDelta[tx.accountId] = (pendingDelta[tx.accountId] ?: 0) - tx.amount
                val dest = tx.toAccountId
                if (dest != null) {
                    pendingDelta[dest] =
                        (pendingDelta[dest] ?: 0) + (tx.destinationAmount ?: tx.amount)
                }
            }
            tx.type == "income" ->
                pendingDelta[tx.accountId] = (pendingDelta[tx.accountId] ?: 0) + tx.amount
            else ->
                pendingDelta[tx.accountId] = (pendingDelta[tx.accountId] ?: 0) - tx.amount
        }
    }
    val accountBalances = accounts.associate { acc ->
        acc.id to (acc.currentBalance - (pendingDelta[acc.id] ?: 0))
    }
    // Mirror the web dashboard: summary stats are scoped to the default
    // (primary) account, not summed across all accounts.
    val resolvedDefaultId = defaultAccountId ?: accounts.firstOrNull()?.id
    val defaultAccount = accounts.firstOrNull { it.id == resolvedDefaultId }
    val defaultTx = if (resolvedDefaultId == null) emptyList() else transactions.filter {
        it.accountId == resolvedDefaultId || it.toAccountId == resolvedDefaultId
    }
    val (defaultUpcoming, defaultPosted) = defaultTx.partition { txInstant(it).isAfter(now) }
    val stats = if (isCurrentMonth) defaultPosted else defaultTx
    // Suppress unused warning when callers pass only posted data.
    @Suppress("UNUSED_VARIABLE")
    val ignoredUpcoming = defaultUpcoming
    return DashboardSummary(
        currentBalance = resolvedDefaultId?.let { accountBalances[it] } ?: 0,
        projectedBalance = defaultAccount?.currentBalance ?: 0,
        income = stats.filter { it.type == "income" && it.accountId == resolvedDefaultId }.sumOf { it.amount },
        expenses = stats.filter { it.type == "expense" && it.accountId == resolvedDefaultId }.sumOf { it.amount },
        accountBalances = accountBalances
    )
}

data class DayGroup(
    val date: LocalDate,
    val transactions: List<TransactionDto>,
    /** Cents spent (expenses only) on this day. */
    val spent: Int
)

fun groupByDay(transactions: List<TransactionDto>): List<DayGroup> =
    transactions
        .groupBy { txInstant(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        .map { (date, txs) -> DayGroup(date, txs, txs.filter { it.type == "expense" }.sumOf { it.amount }) }
        .sortedByDescending { it.date }

/** [category] null means all categories; an empty string means uncategorized. */
fun matchesFilters(tx: TransactionDto, query: String, category: String?): Boolean {
    if (category != null) {
        val txCategory = tx.category?.trim().orEmpty()
        if (category.isEmpty() && txCategory.isNotEmpty()) return false
        if (category.isNotEmpty() && txCategory != category) return false
    }
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return listOfNotNull(tx.name, tx.description, tx.payee, tx.category).any { it.lowercase().contains(q) }
}

fun categoryOptions(transactions: List<TransactionDto>): List<String> =
    transactions
        .mapNotNull { it.category?.trim()?.takeIf { c -> c.isNotEmpty() } }
        .distinct()
        .sortedBy { it.lowercase() }
