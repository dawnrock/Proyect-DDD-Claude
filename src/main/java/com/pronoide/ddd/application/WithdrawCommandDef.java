package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.immutables.value.Value;

/**
 * Orden de retirar un importe de una cuenta. Valida sus campos antes de llegar al dominio (pendiente: T2).
 *
 * <p>{@code currency} es un código ISO 4217 ({@code EUR} por defecto).
 */
@ApplicationValue
@Value.Immutable
public interface WithdrawCommandDef {

    UUID accountId();

    BigDecimal amount();

    @Value.Default
    default String currency() {
        return "EUR";
    }
}
