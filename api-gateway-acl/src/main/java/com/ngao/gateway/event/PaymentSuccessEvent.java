package com.ngao.gateway.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The gateway's own copy of the event contract carried on {@code core-transactions}.
 *
 * <p>Deliberately a separate type from the producer's class (in core-payment-engine):
 * the ACL must not share a domain model with the service it integrates. The Kafka
 * consumer is configured to bind the JSON payload to <em>this</em> type regardless
 * of the producer's type headers.
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
