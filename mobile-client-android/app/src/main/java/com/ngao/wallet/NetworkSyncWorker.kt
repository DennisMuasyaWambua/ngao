package com.ngao.wallet

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * NetworkSyncWorker.kt
 * ====================
 * The background sync engine that makes the wallet "offline-resilient":
 * **capture now, sync eventually.**
 *
 * WorkManager runs this (subject to the CONNECTED network constraint) to drain the
 * local Room outbox: each PENDING [OutboxTransaction] is POSTed to the API Gateway
 * via [WalletApiService] with its stored idempotency key.
 *
 *  - 2xx success  -> mark SYNCED
 *  - 409 Conflict -> the backend already has this payment -> also SYNCED (safe)
 *  - 5xx / network -> Result.retry() so WorkManager backs off and tries again
 */
class NetworkSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val dao = TransactionDatabase.getInstance(appContext).transactionDao()
    private val api = WalletApiService.create()

    override suspend fun doWork(): Result {
        val pending = dao.pendingTransactions()

        for (tx in pending) {
            try {
                val response = api.submitPayment(
                    idempotencyKey = tx.idempotencyKey,
                    payment = PaymentDto(
                        fromAccount = tx.fromAccount,
                        toAccount = tx.toAccount,
                        amount = tx.amount,
                        currency = tx.currency,
                        narrative = tx.narrative
                    )
                )

                when {
                    response.isSuccessful ->
                        dao.updateStatus(tx.idempotencyKey, OutboxTransaction.STATUS_SYNCED)

                    // Duplicate: the backend already committed this payment.
                    response.code() == 409 ->
                        dao.updateStatus(tx.idempotencyKey, OutboxTransaction.STATUS_SYNCED)

                    // Transient server error -> stop and let WorkManager retry the batch.
                    response.code() >= 500 -> return Result.retry()

                    // Permanent client error (e.g. 400) -> park as FAILED for inspection.
                    else ->
                        dao.updateStatus(tx.idempotencyKey, OutboxTransaction.STATUS_FAILED)
                }
            } catch (e: Exception) {
                // Likely back offline mid-batch -> retry the whole batch later.
                return Result.retry()
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "ngao-outbox-sync"
    }
}
