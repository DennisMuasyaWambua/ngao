package com.ngao.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published to the {@code core-transactions} Kafka topic after a
 * payment is durably committed to the ledger.
 *
 * <p>The API Gateway's {@code LegacyTranslatorService} consumes this and maps it
 * into a simulated SOAP/XML core-banking push (Temenos / Oracle).
 */
public record PaymentSuccessEvent(
        UUID transactionId,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String currency,
        String status,
        String narrative,
        Instant occurredAt
) {
}
