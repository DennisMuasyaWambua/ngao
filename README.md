# Project Ngao 🛡️

> **An Offline-Resilient Mobile Wallet & Legacy Integration Gateway**

**Ngao** (Swahili for _"shield"_) is a reference architecture for a mobile money
platform built to thrive in two hostile realities at once:

1. **Unreliable last-mile connectivity** — the Android wallet must keep working
   when the network drops, queuing transactions locally and syncing later.
2. **Decades-old core banking** — the modern, JSON/REST world of the mobile app
   must never leak into, or be corrupted by, the SOAP/XML world of the legacy
   core (e.g. Temenos T24 / Oracle FLEXCUBE). An **Anti-Corruption Layer (ACL)**
   sits between them as the translator and shield.

This repository is a **monorepo** containing the three cooperating components of
the platform plus the infrastructure needed to run them locally.

---

## Table of Contents

- [The Problem](#the-problem)
- [High-Level Architecture](#high-level-architecture)
- [Request Lifecycle](#request-lifecycle)
- [Monorepo Layout](#monorepo-layout)
- [Architectural Patterns](#architectural-patterns)
- [Technology Stack](#technology-stack)
- [Infrastructure Services](#infrastructure-services)
- [Getting Started](#getting-started)
- [Event & API Contracts](#event--api-contracts)
- [Build Phases](#build-phases)

---

## The Problem

Traditional payment integrations fail in emerging-market conditions because they
assume two things that are rarely true: **the device is always online**, and
**the backend speaks a modern protocol**. Ngao rejects both assumptions.

- The **mobile client** treats the local database as the source of truth for the
  user's _intent_, and the network as an unreliable courier. A background worker
  drains the outbox when connectivity returns.
- The **backend** guarantees that retrying a queued payment any number of times
  produces exactly one ledger entry (idempotency), and shields the fragile core
  banking system behind an event-driven translator.

---

## High-Level Architecture

```
                          ┌──────────────────────────────────────────────────┐
                          │                    HOST / CLOUD                    │
   📱 OFFLINE-FIRST       │                                                    │
   ┌───────────────┐      │   ┌────────────────────┐                          │
   │  Android       │     │   │  api-gateway-acl    │   (Project 2 :8080)      │
   │  Wallet        │     │   │  Anti-Corruption    │                          │
   │                │ JSON│   │  Layer / Gateway    │                          │
   │  Room DB       │─────┼──▶│                     │                          │
   │  (outbox)      │ REST│   │  • REST ingress     │                          │
   │  WorkManager   │     │   │  • LegacyTranslator │◀──────────┐              │
   │  Retrofit      │     │   └─────────┬───────────┘           │              │
   └───────────────┘      │             │ REST (forward)        │ Kafka        │
        ▲                 │             ▼                       │ consume      │
        │ sync later      │   ┌────────────────────┐           │              │
        │ (when online)   │   │ core-payment-engine │  (Project 1 :8081)       │
        └─────────────────┼   │                     │           │              │
                          │   │  • Idempotency      │──▶ 🔴 Redis (TTL keys)   │
                          │   │    Interceptor      │           │              │
                          │   │  • PaymentService   │──▶ 🐘 Postgres (ledger)  │
                          │   │  • KafkaTemplate    │───────────┘              │
                          │   └────────────────────┘   publish: core-transactions
                          │                                                    │
                          └──────────────────────────────────────────────────┘
                                                       │
                                                       ▼
                                          📜 Simulated Legacy SOAP/XML
                                          (Temenos / Oracle core push)
```

The arrows show the **two-hop, event-decoupled** flow: a synchronous REST path
in and an asynchronous Kafka path out to the legacy world. The mobile app and the
legacy core never speak to each other directly — the ACL is the only bridge.

---

## Request Lifecycle

A single payment travels through the system as follows:

1. **Capture (offline-safe).** The Android wallet writes the payment to its local
   **Room** database immediately and shows the user a "pending" state. A UUID is
   generated on-device and stored as the **idempotency key** for this intent.
2. **Sync.** When connectivity is available, `NetworkSyncWorker` (WorkManager)
   drains the outbox, calling the gateway via **Retrofit**, attaching the header
   `X-Idempotency-Key: <uuid>`.
3. **Gateway ingress.** `api-gateway-acl` receives the **modern JSON** request,
   performs coarse validation, and **forwards** it to the `core-payment-engine`
   over REST (preserving the idempotency header).
4. **Idempotency gate.** The engine's `IdempotencyInterceptor` checks the key in
   **Redis**:
   - **Key present** → the request is a duplicate retry → respond **409 Conflict**
     (no double-spend).
   - **Key absent** → atomically store the key with a TTL and continue.
5. **Persist.** `PaymentService` writes a `Transaction` row to the **Postgres**
   ledger inside a database transaction.
6. **Publish.** On commit, the engine publishes a `PaymentSuccessEvent` to the
   Kafka topic **`core-transactions`**.
7. **Translate & push (ACL out).** `LegacyTranslatorService` in the gateway
   **consumes** the event and maps the clean JSON/domain model into a
   **simulated SOAP/XML envelope**, logging it to the console to stand in for the
   real Temenos/Oracle push. This is where the anti-corruption translation lives.

> Steps 1–2 are what make Ngao *offline-resilient*; steps 4 and 6–7 are what make
> it *safe* to retry and *safe* to integrate with a legacy core.

---

## Monorepo Layout

| Path                     | Component                  | Description                                                                 |
| ------------------------ | -------------------------- | --------------------------------------------------------------------------- |
| `docker-compose.yml`     | Infrastructure             | Postgres, Redis, Kafka + Zookeeper (and an optional Kafka UI).              |
| `core-payment-engine/`   | **Project 1** (Spring Boot)| Idempotency, ledger persistence, Kafka producer. Owns the money.            |
| `api-gateway-acl/`       | **Project 2** (Spring Boot)| Public REST gateway + Anti-Corruption Layer / legacy SOAP translator.        |
| `mobile-client-android/` | **Project 3** (Kotlin)     | Offline-first wallet scaffold: Room outbox, WorkManager sync, Retrofit API.  |

---

## Architectural Patterns

| Pattern                        | Where                                   | Why it matters                                                                 |
| ------------------------------ | --------------------------------------- | ------------------------------------------------------------------------------ |
| **Anti-Corruption Layer (ACL)**| `api-gateway-acl`                       | Keeps legacy SOAP/XML concerns out of the clean payment domain model.          |
| **Idempotency Key**            | `IdempotencyInterceptor` + Redis        | Guarantees at-most-once ledger writes despite client retries / flaky networks. |
| **Event-Driven / Outbox-ish**  | Kafka `core-transactions`               | Decouples the fast write-path from the slow, fragile legacy push.              |
| **Ledger as System of Record** | Postgres `transactions` table           | Durable, auditable money movement — the single source of financial truth.      |
| **Offline-First / Local Queue**| Room DB + WorkManager (mobile)          | The device, not the network, owns the user's intent.                           |

---

## Technology Stack

| Layer            | Technology                                                         |
| ---------------- | ----------------------------------------------------------------- |
| Mobile           | Android, Kotlin, Room, WorkManager, Retrofit                      |
| Backend services | Java 17, Spring Boot 3, Spring Web, Spring Data JPA, Spring Kafka |
| Legacy bridge    | Spring Web Services (SOAP), simulated XML envelopes               |
| Datastores       | PostgreSQL 16 (ledger), Redis 7 (idempotency)                    |
| Messaging        | Apache Kafka + Zookeeper (Confluent images)                      |
| Build & tooling  | Maven, Gradle (Kotlin DSL), Docker Compose                       |

---

## Infrastructure Services

Brought up by `docker-compose.yml`:

| Service     | Image                          | Host Port | Purpose                                  |
| ----------- | ------------------------------ | --------- | ---------------------------------------- |
| `postgres`  | `postgres:16-alpine`           | `5432`    | Transactional ledger                     |
| `redis`     | `redis:7-alpine`               | `6379`    | Idempotency-key store                    |
| `zookeeper` | `confluentinc/cp-zookeeper`    | `2181`    | Kafka coordination                       |
| `kafka`     | `confluentinc/cp-kafka`        | `9092`    | Event streaming (`core-transactions`)    |
| `kafka-ui`  | `provectuslabs/kafka-ui`       | `8085`    | Optional web console for topics/events   |

**Application ports** (when you run the Spring Boot services):

| Service               | Port   |
| --------------------- | ------ |
| `api-gateway-acl`     | `8080` |
| `core-payment-engine` | `8081` |

---

## Getting Started

### Prerequisites

- Docker & Docker Compose v2
- JDK 17+
- Maven 3.9+ (or use the bundled `./mvnw` wrapper in each service)
- (Optional) Android Studio for the mobile client

### 1. Start the infrastructure

```bash
docker compose up -d
docker compose ps          # wait until postgres/redis/kafka are "healthy"
```

### 2. Run the Core Payment Engine (Project 1)

```bash
cd core-payment-engine
./mvnw spring-boot:run      # starts on http://localhost:8081
```

### 3. Run the API Gateway / ACL (Project 2)

```bash
cd api-gateway-acl
./mvnw spring-boot:run      # starts on http://localhost:8080
```

### 4. Send a test payment through the gateway

```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: 11111111-1111-1111-1111-111111111111" \
  -d '{
        "fromAccount": "ACC-1001",
        "toAccount":   "ACC-2002",
        "amount":      150.00,
        "currency":    "KES",
        "narrative":   "Lunch repayment"
      }'
```

Re-sending the **same** `X-Idempotency-Key` should return **`409 Conflict`**.
Watch the `api-gateway-acl` console — the `LegacyTranslatorService` will print the
**simulated SOAP envelope** generated from the Kafka event.

---

## Event & API Contracts

**Inbound API (mobile → gateway → engine)** — `POST /api/v1/payments`

```jsonc
{
  "fromAccount": "ACC-1001",   // debit account
  "toAccount":   "ACC-2002",   // credit account
  "amount":      150.00,
  "currency":    "KES",
  "narrative":   "Lunch repayment"
}
// Header (required): X-Idempotency-Key: <client-generated UUID>
```

**Kafka topic** `core-transactions` — `PaymentSuccessEvent`

```jsonc
{
  "transactionId": "9f1c...",
  "fromAccount":   "ACC-1001",
  "toAccount":     "ACC-2002",
  "amount":        150.00,
  "currency":      "KES",
  "status":        "SUCCESS",
  "occurredAt":    "2026-06-18T10:15:30Z"
}
```

The `LegacyTranslatorService` transforms the above into a SOAP envelope shaped
like a legacy core-banking `PostTransaction` request before "pushing" it.

---

## Continuous Delivery (CI/CD)

GitHub Actions (`.github/workflows/docker-publish.yml`) builds a Docker image for
each backend service and publishes it to Docker Hub under **`muasya1`**:

| Service               | Image                              |
| --------------------- | ---------------------------------- |
| `core-payment-engine` | `muasya1/ngao-core-payment-engine` |
| `api-gateway-acl`     | `muasya1/ngao-api-gateway-acl`     |

**Triggers**

- **push to `main`** → build + push `:latest` and `:sha-<short>`
- **tag `vX.Y.Z`** → build + push `:X.Y.Z` and `:X.Y`
- **pull request to `main`** → build only (validates the image, no push)

**One-time setup** — add a single repository secret so the workflow can publish:

1. Create a Docker Hub **access token**:
   <https://app.docker.com/settings/personal-access-tokens> → *Generate* (Read & Write).
2. In GitHub: *Settings → Secrets and variables → Actions → New repository secret*
   - **Name:** `DOCKERHUB_TOKEN`
   - **Value:** the access token from step 1

Until that secret exists the pipeline still builds the images (staying green) but
skips the push. Each service also has a standalone multi-stage `Dockerfile`:

```bash
docker build -t muasya1/ngao-core-payment-engine:dev ./core-payment-engine
```

> Runtime config (DB/Redis/Kafka hosts) is overridable via standard Spring
> environment variables — e.g. `SPRING_DATASOURCE_URL`, `SPRING_DATA_REDIS_HOST`,
> `SPRING_KAFKA_BOOTSTRAP_SERVERS` — so the same image runs in any environment.

---

## Build Phases

This monorepo was scaffolded in five deliberate phases:

1. **Infrastructure** — `docker-compose.yml` + this `README.md`.
2. **Core Payment Engine** — idempotency, ledger, Kafka producer.
3. **API Gateway & ACL** — REST forwarder + Kafka-driven SOAP translator.
4. **Mobile Client** — offline-first Android/Kotlin scaffold.
5. **Version Control** — Git initialization and GitHub deployment.

---

> **Disclaimer:** This is a reference scaffold for educational/architectural
> demonstration. The "legacy SOAP push" is simulated by console logging; no real
> core-banking system is contacted. Credentials in `docker-compose.yml` are local
> development defaults and must never be used in production.
