package com.ngao.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * The modern, clean JSON contract accepted from the API Gateway.
 *
 * <p>Deliberately free of any legacy / core-banking concerns — translating to
 * the legacy SOAP world is the sole responsibility of the Anti-Corruption Layer.
 */
public record PaymentRequest(

        @NotBlank(message = "fromAccount is required")
        String fromAccount,

        @NotBlank(message = "toAccount is required")
        String toAccount,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
        String currency,

        String narrative
) {
}
