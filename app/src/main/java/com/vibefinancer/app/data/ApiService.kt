package com.vibefinancer.app.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("api/user")
    suspend fun me(): UserDto

    @GET("api/accounts")
    suspend fun accounts(): List<AccountDto>

    @GET("api/accounts/detailed")
    suspend fun detailedAccounts(
        @Query("month") month: Int,
        @Query("year") year: Int
    ): List<DetailedAccountDto>

    @GET("api/dashboard/summary")
    suspend fun dashboardSummary(
        @Query("month") month: Int,
        @Query("year") year: Int,
        @Query("tz") tz: String
    ): DashboardSummaryDto

    @GET("api/transactions")
    suspend fun monthTransactions(
        @Query("month") month: Int,
        @Query("year") year: Int,
        @Query("tz") tz: String,
        @Query("limit") limit: Int? = null
    ): MonthTransactionsResponse

    @GET("api/accounts/net-worth")
    suspend fun netWorth(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("interval") interval: Int = 1
    ): NetWorthResponse

    @GET("api/currencies")
    suspend fun currencies(): List<CurrencyDto>

    @POST("api/transactions")
    suspend fun createTransaction(@Body body: CreateTransactionRequest): TransactionDto
}
