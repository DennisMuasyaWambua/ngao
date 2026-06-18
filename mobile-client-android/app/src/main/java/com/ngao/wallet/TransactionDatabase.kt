package com.ngao.wallet

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.UUID

/**
 * TransactionDatabase.kt
 * ======================
 * Local, **offline-first** storage for the wallet. The device — not the network —
 * is the source of truth for the user's *intent* to pay.
 *
 * Every payment is written here immediately (status = PENDING) so the app works
 * with zero connectivity. [NetworkSyncWorker] later drains this "outbox" to the
 * backend via [WalletApiService] when the network returns. The locally generated
 * [OutboxTransaction.idempotencyKey] is what guarantees a queued payment is
 * applied exactly once, no matter how many times it is re-sent.
 */

/** A single queued payment — the classic client-side "outbox" pattern. */
@Entity(tableName = "outbox_transactions")
data class OutboxTransaction(
    @PrimaryKey
    val idempotencyKey: String = UUID.randomUUID().toString(), // sent as X-Idempotency-Key
    val fromAccount: String,
    val toAccount: String,
    val amount: Double,
    val currency: String,
    val narrative: String?,
    val status: String = STATUS_PENDING,        // PENDING -> SYNCED / FAILED
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_FAILED = "FAILED"
    }
}

/** Data-access object over the outbox table. */
@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: OutboxTransaction)

    @Query("SELECT * FROM outbox_transactions WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun pendingTransactions(): List<OutboxTransaction>

    @Query("UPDATE outbox_transactions SET status = :status WHERE idempotencyKey = :key")
    suspend fun updateStatus(key: String, status: String)
}

/** Room database holding the offline outbox. Exposed as a process-wide singleton. */
@Database(entities = [OutboxTransaction::class], version = 1, exportSchema = false)
abstract class TransactionDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: TransactionDatabase? = null

        fun getInstance(context: Context): TransactionDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TransactionDatabase::class.java,
                    "ngao-wallet.db"
                ).build().also { INSTANCE = it }
            }
    }
}
