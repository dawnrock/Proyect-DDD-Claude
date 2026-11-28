package com.dawnrock.ddd.application;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Orden de un cliente con sesión de abrir otra cuenta a su nombre: solo indica el depósito. El titular (nombre y NIF)
 * sale del cliente que lo pide. Contrato: {@code docs/contracts/customer-sign-up.md}.
 *
 * @param currency código ISO 4217 ({@code EUR} si es {@code null})
 */
public record OpenAnotherAccountCommand(BigDecimal initialDeposit, String currency) {

    public OpenAnotherAccountCommand {
        Objects.requireNonNull(initialDeposit, "initialDeposit");
        currency = Objects.requireNonNullElse(currency, CommandValidation.DEFAULT_CURRENCY);
        CommandValidation.requireIsoCurrency(currency);
        CommandValidation.requireValidInitialDeposit(initialDeposit, currency);
    }

    /** La orden en euros. */
    public static OpenAnotherAccountCommand of(BigDecimal initialDeposit) {
        return new OpenAnotherAccountCommand(initialDeposit, null);
    }
}
