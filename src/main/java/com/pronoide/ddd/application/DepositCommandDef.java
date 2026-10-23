package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.immutables.value.Value;

/**
 * Orden de ingresar un importe en una cuenta. Valida sus campos al construirse ({@code IllegalArgumentException}), antes de llegar al dominio:
 * importe positivo después de redondear a los decimales de la divisa y divisa ISO 4217 válida.
 *
 * <p>{@code currency} es un código ISO 4217 ({@code EUR} por defecto).
 */
@ApplicationValue
@Value.Immutable
public interface DepositCommandDef {

    UUID accountId();

    BigDecimal amount();

    @Value.Default
    default String currency() {
        return "EUR";
    }

    @Value.Check
    default void validate() {
        CommandValidation.requireValidMovement(amount(), currency());
    }
}
