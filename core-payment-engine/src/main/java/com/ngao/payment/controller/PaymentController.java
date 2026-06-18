package com.ngao.payment.controller;

import com.ngao.payment.dto.PaymentRequest;
import com.ngao.payment.dto.PaymentResponse;
import com.ngao.payment.interceptor.IdempotencyInterceptor;
import com.ngao.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST ingress for the Core Payment Engine.
 *
 * <p>The {@link IdempotencyInterceptor} runs before {@link #createPayment} and
 * guarantees the {@code X-Idempotency-Key} header is present and previously unseen.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(IdempotencyInterceptor.IDEMPOTENCY_HEADER) String idempotencyKey) {

        PaymentResponse response = paymentService.processPayment(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Lightweight liveness probe (deliberately not behind the idempotency gate). */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("service", "core-payment-engine", "status", "UP"));
    }
}
