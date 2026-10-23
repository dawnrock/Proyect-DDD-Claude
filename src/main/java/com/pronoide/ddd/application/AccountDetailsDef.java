package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.immutables.value.Value;

/** Detalle de una cuenta, con sus movimientos en el orden en que se registraron. */
@ApplicationValue
@Value.Immutable
public interface AccountDetailsDef {

    UUID id();

    String accountNumber();

    String holderName();

    /** {@code RETAIL} o {@code HIGH_NET_WORTH}. */
    String segment();

    BigDecimal balance();

    String currency();

    List<TransactionSummary> transactions();
}
