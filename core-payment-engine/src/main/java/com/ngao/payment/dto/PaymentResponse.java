package com.ngao.payment.dto;

import com.ngao.payment.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Response returned to the caller once a payment is committed to the ledger. */
public record PaymentResponse(
        UUID transactionId,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        Instant createdAt
) {
}
