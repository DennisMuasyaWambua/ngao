package com.ngao.payment.repository;

import com.ngao.payment.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository over the {@code transactions} ledger table.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Durable idempotency backstop: find a previously committed payment by key. */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
