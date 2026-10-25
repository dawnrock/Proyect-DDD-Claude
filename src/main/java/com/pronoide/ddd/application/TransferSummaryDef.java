package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.immutables.value.Value;

/** Resultado de una transferencia, tal como lo ve la presentación. */
@ApplicationValue
@Value.Immutable
public interface TransferSummaryDef {

    UUID sourceAccountId();

    String sourceAccountNumber();

    UUID targetAccountId();

    String targetAccountNumber();

    /** Importe de la retirada en origen, ya redondeado a los decimales de la divisa. */
    BigDecimal amount();

    String currency();

    /** Instante de la retirada en origen. */
    Instant occurredOn();

    /** {@code APPROVED} o {@code REJECTED}: el estado de la retirada en la cuenta de origen. */
    String status();
}
