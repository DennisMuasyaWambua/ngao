package com.ngao.wallet

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Launcher screen. In a full build this hosts the wallet UI (balance + send-money
 * form). For the scaffold it simply demonstrates the activity wired into the
 * manifest; the offline plumbing lives in [TransactionDatabase], [WalletApiService]
 * and [NetworkSyncWorker].
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_main) // UI layout intentionally omitted in scaffold
    }
}
