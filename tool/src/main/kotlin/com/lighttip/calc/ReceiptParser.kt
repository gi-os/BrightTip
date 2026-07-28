package com.lighttip.calc

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ParsedItem(
    val name: String,
    val priceCents: Long,
)

data class ParsedReceipt(
    val merchant: String,
    val items: List<ParsedItem>,
    val subtotalCents: Long,
    val taxCents: Long,
    val totalCents: Long,
    val notAReceipt: Boolean = false,
)

/** Reads a photographed bill with Claude Haiku vision. Same key + wire format as LightPass. */
class ReceiptParser {
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 50_000
        }
    }

    private val prompt = """
        Read this photo of a restaurant or store receipt and extract every line item.
        Return ONLY valid JSON, no markdown, no backticks, no explanation.

        {
          "merchant": "business name printed at the top, or null",
          "items": [
            {"name": "item as printed, trimmed", "price": 12.34, "qty": 1}
          ],
          "subtotal": 42.00,
          "tax": 3.73,
          "total": 45.73
        }

        Rules:
        - One entry per printed line item. Keep the order they appear on the receipt.
        - price is the line total for that line, as a number, no currency symbol.
        - qty is the printed quantity for that line, default 1.
        - Do NOT include subtotal, tax, tip, total, discounts or payment lines in "items".
        - Include modifiers priced separately as their own item.
        - If subtotal, tax or total is not printed, infer it from the items and use 0 for tax.
        - If this is not a receipt or no line items are legible, return {"error": "not_a_receipt"}.
    """.trimIndent()

    suspend fun parse(imageFile: File, apiKey: String, model: String = DEFAULT_MODEL): ParsedReceipt {
        var lastError: Exception? = null
        repeat(MAX_RETRIES) {
            try {
                return requestOnce(imageFile, apiKey, model)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("receipt parse failed")
    }

    private suspend fun requestOnce(imageFile: File, apiKey: String, model: String): ParsedReceipt {
        val b64 = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("model", model)
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

        val raw = client.post(API_URL) {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()

        val text = JSONObject(raw).getJSONArray("content").getJSONObject(0).getString("text")
        val json = JSONObject(text.substringAfter('{').substringBeforeLast('}').let { "{$it}" })

        if (json.optString("error").isNotBlank()) {
            return ParsedReceipt("", emptyList(), 0, 0, 0, notAReceipt = true)
        }

        val items = mutableListOf<ParsedItem>()
        val array = json.optJSONArray("items") ?: JSONArray()
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val name = entry.optString("name").trim().ifBlank { "Item ${i + 1}" }
            val lineCents = entry.optDouble("price", 0.0).toCents()
            if (lineCents <= 0L) continue
            // Expand quantities so two people can each claim one of the same thing.
            val qty = entry.optInt("qty", 1).coerceIn(1, 20)
            val parts = evenlyDivide(lineCents, qty)
            repeat(qty) { q ->
                items.add(ParsedItem(name, parts[q]))
            }
        }
        if (items.isEmpty()) {
            return ParsedReceipt("", emptyList(), 0, 0, 0, notAReceipt = true)
        }

        val itemsSum = items.sumOf { it.priceCents }
        val subtotal = json.optDouble("subtotal", 0.0).toCents().takeIf { it > 0L } ?: itemsSum
        val tax = json.optDouble("tax", 0.0).toCents().coerceAtLeast(0L)
        val total = json.optDouble("total", 0.0).toCents().takeIf { it > 0L } ?: (subtotal + tax)

        return ParsedReceipt(
            merchant = json.optString("merchant").trim().ifBlank { "Receipt" },
            items = items,
            subtotalCents = subtotal,
            taxCents = tax,
            totalCents = total,
        )
    }

    fun close() = client.close()

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val MAX_RETRIES = 2
        const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"
    }
}
