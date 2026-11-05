package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.immutables.value.Value;

/** Un movimiento de la cuenta, tal como lo ve la presentación. */
@ApplicationValue
@Value.Immutable
public interface TransactionSummaryDef {

    UUID id();

    /** {@code DEPOSIT} o {@code WITHDRAWAL}. */
    String type();

    BigDecimal amount();

    String currency();

    Instant occurredOn();

    /** {@code PENDING}, {@code APPROVED} o {@code REJECTED}. */
    String status();

    /**
     * IBAN normalizado de la otra cuenta si el movimiento forma parte de una transferencia (también si se rechazó):
     * la de destino en la retirada del origen y la de origen en el ingreso del destino. Vacío en los demás (#72).
     */
    Optional<String> counterpartAccountNumber();
}
