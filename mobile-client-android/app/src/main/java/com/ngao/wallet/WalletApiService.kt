package com.ngao.wallet

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * WalletApiService.kt
 * ===================
 * Retrofit interface for talking to the API Gateway (ACL).
 *
 * The critical detail is the per-request **`X-Idempotency-Key`** header: the SAME
 * client-generated UUID is sent on every retry of a given payment, so the
 * backend's Redis idempotency gate collapses duplicate submissions into exactly
 * one ledger entry. This is essential for an offline-first client that may resend
 * queued payments after regaining connectivity.
 */

/** Mirrors the gateway's modern JSON contract (POST /api/v1/payments). */
data class PaymentDto(
    val fromAccount: String,
    val toAccount: String,
    val amount: Double,
    val currency: String,
    val narrative: String?
)

interface WalletApiService {

    @POST("api/v1/payments")
    suspend fun submitPayment(
        @Header("X-Idempotency-Key") idempotencyKey: String,
        @Body payment: PaymentDto
    ): Response<Unit>

    companion object {
        // 10.0.2.2 is the Android emulator's alias for the host machine's localhost,
        // where the API Gateway (api-gateway-acl) listens on port 8080.
        private const val BASE_URL = "http://10.0.2.2:8080/"

        fun create(): WalletApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WalletApiService::class.java)
        }
    }
}
