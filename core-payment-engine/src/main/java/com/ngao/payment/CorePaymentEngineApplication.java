package com.ngao.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Project Ngao :: Core Payment Engine.
 *
 * <p>Owns the money. Enforces idempotency (Redis), persists the ledger
 * (Postgres) and emits domain events (Kafka topic {@code core-transactions}).
 */
@SpringBootApplication
public class CorePaymentEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CorePaymentEngineApplication.class, args);
    }
}
