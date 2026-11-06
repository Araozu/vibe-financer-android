package com.vibefinancer.app.ui

import com.vibefinancer.app.data.TransactionDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun txInstant(tx: TransactionDto): Instant =
    runCatching { Instant.parse(tx.createdAt) }.getOrDefault(Instant.EPOCH)

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
