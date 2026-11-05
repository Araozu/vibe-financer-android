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

    /** "accountName|balanceCents|symbol|updatedAt" cached for the widget. */
    suspend fun widgetCache(): String? =
        context.prefs.data.map { it[Keys.WIDGET_CACHE] }.first()

    suspend fun setWidgetCache(value: String) {
        context.prefs.edit { it[Keys.WIDGET_CACHE] = value }
    }

    companion object {
        /** Emulator loopback to the host machine. */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:5124"
    }
}
