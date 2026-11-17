package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/** Comprobaciones de formato que comparten los commands antes de llegar al dominio. */
final class CommandValidation {

    /**
     * Importe máximo de una operación (ingreso, retirada, transferencia o depósito inicial). Es un límite técnico de
     * la entrada, no una regla de negocio: evita importes absurdos como {@code 1e308} (#108).
     */
    static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

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
     * Exige una divisa ISO 4217 válida y un importe positivo y no mayor que {@link #MAX_AMOUNT} después de
     * redondearlo (HALF_UP) a los decimales de esa divisa, igual que hace {@code MonetaryAmount}.
     */
    static void requireValidMovement(BigDecimal amount, String currency) {
        if (!isIsoCurrency(currency)) {
            throw new IllegalArgumentException("La divisa %s no es un código ISO 4217 válido".formatted(currency));
        }
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        BigDecimal rounded = amount.setScale(digits, RoundingMode.HALF_UP);
        if (rounded.signum() <= 0) {
            throw new IllegalArgumentException("El importe debe ser positivo");
        }
        if (rounded.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("El importe no puede superar " + MAX_AMOUNT.toPlainString());
        }
    }
}
