package com.vibefinancer.app.data

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import java.net.UnknownHostException

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val message: String, val unauthorized: Boolean = false) : ApiResult<Nothing>
}

class FinancerRepository(private val store: SessionStore) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private suspend fun api(): ApiService {
        val baseUrl = store.baseUrl()
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val token = kotlinx.coroutines.runBlocking { store.token() }
                val req = if (token != null) {
                    chain.request().newBuilder()
                        .header("Cookie", "session=$token")
                        .build()
                } else chain.request()
                chain.proceed(req)
            })
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            .build()
        return Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    private suspend fun <T> call(block: suspend ApiService.() -> T): ApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                ApiResult.Ok(api().block())
            } catch (e: HttpException) {
                val msg = try {
                    e.response()?.errorBody()?.string()?.let {
                        runCatching { json.decodeFromString<ErrorResponse>(it).error }.getOrNull()
                    }
                } catch (_: Exception) { null } ?: "Request failed (${e.code()})"
                ApiResult.Err(msg, unauthorized = e.code() == 401)
            } catch (e: UnknownHostException) {
                ApiResult.Err("Cannot reach server. Check the server URL in settings.")
            } catch (e: IOException) {
                ApiResult.Err("Network error: ${e.message}")
            } catch (e: Exception) {
                ApiResult.Err(e.message ?: "Unexpected error")
            }
        }

    suspend fun login(baseUrl: String, email: String, password: String): ApiResult<String> {
        store.setBaseUrl(baseUrl)
        store.setToken(null)
        // Login call goes to the freshly set base URL without a token.
        val res = call { login(LoginRequest(email.trim(), password)) }
        return when (res) {
            is ApiResult.Ok -> {
                store.setToken(res.value.sessionToken)
                ApiResult.Ok(res.value.sessionToken)
            }
            is ApiResult.Err -> res
        }
    }

    suspend fun logout() = store.setToken(null)

    suspend fun detailedAccounts(month: Int, year: Int) =
        call { detailedAccounts(month, year) }

    suspend fun monthTransactions(month: Int, year: Int, tz: String, limit: Int? = null) =
        call { monthTransactions(month, year, tz, limit) }

    suspend fun netWorthTrend(startIso: String, endIso: String, interval: Int = 1) =
        call { netWorth(startIso, endIso, interval) }

    suspend fun createTransaction(body: CreateTransactionRequest) =
        call { createTransaction(body) }
}
