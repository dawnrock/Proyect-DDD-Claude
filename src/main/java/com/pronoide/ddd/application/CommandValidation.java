package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/** Comprobaciones de formato que comparten los commands antes de llegar al dominio. */
final class CommandValidation {

    private CommandValidation() {
    }

    /** {@code true} si {@code code} es un código ISO 4217 en mayúsculas y existente. */
    static boolean isIsoCurrency(String code) {
        try {
            return code.equals(Currency.getInstance(code).getCurrencyCode());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Exige una divisa ISO 4217 válida y un importe positivo después de redondearlo (HALF_UP) a los
     * decimales de esa divisa, igual que hace {@code MonetaryAmount}.
     */
    static void requireValidMovement(BigDecimal amount, String currency) {
        if (!isIsoCurrency(currency)) {
            throw new IllegalArgumentException("La divisa %s no es un código ISO 4217 válido".formatted(currency));
        }
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        if (amount.setScale(digits, RoundingMode.HALF_UP).signum() <= 0) {
            throw new IllegalArgumentException("El importe debe ser positivo");
        }
    }
}
