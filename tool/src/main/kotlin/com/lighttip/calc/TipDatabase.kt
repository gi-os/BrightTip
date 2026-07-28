package com.lighttip.calc

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One photographed bill. All money is stored in cents to keep the arithmetic exact. */
@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val merchant: String,
    val subtotalCents: Long,
    val taxCents: Long,
    val totalCents: Long,
    val tipPercent: Int = 20,
    val imagePath: String,
    val status: String = STATUS_READY,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_READING = "reading"
        const val STATUS_READY = "ready"
        const val STATUS_FAILED = "failed"
        const val STATUS_NO_KEY = "no_key"
    }
}

/** A single line on the bill. Quantities are expanded, so 2x Beer becomes two rows. */
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val receiptId: String,
    val name: String,
    val priceCents: Long,
    val position: Int,
)

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val receiptId: String,
    val name: String,
    val position: Int,
)

/** Join row: this person is on the hook for part of this item. */
@Entity(tableName = "assignments", primaryKeys = ["itemId", "personId"])
data class AssignmentEntity(
    val itemId: String,
    val personId: String,
    val receiptId: String,
)

@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface TipDao {
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    fun observeReceipts(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE id = :id LIMIT 1")
    fun observeReceipt(id: String): Flow<ReceiptEntity?>

    @Query("SELECT * FROM receipts WHERE id = :id LIMIT 1")
    suspend fun getReceipt(id: String): ReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putReceipt(receipt: ReceiptEntity)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteReceipt(id: String)

    @Query("UPDATE receipts SET tipPercent = :percent WHERE id = :id")
    suspend fun setTipPercent(id: String, percent: Int)

    @Query("SELECT * FROM items WHERE receiptId = :receiptId ORDER BY position ASC")
    fun observeItems(receiptId: String): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putItems(items: List<ItemEntity>)

    @Query("DELETE FROM items WHERE receiptId = :receiptId")
    suspend fun deleteItems(receiptId: String)

    @Query("SELECT * FROM people WHERE receiptId = :receiptId ORDER BY position ASC")
    fun observePeople(receiptId: String): Flow<List<PersonEntity>>

    @Query("SELECT COUNT(*) FROM people WHERE receiptId = :receiptId")
    suspend fun countPeople(receiptId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPerson(person: PersonEntity)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deletePerson(id: String)

    @Query("DELETE FROM people WHERE receiptId = :receiptId")
    suspend fun deletePeople(receiptId: String)

    @Query("SELECT * FROM assignments WHERE receiptId = :receiptId")
    fun observeAssignments(receiptId: String): Flow<List<AssignmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAssignment(assignment: AssignmentEntity)

    @Query("DELETE FROM assignments WHERE itemId = :itemId AND personId = :personId")
    suspend fun deleteAssignment(itemId: String, personId: String)

    @Query("DELETE FROM assignments WHERE personId = :personId")
    suspend fun deleteAssignmentsForPerson(personId: String)

    @Query("DELETE FROM assignments WHERE receiptId = :receiptId")
    suspend fun deleteAssignmentsForReceipt(receiptId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMetadata(metadata: AppMetadataEntity)

    @Query("SELECT value FROM app_metadata WHERE `key` = :key LIMIT 1")
    suspend fun getMetadata(key: String): String?
}

@Database(
    entities = [
        ReceiptEntity::class,
        ItemEntity::class,
        PersonEntity::class,
        AssignmentEntity::class,
        AppMetadataEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TipDatabase : RoomDatabase() {
    abstract fun tipDao(): TipDao
}
