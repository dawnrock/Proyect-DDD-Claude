package com.pronoide.ddd.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Se ha ingresado dinero en la cuenta, también el depósito inicial al abrirla. */
public record MoneyDeposited(UUID accountId, UUID transactionId, MonetaryAmount amount, Instant occurredOn)
        implements DomainEvent {

    public MoneyDeposited {
        Objects.requireNonNull(accountId, "La cuenta del evento es obligatoria");
        Objects.requireNonNull(transactionId, "El movimiento del evento es obligatorio");
        Objects.requireNonNull(amount, "El importe del evento es obligatorio");
        Objects.requireNonNull(occurredOn, "El instante del evento es obligatorio");
    }
}
