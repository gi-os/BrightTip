package com.gios.lighttip.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

/**
 * Exchange rates from open.er-api.com — no key, no account, ~160 currencies, refreshed
 * once a day at the source.
 *
 * The whole table is cached to prefs on every successful fetch, because the moment you
 * actually need this screen is the moment you are standing in a shop abroad with no data
 * plan. Offline is the normal case, not the error case: the converter always works, and
 * the UI says how old the numbers are so the person can decide whether that matters.
 */
class RateRepository(context: Context) {

    private val prefs = context.getSharedPreferences("lighttip", Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * @property rates units of each currency per one USD. Everything is stored against
     *   USD and cross-rated on demand, so one fetch covers every pair rather than one
     *   fetch per pair the person happens to pick.
     * @property fetchedAtMillis when this device pulled the table, not when the source
     *   published it — it is the honest answer to "how stale is this?".
     */
    data class RateTable(
        val rates: Map<String, BigDecimal>,
        val fetchedAtMillis: Long,
        val sourceDate: String,
    ) {
        fun has(code: String): Boolean = rates.containsKey(code)
    }

    fun cached(): RateTable? {
        val blob = prefs.getString(KEY_RATES, null)?.takeIf { it.isNotBlank() } ?: return null
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        val sourceDate = prefs.getString(KEY_SOURCE_DATE, "").orEmpty()
        val parsed = runCatching { parseCsv(blob) }.getOrNull() ?: return null
        if (parsed.isEmpty()) return null
        return RateTable(parsed, fetchedAt, sourceDate)
    }

    /**
     * Blocking — call it off the main thread. Returns null on any failure at all, which
     * the caller reads as "keep using the cache": a converter that goes blank because a
     * captive-portal wifi returned HTML is worse than one showing yesterday's rate.
     */
    fun fetch(): RateTable? = runCatching {
        val request = Request.Builder()
            .url("https://open.er-api.com/v6/latest/USD")
            .header("Accept", "application/json")
            .build()

        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            response.body?.string()
        } ?: return@runCatching null

        val json = JSONObject(body)
        if (json.optString("result") != "success") return@runCatching null
        val ratesJson = json.optJSONObject("rates") ?: return@runCatching null

        val parsed = buildMap<String, BigDecimal> {
            for (code in ratesJson.keys()) {
                // Read as a string, never as a double: the response carries rates like
                // 0.86453, and going through binary float loses digits before we ever
                // start dividing by them.
                val raw = ratesJson.get(code).toString()
                val value = raw.toBigDecimalOrNull() ?: continue
                if (value.signum() > 0) put(code.uppercase(), value)
            }
        }
        if (parsed.isEmpty()) return@runCatching null

        val sourceDate = json.optString("time_last_update_utc").take(16).trim()
        val now = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_RATES, toCsv(parsed))
            .putLong(KEY_FETCHED_AT, now)
            .putString(KEY_SOURCE_DATE, sourceDate)
            .apply()

        RateTable(parsed, now, sourceDate)
    }.getOrNull()

    /** The pair the person last used, so the screen opens where they left it. */
    fun lastPair(): Pair<String, String> = Pair(
        prefs.getString(KEY_FROM, null) ?: "USD",
        prefs.getString(KEY_TO, null) ?: "EUR",
    )

    fun setLastPair(from: String, to: String) {
        prefs.edit().putString(KEY_FROM, from).putString(KEY_TO, to).apply()
    }

    /* --------------------------------------------------------- cache format */

    // One line per currency rather than JSON: it is a flat map of code to decimal string,
    // and a hand-rolled two-field split has no parser to go wrong on 160 rows.
    private fun toCsv(rates: Map<String, BigDecimal>): String =
        rates.entries.joinToString("\n") { "${it.key}=${it.value.toPlainString()}" }

    private fun parseCsv(blob: String): Map<String, BigDecimal> = buildMap {
        for (line in blob.lineSequence()) {
            val code = line.substringBefore('=', "").trim()
            val value = line.substringAfter('=', "").trim().toBigDecimalOrNull() ?: continue
            if (code.isNotEmpty() && value.signum() > 0) put(code, value)
        }
    }

    private companion object {
        const val KEY_RATES = "fx_rates"
        const val KEY_FETCHED_AT = "fx_fetched_at"
        const val KEY_SOURCE_DATE = "fx_source_date"
        const val KEY_FROM = "fx_from"
        const val KEY_TO = "fx_to"
    }
}

/**
 * Convert an amount held in [from]'s minor units into [to]'s minor units, cross-rated
 * through USD.
 *
 * Two things this has to get right that a naive multiply does not. The exponents differ —
 * 1000 minor units is $10.00 but ¥1000 — so the amount is taken up to whole units before
 * the rate is applied and back down afterwards. And sixteen significant digits carry the
 * intermediate, so the divide and the multiply don't each shed a digit off the tail of a
 * currency that runs to five figures per dollar. Rounding happens exactly once, at the end.
 */
fun convertMinor(
    amountMinor: Long,
    from: String,
    to: String,
    rates: Map<String, BigDecimal>,
): Long? {
    if (from == to) return amountMinor
    val fromRate = rates[from] ?: return null
    val toRate = rates[to] ?: return null
    if (fromRate.signum() <= 0) return null
    val mc = MathContext(16, RoundingMode.HALF_UP)
    val fromPer = BigDecimal(Currencies.minorPerUnit(from))
    val toPer = BigDecimal(Currencies.minorPerUnit(to))
    return BigDecimal(amountMinor)
        .divide(fromPer, mc)      // minor units -> whole units of `from`
        .divide(fromRate, mc)     // -> USD
        .multiply(toRate, mc)     // -> whole units of `to`
        .multiply(toPer, mc)      // -> minor units of `to`
        .setScale(0, RoundingMode.HALF_UP)
        .toBigInteger()
        .toLong()
}

/** "1 USD = 0.8645 EUR", the line under the readout. Four decimals reads at a glance. */
fun unitRateText(from: String, to: String, rates: Map<String, BigDecimal>): String? {
    val fromRate = rates[from] ?: return null
    val toRate = rates[to] ?: return null
    if (fromRate.signum() <= 0) return null
    val unit = toRate.divide(fromRate, MathContext(10, RoundingMode.HALF_UP))
        .setScale(4, RoundingMode.HALF_UP)
        .stripTrailingZeros()
    val shown = if (unit.scale() < 0) unit.setScale(0) else unit
    return "1 $from = ${shown.toPlainString()} $to"
}
