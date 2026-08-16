package com.gios.lighttip.data

/**
 * Names and minor-unit exponents for the currencies the rate feed returns.
 *
 * The exponent is not decoration. Yen has no subunit — ¥1,200 is twelve hundred yen, not
 * twelve — so a keypad that always pushes hundredths would have someone tip a hundredth
 * of what they meant. Dinars go the other way at three places. The list below is the ISO
 * 4217 exponent for every currency that is not the usual two, and the formatter and the
 * converter both read it.
 */
object Currencies {

    /** ISO 4217 currencies with no minor unit: the price *is* the whole number. */
    private val ZERO_DECIMAL = setOf(
        "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG",
        "RWF", "UGX", "UYI", "VND", "VUV", "XAF", "XOF", "XPF",
    )

    /** Three minor digits — the Gulf and North African dinars, plus the Tunisian one. */
    private val THREE_DECIMAL = setOf("BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND")

    fun exponent(code: String): Int = when (code.uppercase()) {
        in ZERO_DECIMAL -> 0
        in THREE_DECIMAL -> 3
        else -> 2
    }

    /** 10^exponent, as a Long — the number of minor units in one whole unit. */
    fun minorPerUnit(code: String): Long = when (exponent(code)) {
        0 -> 1L
        3 -> 1_000L
        else -> 100L
    }

    /**
     * Format minor units for display: "$1,234.56", "¥1,200", "1.234,000 KWD"-ish. The
     * code goes after the number for anything without a well-known one-glyph symbol,
     * which is most of them — an unfamiliar symbol on a greyscale panel is worse than
     * three plain letters.
     */
    fun format(minor: Long, code: String): String {
        val exp = exponent(code)
        val negative = minor < 0
        val magnitude = if (negative) -minor else minor
        val per = minorPerUnit(code)
        val whole = magnitude / per
        val frac = magnitude % per
        val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
        val body = if (exp == 0) grouped else "$grouped.${frac.toString().padStart(exp, '0')}"
        val symbol = SYMBOLS[code.uppercase()]
        val sign = if (negative) "-" else ""
        return if (symbol != null) "$sign$symbol$body" else "$sign$body $code"
    }

    /** The handful of symbols that are unambiguous enough to be worth showing. */
    private val SYMBOLS = mapOf(
        "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥",
        "CNY" to "¥", "KRW" to "₩", "INR" to "₹", "VND" to "₫",
        "THB" to "฿", "PHP" to "₱", "ILS" to "₪", "NGN" to "₦",
    )

    /**
     * Full names for the codes people actually reach for, so the picker can read
     * "JPY — Japanese Yen". Anything the feed returns that isn't here still lists, just
     * by code alone: a missing name should never hide a currency.
     */
    fun name(code: String): String? = NAMES[code.uppercase()]

    /** Offered first in the picker before any search, in rough order of who travels where. */
    val POPULAR = listOf("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "MXN", "INR")

    private val NAMES = mapOf(
        "AED" to "UAE Dirham", "ARS" to "Argentine Peso", "AUD" to "Australian Dollar",
        "BGN" to "Bulgarian Lev", "BHD" to "Bahraini Dinar", "BRL" to "Brazilian Real",
        "CAD" to "Canadian Dollar", "CHF" to "Swiss Franc", "CLP" to "Chilean Peso",
        "CNY" to "Chinese Yuan", "COP" to "Colombian Peso", "CZK" to "Czech Koruna",
        "DKK" to "Danish Krone", "EGP" to "Egyptian Pound", "EUR" to "Euro",
        "GBP" to "British Pound", "HKD" to "Hong Kong Dollar", "HRK" to "Croatian Kuna",
        "HUF" to "Hungarian Forint", "IDR" to "Indonesian Rupiah", "ILS" to "Israeli Shekel",
        "INR" to "Indian Rupee", "ISK" to "Icelandic Krona", "JPY" to "Japanese Yen",
        "KES" to "Kenyan Shilling", "KRW" to "South Korean Won", "KWD" to "Kuwaiti Dinar",
        "MAD" to "Moroccan Dirham", "MXN" to "Mexican Peso", "MYR" to "Malaysian Ringgit",
        "NGN" to "Nigerian Naira", "NOK" to "Norwegian Krone", "NZD" to "New Zealand Dollar",
        "PEN" to "Peruvian Sol", "PHP" to "Philippine Peso", "PKR" to "Pakistani Rupee",
        "PLN" to "Polish Zloty", "QAR" to "Qatari Riyal", "RON" to "Romanian Leu",
        "RSD" to "Serbian Dinar", "RUB" to "Russian Ruble", "SAR" to "Saudi Riyal",
        "SEK" to "Swedish Krona", "SGD" to "Singapore Dollar", "THB" to "Thai Baht",
        "TRY" to "Turkish Lira", "TWD" to "Taiwan Dollar", "TZS" to "Tanzanian Shilling",
        "UAH" to "Ukrainian Hryvnia", "USD" to "US Dollar", "UYU" to "Uruguayan Peso",
        "VND" to "Vietnamese Dong", "ZAR" to "South African Rand",
    )
}
