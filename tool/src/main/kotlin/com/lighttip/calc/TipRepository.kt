package com.lighttip.calc

import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

private const val KEY_API = "anthropic_api_key"

class TipRepository(
    private val dao: TipDao,
    private val lightContext: SealedLightContext,
) {
    private val parser = ReceiptParser()

    private val receiptDir: File
        get() = File(lightContext.filesDir, "receipts").apply { mkdirs() }

    fun observeReceipts(): Flow<List<ReceiptEntity>> = dao.observeReceipts()
    fun observeReceipt(id: String): Flow<ReceiptEntity?> = dao.observeReceipt(id)
    fun observeItems(receiptId: String): Flow<List<ItemEntity>> = dao.observeItems(receiptId)
    fun observePeople(receiptId: String): Flow<List<PersonEntity>> = dao.observePeople(receiptId)
    fun observeAssignments(receiptId: String): Flow<List<AssignmentEntity>> =
        dao.observeAssignments(receiptId)

    suspend fun getApiKey(): String = dao.getMetadata(KEY_API).orEmpty()
    suspend fun setApiKey(key: String) = dao.putMetadata(AppMetadataEntity(KEY_API, key.trim()))

    fun newCaptureFile(): File = File(receiptDir, "cap_${UUID.randomUUID()}.jpg")

    suspend fun addReceiptFromFile(file: File): String {
        val id = addReceipt(file.readBytes())
        runCatching { file.delete() }
        return id
    }

    /**
     * Stores the photo, then asks Haiku what is on it. The row lands in the list
     * immediately with status "reading" so the UI has something to show while we wait.
     */
    suspend fun addReceipt(bytes: ByteArray): String {
        val id = UUID.randomUUID().toString()
        val image = File(receiptDir, "$id.jpg")
        image.writeBytes(bytes)

        val apiKey = getApiKey()
        dao.putReceipt(
            ReceiptEntity(
                id = id,
                merchant = "Reading…",
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
        if (apiKey.isBlank()) {
            dao.putReceipt(dao.getReceipt(id)!!.copy(merchant = "No API key"))
            return id
        }

        val parsed = runCatching { parser.parse(image, apiKey) }.getOrNull()
        val current = dao.getReceipt(id) ?: return id
        if (parsed == null || parsed.notAReceipt) {
            dao.putReceipt(
                current.copy(
                    merchant = if (parsed == null) "Couldn't read" else "Not a receipt",
                    status = ReceiptEntity.STATUS_FAILED,
                ),
            )
            return id
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
                    receiptId = id,
                    name = item.name,
                    priceCents = item.priceCents,
                    position = index,
                )
            },
        )
        return id
    }

    /** Retry a bill that failed or was captured before a key was set. */
    suspend fun rescan(receiptId: String) {
        val receipt = dao.getReceipt(receiptId) ?: return
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return
        val image = File(receipt.imagePath)
        if (!image.exists()) return

        dao.putReceipt(receipt.copy(merchant = "Reading…", status = ReceiptEntity.STATUS_READING))
        val parsed = runCatching { parser.parse(image, apiKey) }.getOrNull()
        if (parsed == null || parsed.notAReceipt) {
            dao.putReceipt(
                receipt.copy(
                    merchant = if (parsed == null) "Couldn't read" else "Not a receipt",
                    status = ReceiptEntity.STATUS_FAILED,
                ),
            )
            return
        }
        dao.deleteAssignmentsForReceipt(receiptId)
        dao.deleteItems(receiptId)
        dao.putReceipt(
            receipt.copy(
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
        dao.putPerson(
            PersonEntity(
                id = UUID.randomUUID().toString(),
                receiptId = receiptId,
                name = name.trim().ifBlank { "Person ${position + 1}" },
                position = position,
            ),
        )
    }

    suspend fun renamePerson(person: PersonEntity, name: String) =
        dao.putPerson(person.copy(name = name.trim().ifBlank { person.name }))

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

    suspend fun clearAssignments(receiptId: String) =
        dao.deleteAssignmentsForReceipt(receiptId)

    suspend fun deleteReceipt(receipt: ReceiptEntity) {
        runCatching { File(receipt.imagePath).delete() }
        dao.deleteAssignmentsForReceipt(receipt.id)
        dao.deleteItems(receipt.id)
        dao.deletePeople(receipt.id)
        dao.deleteReceipt(receipt.id)
    }

    fun close() = parser.close()
}
