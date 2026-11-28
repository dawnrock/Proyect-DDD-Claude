package com.dawnrock.ddd.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Orden de retirar un importe de una cuenta. Valida sus campos al construirse ({@code IllegalArgumentException}), antes de
 * llegar al dominio: importe positivo después de redondear a los decimales de la divisa y divisa ISO 4217 válida.
 *
 * @param currency código ISO 4217 ({@code EUR} si es {@code null})
 */
public record WithdrawCommand(UUID accountId, BigDecimal amount, String currency) {

    public WithdrawCommand {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        currency = Objects.requireNonNullElse(currency, CommandValidation.DEFAULT_CURRENCY);
        CommandValidation.requireValidMovement(amount, currency);
    }

    /** La orden en euros. */
    public static WithdrawCommand of(UUID accountId, BigDecimal amount) {
        return new WithdrawCommand(accountId, amount, null);
    }
}
