package com.ngao.payment.service;

import com.ngao.payment.config.KafkaTopicConfig;
import com.ngao.payment.domain.Transaction;
import com.ngao.payment.domain.TransactionStatus;
import com.ngao.payment.dto.PaymentRequest;
import com.ngao.payment.dto.PaymentResponse;
import com.ngao.payment.event.PaymentSuccessEvent;
import com.ngao.payment.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Orchestrates a payment: persist to the ledger, then publish a domain event.
 *
 * <p>Idempotency is enforced at two layers:
 * <ol>
 *   <li>Redis (fast path) via {@code IdempotencyInterceptor}, before we get here.</li>
 *   <li>The unique {@code idempotency_key} column on the ledger (durable backstop),
 *       checked here and guaranteed by the database constraint.</li>
 * </ol>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, PaymentSuccessEvent> kafkaTemplate;

    public PaymentService(TransactionRepository transactionRepository,
                          KafkaTemplate<String, PaymentSuccessEvent> kafkaTemplate) {
        this.transactionRepository = transactionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {

        // Durable idempotency backstop: if Redis was flushed but we already
        // committed this key, return the existing transaction (no double-post).
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotency key '{}' already committed as transaction {}",
                    idempotencyKey, existing.get().getId());
            return toResponse(existing.get());
        }

        Transaction tx = new Transaction(
                idempotencyKey,
                request.fromAccount(),
                request.toAccount(),
                request.amount(),
                request.currency(),
                request.narrative(),
                TransactionStatus.SUCCESS
        );
        Transaction saved = transactionRepository.save(tx);
        log.info("Persisted transaction {} to ledger (amount={} {})",
                saved.getId(), saved.getAmount(), saved.getCurrency());

        publishEvent(saved);
        return toResponse(saved);
    }

    private void publishEvent(Transaction tx) {
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                tx.getId(),
                tx.getFromAccount(),
                tx.getToAccount(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getStatus().name(),
                tx.getNarrative(),
                tx.getCreatedAt() != null ? tx.getCreatedAt() : Instant.now()
        );

        // NOTE: published inline for clarity. A production system would use the
        // transactional-outbox pattern (or @TransactionalEventListener AFTER_COMMIT)
        // so an event is never emitted for a rolled-back ledger write.
        kafkaTemplate.send(KafkaTopicConfig.CORE_TRANSACTIONS_TOPIC, tx.getId().toString(), event);
        log.info("Published PaymentSuccessEvent for transaction {} -> topic '{}'",
                tx.getId(), KafkaTopicConfig.CORE_TRANSACTIONS_TOPIC);
    }

    private PaymentResponse toResponse(Transaction tx) {
        return new PaymentResponse(
                tx.getId(),
                tx.getFromAccount(),
                tx.getToAccount(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getStatus(),
                tx.getCreatedAt()
        );
    }
}
