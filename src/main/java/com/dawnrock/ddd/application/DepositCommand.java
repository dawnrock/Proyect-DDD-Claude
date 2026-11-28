package com.dawnrock.ddd.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Orden de ingresar un importe en una cuenta. Valida sus campos al construirse ({@code IllegalArgumentException}), antes de
 * llegar al dominio: importe positivo después de redondear a los decimales de la divisa y divisa ISO 4217 válida.
 *
 * @param currency código ISO 4217 ({@code EUR} si es {@code null})
 */
public record DepositCommand(UUID accountId, BigDecimal amount, String currency) {

    public DepositCommand {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        currency = Objects.requireNonNullElse(currency, CommandValidation.DEFAULT_CURRENCY);
        CommandValidation.requireValidMovement(amount, currency);
    }

    /** La orden en euros. */
    public static DepositCommand of(UUID accountId, BigDecimal amount) {
        return new DepositCommand(accountId, amount, null);
    }
}
