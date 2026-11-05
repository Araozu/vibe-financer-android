package com.vibefinancer.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.prefs by preferencesDataStore(name = "vibefinancer")

/** Simple key-value session + settings store shared by the app and the widget. */
class SessionStore(private val context: Context) {

    /** One-shot read of every key. Prefer this over individual reads on hot paths
     * (widget render, deep-link routing): a single DataStore collection instead
     * of one per key. */
    data class Snapshot(
        val baseUrl: String,
        val token: String?,
        val widgetAccountId: String?,
        val widgetCacheRaw: String?
    )

    suspend fun snapshot(): Snapshot {
        val prefs = context.prefs.data.first()
        return Snapshot(
            baseUrl = prefs[Keys.BASE_URL] ?: DEFAULT_BASE_URL,
            token = prefs[Keys.TOKEN],
            widgetAccountId = prefs[Keys.WIDGET_ACCOUNT_ID],
            widgetCacheRaw = prefs[Keys.WIDGET_CACHE]
        )
    }

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("session_token")
        val WIDGET_ACCOUNT_ID = stringPreferencesKey("widget_account_id")
        val WIDGET_CACHE = stringPreferencesKey("widget_cache")
    }

    suspend fun baseUrl(): String =
        context.prefs.data.map { it[Keys.BASE_URL] ?: DEFAULT_BASE_URL }.first()

    suspend fun setBaseUrl(url: String) {
        context.prefs.edit { it[Keys.BASE_URL] = url.trimEnd('/') }
    }

    suspend fun token(): String? =
        context.prefs.data.map { it[Keys.TOKEN] }.first()

    suspend fun setToken(token: String?) {
        context.prefs.edit {
            if (token == null) it.remove(Keys.TOKEN) else it[Keys.TOKEN] = token
        }
    }

    suspend fun widgetAccountId(): String? =
        context.prefs.data.map { it[Keys.WIDGET_ACCOUNT_ID] }.first()

    suspend fun setWidgetAccountId(id: String?) {
        context.prefs.edit {
            if (id == null) it.remove(Keys.WIDGET_ACCOUNT_ID) else it[Keys.WIDGET_ACCOUNT_ID] = id
        }
    }

    /** "v2|accountId|accountName|balanceCents|symbol" cached for the widget.
     * Name sits in the middle so it may contain "|" without breaking parsing.
     * Legacy "accountName|balanceCents|symbol|..." rows are still accepted. */
    suspend fun widgetCache(): String? =
        context.prefs.data.map { it[Keys.WIDGET_CACHE] }.first()

    suspend fun setWidgetCache(value: String) {
        context.prefs.edit { it[Keys.WIDGET_CACHE] = value }
    }

    suspend fun setWidgetCacheV2(accountId: String, accountName: String, balanceCents: Int, symbol: String) {
        setWidgetCache("v2|$accountId|$accountName|$balanceCents|$symbol")
    }

    companion object {
        /** Emulator loopback to the host machine. */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:5124"
    }
}

/** Parsed widget cache: the main (widget) account's id, display name and balance. */
data class WidgetCache(
    val accountId: String?,
    val accountName: String,
    val balanceCents: Long,
    val symbol: String
)

/**
 * Parse a raw widget cache string. Accepts v2 rows
 * ("v2|accountId|accountName|balanceCents|symbol", name may contain "|") and the
 * legacy "accountName|balanceCents|symbol|..." layout. Null on garbage.
 */
fun parseWidgetCache(raw: String?): WidgetCache? {
    if (raw.isNullOrEmpty()) return null
    if (raw.startsWith("v2|")) {
        val body = raw.removePrefix("v2|")
        val firstSep = body.indexOf('|')
        if (firstSep < 0) return null
        val accountId = body.substring(0, firstSep).ifBlank { null }
        val rest = body.substring(firstSep + 1)
        // Balance is the last numeric segment; symbol is whatever follows it.
        val segs = rest.split('|')
        var balanceIdx = -1
        for (i in segs.indices.reversed()) {
            if (segs[i].toLongOrNull() != null) {
                balanceIdx = i
                break
            }
        }
        if (balanceIdx < 0) return null
        val name = segs.subList(0, balanceIdx).joinToString("|").ifBlank { return null }
        val symbol = segs.subList(balanceIdx + 1, segs.size).joinToString("|")
        return WidgetCache(accountId, name, segs[balanceIdx].toLong(), symbol)
    }
    val parts = raw.split('|')
    if (parts.size < 3) return null
    return WidgetCache(
        accountId = null,
        accountName = parts[0].ifBlank { return null },
        balanceCents = parts[1].toLongOrNull() ?: return null,
        symbol = parts[2]
    )
}
