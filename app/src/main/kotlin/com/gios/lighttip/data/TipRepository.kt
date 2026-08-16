package com.gios.lighttip.data

import android.content.Context
import com.gios.lighttip.ai.ReceiptParser
import com.gios.lighttip.util.ImageUtils
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class TipRepository(private val context: Context) {
    private val dao = TipDatabase.get(context).tipDao()
    private val prefs = context.getSharedPreferences("lighttip", Context.MODE_PRIVATE)
    private val receiptDir: File get() = File(context.filesDir, "receipts").apply { mkdirs() }

    fun observeReceipts(): Flow<List<ReceiptEntity>> = dao.observeReceipts()
    fun observeReceipt(id: String): Flow<ReceiptEntity?> = dao.observeReceipt(id)
    fun observeItems(receiptId: String): Flow<List<ItemEntity>> = dao.observeItems(receiptId)
    fun observePeople(receiptId: String): Flow<List<PersonEntity>> = dao.observePeople(receiptId)
    fun observeAssignments(receiptId: String): Flow<List<AssignmentEntity>> =
        dao.observeAssignments(receiptId)

    /**
     * The mode the app was last left in, by ordinal. Someone who only ever uses this as a
     * calculator should not have to re-pick it every launch.
     */
    fun lastMode(): Int = prefs.getInt("last_mode", 0)

    fun setLastMode(ordinal: Int) {
        prefs.edit().putInt("last_mode", ordinal).apply()
    }

    fun getApiKey(): String = prefs.getString("api_key", "").orEmpty()
    fun setApiKey(key: String) {
        prefs.edit().putString("api_key", key.trim()).apply()
    }

    /** Names persist across bills, so the usual crowd is one tap away on the next receipt. */
    fun recentNames(): List<String> =
        prefs.getString("recent_names", "").orEmpty()
            .split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    private fun rememberName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val next = (listOf(trimmed) + recentNames().filterNot { it.equals(trimmed, true) }).take(12)
        prefs.edit().putString("recent_names", next.joinToString("\n")).apply()
    }

    fun newCaptureFile(): File = File(receiptDir, "cap_${UUID.randomUUID()}.jpg")

    suspend fun addFromFile(file: File): String =
        addBytes(file.readBytes()).also { runCatching { file.delete() } }

    /**
     * Stores the photo and asks Haiku what is on it. The row is written before the
     * network call so the list has something to show while the read is in flight.
     */
    private suspend fun addBytes(bytes: ByteArray): String {
        val id = UUID.randomUUID().toString()
        val upright = ImageUtils.normalizeUpright(bytes)
        val scaled = ImageUtils.downscaled(upright)
        val image = ImageUtils.saveJpeg(scaled, File(receiptDir, "$id.jpg"))
        if (scaled !== upright) scaled.recycle()
        upright.recycle()

        val apiKey = getApiKey()
        dao.putReceipt(
            ReceiptEntity(
                id = id,
                merchant = if (apiKey.isBlank()) "No API key" else "Reading…",
                subtotalCents = 0,
                taxCents = 0,
                totalCents = 0,
                imagePath = image.absolutePath,
                status = if (apiKey.isBlank()) {
                    ReceiptEntity.STATUS_NO_KEY
                } else {
                    ReceiptEntity.STATUS_READING
                },
            ),
        )
        if (apiKey.isNotBlank()) applyParse(id, image, apiKey)
        return id
    }

    /** Retry a bill that failed, or one captured before a key was set. */
    suspend fun rescan(receiptId: String) {
        val receipt = dao.getReceipt(receiptId) ?: return
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return
        val image = File(receipt.imagePath)
        if (!image.exists()) return
        dao.putReceipt(receipt.copy(merchant = "Reading…", status = ReceiptEntity.STATUS_READING))
        dao.deleteAssignmentsForReceipt(receiptId)
        dao.deleteItems(receiptId)
        applyParse(receiptId, image, apiKey)
    }

    private suspend fun applyParse(receiptId: String, image: File, apiKey: String) {
        val current = dao.getReceipt(receiptId) ?: return
        val parsed = runCatching { ReceiptParser.parse(image, apiKey) }.getOrNull()
        if (parsed == null || parsed.notAReceipt) {
            dao.putReceipt(
                current.copy(
                    merchant = if (parsed == null) "Couldn't read" else "Not a receipt",
                    status = ReceiptEntity.STATUS_FAILED,
                ),
            )
            return
        }
        dao.putReceipt(
            current.copy(
                merchant = parsed.merchant,
                subtotalCents = parsed.subtotalCents,
                taxCents = parsed.taxCents,
                totalCents = parsed.totalCents,
                status = ReceiptEntity.STATUS_READY,
            ),
        )
        dao.putItems(
            parsed.items.mapIndexed { index, item ->
                ItemEntity(
                    id = UUID.randomUUID().toString(),
                    receiptId = receiptId,
                    name = item.name,
                    priceCents = item.priceCents,
                    position = index,
                )
            },
        )
    }

    suspend fun setTipPercent(receiptId: String, percent: Int) =
        dao.setTipPercent(receiptId, percent)

    suspend fun addPerson(receiptId: String, name: String) {
        val position = dao.countPeople(receiptId)
        val resolved = name.trim().ifBlank { "Person ${position + 1}" }
        rememberName(resolved)
        dao.putPerson(
            PersonEntity(
                id = UUID.randomUUID().toString(),
                receiptId = receiptId,
                name = resolved,
                position = position,
            ),
        )
    }

    suspend fun renamePerson(person: PersonEntity, name: String) {
        val resolved = name.trim().ifBlank { person.name }
        rememberName(resolved)
        dao.putPerson(person.copy(name = resolved))
    }

    suspend fun deletePerson(person: PersonEntity) {
        dao.deleteAssignmentsForPerson(person.id)
        dao.deletePerson(person.id)
    }

    suspend fun setAssigned(receiptId: String, itemId: String, personId: String, assigned: Boolean) {
        if (assigned) {
            dao.putAssignment(AssignmentEntity(itemId, personId, receiptId))
        } else {
            dao.deleteAssignment(itemId, personId)
        }
    }

    suspend fun clearAssignments(receiptId: String) = dao.deleteAssignmentsForReceipt(receiptId)

    suspend fun deleteReceipt(receipt: ReceiptEntity) {
        runCatching { File(receipt.imagePath).delete() }
        dao.deleteAssignmentsForReceipt(receipt.id)
        dao.deleteItems(receipt.id)
        dao.deletePeople(receipt.id)
        dao.deleteReceipt(receipt.id)
    }
}
