package com.vibefinancer.app.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** Amounts from the API are integer cents. */
fun formatMoney(cents: Int, currencyCode: String?, symbol: String?): String {
    val major = cents / 100.0
    return try {
        val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
        if (currencyCode != null) nf.currency = Currency.getInstance(currencyCode)
        var out = nf.format(major)
        if (symbol != null && currencyCode == null) out = "$symbol$out"
        out
    } catch (_: Exception) {
        "${symbol ?: (currencyCode?.plus(" ") ?: "")}$major"
    }
}

fun parseInstant(iso: String): Instant = Instant.parse(iso)

fun formatTxDate(iso: String, zone: ZoneId = ZoneId.systemDefault()): String {
    return try {
        DateTimeFormatter.ofPattern("d MMM, HH:mm")
            .format(parseInstant(iso).atZone(zone))
    } catch (_: Exception) { iso }
}

fun monthLabel(month0: Int, year: Int): String {
    val m = Month.of(month0 + 1).getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    return "$m $year"
}

fun todayIso(): LocalDate = LocalDate.now()
