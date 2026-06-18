package com.ngao.gateway.controller;

import com.ngao.gateway.client.PaymentEngineClient;
import com.ngao.gateway.dto.PaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;
import java.util.UUID;

/**
 * Public REST ingress for the mobile wallet.
 *
 * <p>Accepts a modern JSON payment, ensures an idempotency key is present (minting
 * one if the client omitted it), forwards to the Core Payment Engine, and relays
 * the engine's response — including duplicate (409) and validation (400) outcomes.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class GatewayPaymentController {

    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    private static final Logger log = LoggerFactory.getLogger(GatewayPaymentController.class);

    private final PaymentEngineClient paymentEngineClient;

    public GatewayPaymentController(PaymentEngineClient paymentEngineClient) {
        this.paymentEngineClient = paymentEngineClient;
    }

    @PostMapping
    public ResponseEntity<String> submitPayment(
            @RequestBody PaymentRequest request,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {

        // Offline clients generate the UUID on-device; mint one as a safety net.
        String key = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString()
                : idempotencyKey;

        try {
            ResponseEntity<String> engineResponse = paymentEngineClient.forwardPayment(request, key);
            return ResponseEntity.status(engineResponse.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(engineResponse.getBody());

        } catch (HttpStatusCodeException e) {
            // Propagate the engine's own status (e.g. 409 duplicate, 400 invalid).
            log.warn("Engine returned {} for idempotencyKey {}", e.getStatusCode(), key);
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());

        } catch (ResourceAccessException e) {
            // Engine unreachable / timed out -> 503 so the mobile client can retry later.
            log.error("Core Payment Engine unreachable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"core-payment-engine unavailable, retry later\"}");
        }
    }

    /** Lightweight liveness probe. */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("service", "api-gateway-acl", "status", "UP"));
    }
}
