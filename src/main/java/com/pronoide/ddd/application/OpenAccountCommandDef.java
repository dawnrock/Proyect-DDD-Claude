package com.pronoide.ddd.application;

import java.math.BigDecimal;

import org.immutables.value.Value;

/**
 * Orden de abrir una cuenta corriente. Valida sus campos antes de llegar al dominio (pendiente: T2).
 *
 * <p>{@code segment} es {@code RETAIL} o {@code HIGH_NET_WORTH}; {@code currency}, un código ISO 4217
 * ({@code EUR} por defecto).
 */
@ApplicationValue
@Value.Immutable
public interface OpenAccountCommandDef {

    String accountNumber();

    String holderName();

    String segment();

    BigDecimal initialDeposit();

    @Value.Default
    default String currency() {
        return "EUR";
    }
}
