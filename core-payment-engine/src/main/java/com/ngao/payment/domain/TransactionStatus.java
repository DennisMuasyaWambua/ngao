package com.ngao.payment.domain;

/** Lifecycle state of a {@link Transaction} in the ledger. */
public enum TransactionStatus {
    PENDING,
    SUCCESS,
    FAILED
}
