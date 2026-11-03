package com.pronoide.ddd.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Se ha retirado dinero de la cuenta: la retirada se ha aprobado. */
public record MoneyWithdrawn(UUID accountId, UUID transactionId, MonetaryAmount amount, Instant occurredOn)
        implements DomainEvent {

    public MoneyWithdrawn {
        Objects.requireNonNull(accountId, "La cuenta del evento es obligatoria");
        Objects.requireNonNull(transactionId, "El movimiento del evento es obligatorio");
        Objects.requireNonNull(amount, "El importe del evento es obligatorio");
        Objects.requireNonNull(occurredOn, "El instante del evento es obligatorio");
    }
}
