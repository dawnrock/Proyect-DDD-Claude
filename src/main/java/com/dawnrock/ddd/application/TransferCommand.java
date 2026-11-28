package com.dawnrock.ddd.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Orden de transferir un importe de una cuenta de origen a una de destino. Contratos:
 * {@code docs/contracts/money-transfers.md} y {@code docs/contracts/login-roles.md}.
 *
 * <p>Que origen y destino sean la misma cuenta es una regla de negocio (R17), no de formato: la comprueba el dominio,
 * no este command.
 *
 * @param targetAccountNumber destino por número de cuenta (IBAN español, con o sin espacios, en mayúsculas o
 *                            minúsculas). Así un cliente transfiere a otra cuenta sin ver la lista de cuentas de los
 *                            demás
 * @param currency            código ISO 4217 ({@code EUR} si es {@code null})
 */
public record TransferCommand(UUID sourceAccountId, String targetAccountNumber, BigDecimal amount, String currency) {

    public TransferCommand {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        Objects.requireNonNull(targetAccountNumber, "targetAccountNumber");
        Objects.requireNonNull(amount, "amount");
        currency = Objects.requireNonNullElse(currency, CommandValidation.DEFAULT_CURRENCY);
        if (!targetAccountNumber.replace(" ", "").toUpperCase().matches("ES\\d{22}")) {
            throw new IllegalArgumentException(
                    "El número de cuenta de destino debe ser ES seguido de 22 dígitos: " + targetAccountNumber);
        }
        CommandValidation.requireValidMovement(amount, currency);
    }

    /** La orden en euros. */
    public static TransferCommand of(UUID sourceAccountId, String targetAccountNumber, BigDecimal amount) {
        return new TransferCommand(sourceAccountId, targetAccountNumber, amount, null);
    }
}
