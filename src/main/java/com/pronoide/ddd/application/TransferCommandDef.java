package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

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

    /** Destino por id. Transitorio: lo sustituye {@link #targetAccountNumber()} y se elimina al integrar la #50. */
    @Nullable
    UUID targetAccountId();

    /**
     * Destino por número de cuenta (IBAN, con o sin espacios). Contrato {@code docs/contracts/login-roles.md}: así un
     * cliente transfiere a otra cuenta sin ver la lista de cuentas de los demás.
     */
    @Nullable
    String targetAccountNumber();

    BigDecimal amount();

    @Value.Default
    default String currency() {
        return "EUR";
    }

    @Value.Check
    default void validate() {
        if ((targetAccountId() == null) == (targetAccountNumber() == null)) {
            throw new IllegalArgumentException("Indica la cuenta de destino por su número");
        }
        if (targetAccountNumber() != null
                && !targetAccountNumber().replace(" ", "").toUpperCase().matches("ES\\d{22}")) {
            throw new IllegalArgumentException(
                    "El número de cuenta de destino debe ser ES seguido de 22 dígitos: " + targetAccountNumber());
        }
        CommandValidation.requireValidMovement(amount(), currency());
    }
}
