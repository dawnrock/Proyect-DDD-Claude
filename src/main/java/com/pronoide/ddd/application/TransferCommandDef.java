package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.immutables.value.Value;

/**
 * Orden de transferir un importe de una cuenta de origen a una de destino. Contrato:
 * {@code docs/contracts/money-transfers.md}.
 *
 * <p>{@code currency} es un código ISO 4217 ({@code EUR} por defecto). Que origen y destino sean la misma cuenta es
 * una regla de negocio (R17), no de formato: la comprueba el dominio, no este command.
 */
@ApplicationValue
@Value.Immutable
public interface TransferCommandDef {

    UUID sourceAccountId();

    UUID targetAccountId();

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
