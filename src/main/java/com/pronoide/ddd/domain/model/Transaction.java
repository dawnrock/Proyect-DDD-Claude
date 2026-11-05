package com.pronoide.ddd.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class Transaction {

    private final UUID id;
    private final UUID accountId;
    private final TransactionType type;
    private final MonetaryAmount amount;
    private final Instant occurredOn;
    private final AccountNumber counterpart;
    private TransactionStatus status;

    private Transaction(UUID accountId, TransactionType type, MonetaryAmount amount, AccountNumber counterpart) {
        this.id = UUID.randomUUID();
        this.accountId = Objects.requireNonNull(accountId, "La cuenta del movimiento es obligatoria");
        this.type = type;
        this.amount = amount;
        this.counterpart = counterpart;
        this.occurredOn = Instant.now();
        this.status = TransactionStatus.PENDING;
    }

    static Transaction deposit(UUID accountId, MonetaryAmount amount) {
        return new Transaction(accountId, TransactionType.DEPOSIT, amount, null);
    }

    static Transaction withdrawal(UUID accountId, MonetaryAmount amount) {
        return new Transaction(accountId, TransactionType.WITHDRAWAL, amount, null);
    }

    /** Ingreso que llega por una transferencia: guarda la cuenta de origen. */
    static Transaction depositFromTransfer(UUID accountId, MonetaryAmount amount, AccountNumber source) {
        Objects.requireNonNull(source, "La cuenta de origen de la transferencia es obligatoria");
        return new Transaction(accountId, TransactionType.DEPOSIT, amount, source);
    }

    /** Retirada que sale por una transferencia: guarda la cuenta de destino. */
    static Transaction withdrawalForTransfer(UUID accountId, MonetaryAmount amount, AccountNumber target) {
        Objects.requireNonNull(target, "La cuenta de destino de la transferencia es obligatoria");
        return new Transaction(accountId, TransactionType.WITHDRAWAL, amount, target);
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

    /** La otra cuenta de la transferencia a la que pertenece el movimiento; vacío en ingresos y retiradas normales. */
    public Optional<AccountNumber> counterpart() {
        return Optional.ofNullable(counterpart);
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
