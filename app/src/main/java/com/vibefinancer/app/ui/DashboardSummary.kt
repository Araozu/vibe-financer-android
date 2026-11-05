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
    isCurrentMonth: Boolean
): DashboardSummary {
    val (upcoming, posted) = transactions.partition { txInstant(it).isAfter(now) }
    val pending = if (isCurrentMonth) upcoming else emptyList()
    val stats = if (isCurrentMonth) posted else transactions
    val accountBalances = accounts.associate { acc ->
        acc.id to (acc.currentBalance - pending.sumOf { balanceDelta(it, acc.id) })
    }
    return DashboardSummary(
        currentBalance = accountBalances.values.sum(),
        projectedBalance = accounts.sumOf { it.currentBalance },
        income = stats.filter { it.type == "income" }.sumOf { it.amount },
        expenses = stats.filter { it.type == "expense" }.sumOf { it.amount },
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
