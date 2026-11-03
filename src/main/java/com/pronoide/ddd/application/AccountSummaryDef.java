package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.immutables.value.Value;

/** Resumen de una cuenta para listados y para la respuesta de abrir cuenta. */
@ApplicationValue
@Value.Immutable
public interface AccountSummaryDef {

    UUID id();

    /** IBAN normalizado: sin espacios y en mayúsculas. */
    String accountNumber();

    String holderName();

    /** {@code RETAIL} o {@code HIGH_NET_WORTH}: el segmento que decidió la factoría (#18). */
    String segment();

    BigDecimal balance();

    String currency();
}
