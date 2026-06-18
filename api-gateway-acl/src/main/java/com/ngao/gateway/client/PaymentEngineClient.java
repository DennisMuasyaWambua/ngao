package com.ngao.gateway.client;

import com.ngao.gateway.dto.PaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Thin REST client that forwards a payment to the Core Payment Engine, preserving
 * the {@code X-Idempotency-Key} so the engine's Redis gate can de-duplicate retries.
 */
@Component
public class PaymentEngineClient {

    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    private static final Logger log = LoggerFactory.getLogger(PaymentEngineClient.class);

    private final RestTemplate restTemplate;
    private final String paymentsUrl;

    public PaymentEngineClient(RestTemplate restTemplate,
                               @Value("${core-payment-engine.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.paymentsUrl = baseUrl + "/api/v1/payments";
    }

    /**
     * Forward the payment downstream. Returns the engine's raw response (body +
     * status) so the gateway can pass it straight back to the caller.
     */
    public ResponseEntity<String> forwardPayment(PaymentRequest request, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(IDEMPOTENCY_HEADER, idempotencyKey);

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);
        log.info("Forwarding payment (idempotencyKey={}) -> {}", idempotencyKey, paymentsUrl);
        return restTemplate.postForEntity(paymentsUrl, entity, String.class);
    }
}
