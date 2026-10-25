package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.immutables.value.Value;

/**
 * Orden de transferir un importe de una cuenta de origen a una de destino. Contratos:
 * {@code docs/contracts/money-transfers.md} y {@code docs/contracts/login-roles.md}.
 *
 * <p>{@code currency} es un código ISO 4217 ({@code EUR} por defecto). Que origen y destino sean la misma cuenta es
 * una regla de negocio (R17), no de formato: la comprueba el dominio, no este command.
 */
@ApplicationValue
@Value.Immutable
public interface TransferCommandDef {

    UUID sourceAccountId();

    /**
     * Destino por número de cuenta (IBAN español, con o sin espacios, en mayúsculas o minúsculas). Así un cliente
     * transfiere a otra cuenta sin ver la lista de cuentas de los demás.
     */
    String targetAccountNumber();

    BigDecimal amount();

    @Value.Default
    default String currency() {
        return "EUR";
    }

    @Value.Check
    default void validate() {
        if (!targetAccountNumber().replace(" ", "").toUpperCase().matches("ES\\d{22}")) {
            throw new IllegalArgumentException(
                    "El número de cuenta de destino debe ser ES seguido de 22 dígitos: " + targetAccountNumber());
        }
        CommandValidation.requireValidMovement(amount(), currency());
    }
}
