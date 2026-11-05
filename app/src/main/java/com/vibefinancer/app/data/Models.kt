package com.vibefinancer.app.data

import kotlinx.serialization.Serializable

@Serializable
data class GoalDto(
    val id: String,
    val accountId: String,
    val targetAmount: Int,
    val targetDate: String? = null,
    val name: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class AccountDto(
    val id: String,
    val userId: String,
    val name: String,
    val description: String? = null,
    val type: String,
    /** Cents. */
    val currentBalance: Int,
    /** Cents. */
    val initialBalance: Int,
    val currencyId: String,
    val currencyCode: String? = null,
    val currencySymbol: String? = null,
    val color: String,
    val createdAt: String,
    val updatedAt: String,
    val goal: GoalDto? = null
)

@Serializable
data class TransactionDto(
    val id: String,
    val accountId: String,
    val type: String,
    /** Cents. */
    val amount: Int,
    val destinationAmount: Int? = null,
    val name: String? = null,
    val description: String? = null,
    val category: String? = null,
    val budgetId: String? = null,
    val payee: String? = null,
    val toAccountId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null
)

@Serializable
data class ChartPointDto(
    val date: String,
    /** Major currency units (backend already divides by 100). */
    val balance: Double
)

@Serializable
data class DetailedAccountDto(
    val id: String,
    val userId: String,
    val name: String,
    val description: String? = null,
    val type: String,
    /** Cents, at end of requested month. */
    val currentBalance: Int,
    val initialBalance: Int,
    val currencyId: String,
    val currencyCode: String? = null,
    val currencySymbol: String? = null,
    val color: String,
    val createdAt: String,
    val updatedAt: String,
    val goal: GoalDto? = null,
    val last10Transactions: List<TransactionDto> = emptyList(),
    val chartData: List<ChartPointDto> = emptyList()
)

@Serializable
data class MonthTransactionsResponse(
    val transactions: List<TransactionDto> = emptyList(),
    val initialBalances: Map<String, Int> = emptyMap()
)

@Serializable
data class NetWorthAccountDto(
    val accountId: String,
    val name: String,
    /** Cents. */
    val balance: Int
)

@Serializable
data class NetWorthSnapshotDto(
    val timestamp: String,
    /** Cents. */
    val totalBalance: Int,
    val accounts: List<NetWorthAccountDto> = emptyList()
)

@Serializable
data class NetWorthTrendPointDto(
    val date: String,
    /** Cents. */
    val netWorth: Int
)

@Serializable
data class NetWorthResponse(
    val current: NetWorthSnapshotDto? = null,
    val trend: List<NetWorthTrendPointDto> = emptyList()
)

@Serializable
data class CurrencyDto(
    val id: String,
    val code: String,
    val symbol: String,
    val name: String,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val defaultAccountId: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val sessionToken: String
)

@Serializable
data class CreateTransactionRequest(
    val accountId: String,
    val type: String,
    /** Major currency units, e.g. 12.50. */
    val amount: Double,
    val name: String? = null,
    val description: String? = null,
    val category: String? = null,
    val payee: String? = null,
    val toAccountId: String? = null,
    val exchangeRate: Double? = null,
    val createdAt: String? = null
)

@Serializable
data class ErrorResponse(
    val error: String? = null
)
