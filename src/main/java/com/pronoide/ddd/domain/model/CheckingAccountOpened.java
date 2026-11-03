package com.pronoide.ddd.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Se ha abierto una cuenta. Va seguido del {@link MoneyDeposited} de su depósito inicial, que es un ingreso más; su
 * instante es el de ese ingreso.
 */
public record CheckingAccountOpened(UUID accountId, AccountNumber accountNumber, String holderName,
                                    MonetaryAmount initialDeposit, Instant occurredOn) implements DomainEvent {

    public CheckingAccountOpened {
        Objects.requireNonNull(accountId, "La cuenta del evento es obligatoria");
        Objects.requireNonNull(accountNumber, "El número de cuenta del evento es obligatorio");
        Objects.requireNonNull(holderName, "El titular del evento es obligatorio");
        Objects.requireNonNull(initialDeposit, "El depósito inicial del evento es obligatorio");
        Objects.requireNonNull(occurredOn, "El instante del evento es obligatorio");
    }
}
