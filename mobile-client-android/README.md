# Ngao Wallet — Android Client (Scaffold)

The offline-first mobile wallet for Project Ngao. This module is a **structural
scaffold**: the architecture-defining files are present and commented, but the
generated bits Android Studio normally adds (the Gradle wrapper, full launcher
mipmaps, layouts) are intentionally omitted.

## Offline-resilience model

```
 user taps "Send"                 connectivity returns
        │                                  │
        ▼                                  ▼
  TransactionDatabase  ──drained by──▶  NetworkSyncWorker ──▶ WalletApiService ──▶ API Gateway
  (Room outbox, local         (WorkManager,            (Retrofit + the
   source of truth)            network-constrained)     X-Idempotency-Key header)
```

The device records the user's *intent* locally and treats the network as an
unreliable courier. Because each queued payment carries a stable, on-device UUID
as its `X-Idempotency-Key`, re-sending is always safe — the backend collapses
duplicates into a single ledger entry.

## Key files (`app/src/main/java/com/ngao/wallet/`)

| File                     | Purpose                                                                 |
| ------------------------ | ----------------------------------------------------------------------- |
| `TransactionDatabase.kt` | Room DB + DAO + outbox entity — local offline storage / source of truth. |
| `NetworkSyncWorker.kt`   | WorkManager `CoroutineWorker` that drains the outbox to the backend.     |
| `WalletApiService.kt`    | Retrofit API to the gateway, attaching the UUID idempotency header.      |
| `WalletApplication.kt`   | Schedules the periodic background sync on startup.                       |
| `MainActivity.kt`        | Launcher activity (UI omitted in the scaffold).                          |

## Opening / building

Open the `mobile-client-android/` folder in **Android Studio** (Giraffe+). Android
Studio will sync Gradle and generate the wrapper automatically. To build from the
CLI you would first generate the wrapper:

```bash
cd mobile-client-android
gradle wrapper --gradle-version 8.7   # requires a local Gradle 8.x
./gradlew :app:assembleDebug
```

> Connects to the API Gateway at `http://10.0.2.2:8080` — the Android emulator's
> alias for the host machine's `localhost`.
