package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.time.Instant;
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
}
