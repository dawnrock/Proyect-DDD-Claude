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

    private Transaction(UUID id, UUID accountId, TransactionType type, MonetaryAmount amount, Instant occurredOn,
                        AccountNumber counterpart, TransactionStatus status) {
        this.id = Objects.requireNonNull(id, "El id del movimiento es obligatorio");
        this.accountId = Objects.requireNonNull(accountId, "La cuenta del movimiento es obligatoria");
        this.type = Objects.requireNonNull(type, "El tipo del movimiento es obligatorio");
        this.amount = Objects.requireNonNull(amount, "El importe del movimiento es obligatorio");
        this.occurredOn = Objects.requireNonNull(occurredOn, "La fecha del movimiento es obligatoria");
        this.counterpart = counterpart;
        this.status = Objects.requireNonNull(status, "El estado del movimiento es obligatorio");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("El importe del movimiento debe ser positivo");
        }
    }

    /**
     * Reconstruye un movimiento ya guardado, con su identidad, su fecha y su estado (#150). Solo lo usan la cuenta al
     * reconstituirse y los adaptadores de persistencia; un movimiento nuevo nace dentro de la cuenta.
     *
     * @param counterpart la otra cuenta de una transferencia, o {@code null}
     */
    public static Transaction reconstitute(UUID id, UUID accountId, TransactionType type, MonetaryAmount amount,
                                           Instant occurredOn, AccountNumber counterpart, TransactionStatus status) {
        return new Transaction(id, accountId, type, amount, occurredOn, counterpart, status);
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
