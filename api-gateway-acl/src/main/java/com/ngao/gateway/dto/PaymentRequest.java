package com.ngao.gateway.dto;

import java.math.BigDecimal;

/**
 * The modern JSON payload accepted from the mobile wallet. The gateway treats it
 * as an opaque pass-through and forwards it verbatim to the Core Payment Engine,
 * which owns validation and persistence.
 */
public record PaymentRequest(
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String currency,
        String narrative
) {
}
