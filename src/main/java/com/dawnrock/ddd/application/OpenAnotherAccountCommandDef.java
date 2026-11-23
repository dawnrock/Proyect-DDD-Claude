package com.dawnrock.ddd.application;

import java.math.BigDecimal;

import org.immutables.value.Value;

/**
 * Orden de un cliente con sesión de abrir otra cuenta a su nombre: solo indica el depósito. El titular (nombre y NIF)
 * sale del cliente que lo pide. Contrato: {@code docs/contracts/customer-sign-up.md}.
 */
@ApplicationValue
@Value.Immutable
public interface OpenAnotherAccountCommandDef {

    BigDecimal initialDeposit();

    @Value.Default
    default String currency() {
        return "EUR";
    }

    @Value.Check
    default void validate() {
        if (initialDeposit().signum() <= 0) {
            throw new IllegalArgumentException("El depósito inicial debe ser positivo");
        }
        if (!CommandValidation.isIsoCurrency(currency())) {
            throw new IllegalArgumentException("La divisa %s no es un código ISO 4217 válido".formatted(currency()));
        }
    }
}
