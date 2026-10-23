package com.pronoide.ddd.domain.model;

import java.time.Instant;
import java.util.Objects;

import java.util.UUID;

public class Transaction {

    private final UUID id;
    private final UUID accountId;
    private final TransactionType type;
    private final MonetaryAmount amount;
    private final Instant occurredOn;
    private TransactionStatus status;

    private Transaction(UUID accountId, TransactionType type, MonetaryAmount amount) {
        this.id = UUID.randomUUID();
        this.accountId = Objects.requireNonNull(accountId, "La cuenta del movimiento es obligatoria");
        this.type = type;
        this.amount = amount;
        this.occurredOn = Instant.now();
        this.status = TransactionStatus.PENDING;
    }

    static Transaction deposit(UUID accountId, MonetaryAmount amount) {
        return new Transaction(accountId, TransactionType.DEPOSIT, amount);
    }

    static Transaction withdrawal(UUID accountId, MonetaryAmount amount) {
        return new Transaction(accountId, TransactionType.WITHDRAWAL, amount);
    }

    void approve() {
        requirePending();
        this.status = TransactionStatus.APPROVED;
    }

    void reject() {
        requirePending();
        this.status = TransactionStatus.REJECTED;
    }

    public boolean isApproved() {
        return status == TransactionStatus.APPROVED;
    }

    public boolean isRejected() {
        return status == TransactionStatus.REJECTED;
    }

    public UUID id() {
        return id;
    }

    public UUID accountId() {
        return accountId;

    }

    public TransactionType type() {
        return type;
    }

    public MonetaryAmount amount() {
        return amount;
    }

    public Instant occurredOn() {
        return occurredOn;
    }

    public TransactionStatus status() {
        return status;
    }

    private void requirePending() {
        if (status != TransactionStatus.PENDING) {
            throw new IllegalStateException(
                    "El movimiento %s ya se resolvió con el estado %s".formatted(id, status));
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Transaction that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "%s de %s [%s]".formatted(type, amount, status);
    }
}
