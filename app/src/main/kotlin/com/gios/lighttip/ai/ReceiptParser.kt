package com.gios.lighttip.ai

import android.util.Base64
import com.gios.lighttip.util.evenlyDivide
import com.gios.lighttip.util.toCents
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class ParsedItem(val name: String, val priceCents: Long)

data class ParsedReceipt(
    val merchant: String,
    val items: List<ParsedItem>,
    val subtotalCents: Long,
    val taxCents: Long,
    val totalCents: Long,
    val notAReceipt: Boolean = false,
)

/** Reads a photographed bill with Claude Haiku vision, using the user's own key. */
object ReceiptParser {
    private const val API_URL = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-haiku-4-5-20251001"
    private const val MAX_RETRIES = 2

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val prompt = """
        Read this photo of a restaurant or store receipt and extract every line item.
        Return ONLY valid JSON, no markdown, no backticks, no explanation.
        {
          "merchant": "business name printed at the top, or null",
          "items": [ {"name": "item as printed, trimmed", "price": 12.34, "qty": 1} ],
          "subtotal": 42.00,
          "tax": 3.73,
          "total": 45.73
        }
        Rules:
        - One entry per printed line item, in the order they appear.
        - price is that line's total as a number, no currency symbol.
        - qty is the printed quantity for the line, default 1.
        - Do NOT put subtotal, tax, tip, total, discount or payment lines in "items".
        - Priced modifiers and add-ons are their own items.
        - If subtotal, tax or total is missing, infer from the items and use 0 for tax.
        - If this is not a receipt, or no line items are legible,
          return {"error":"not_a_receipt"}.
    """.trimIndent()

    fun parse(imageFile: File, apiKey: String): ParsedReceipt {
        var last: Exception? = null
        repeat(MAX_RETRIES) {
            try {
                return requestOnce(imageFile, apiKey)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("receipt parse failed")
    }

    private fun requestOnce(imageFile: File, apiKey: String): ParsedReceipt {
        val b64 = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 2000)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            JSONArray()
                                .put(
                                    JSONObject().apply {
                                        put("type", "image")
                                        put(
                                            "source",
                                            JSONObject().apply {
                                                put("type", "base64")
                                                put("media_type", "image/jpeg")
                                                put("data", b64)
                                            },
                                        )
                                    },
                                )
                                .put(JSONObject().apply { put("type", "text"); put("text", prompt) }),
                        )
                    },
                ),
            )
        }.toString()

        val req = Request.Builder()
            .url(API_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val text = JSONObject(raw).getJSONArray("content").getJSONObject(0).getString("text")
            val j = JSONObject(text.substringAfter('{').substringBeforeLast('}').let { "{$it}" })
            if (j.optString("error").isNotBlank()) return notAReceipt()

            val items = mutableListOf<ParsedItem>()
            val array = j.optJSONArray("items") ?: JSONArray()
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val name = entry.optString("name").trim().ifBlank { "Item ${i + 1}" }
                val lineCents = entry.optDouble("price", 0.0).toCents()
                if (lineCents <= 0L) continue
                // Expand quantities so two people can each claim one of the same thing.
                val qty = entry.optInt("qty", 1).coerceIn(1, 20)
                val parts = evenlyDivide(lineCents, qty)
                repeat(qty) { q -> items.add(ParsedItem(name, parts[q])) }
            }
            if (items.isEmpty()) return notAReceipt()

            val itemsSum = items.sumOf { it.priceCents }
            val subtotal = j.optDouble("subtotal", 0.0).toCents().takeIf { it > 0L } ?: itemsSum
            val tax = j.optDouble("tax", 0.0).toCents().coerceAtLeast(0L)
            val total = j.optDouble("total", 0.0).toCents().takeIf { it > 0L } ?: (subtotal + tax)

            return ParsedReceipt(
                merchant = j.optString("merchant").trim().ifBlank { "Receipt" },
                items = items,
                subtotalCents = subtotal,
                taxCents = tax,
                totalCents = total,
            )
        }
    }

    private fun notAReceipt() = ParsedReceipt("", emptyList(), 0, 0, 0, notAReceipt = true)
}
