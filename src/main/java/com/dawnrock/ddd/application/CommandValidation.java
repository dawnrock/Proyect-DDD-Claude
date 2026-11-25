package com.dawnrock.ddd.application;

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

    /** Exige que {@code value} cumpla {@code pattern}; si no, lo rechaza con {@code message}. */
    static void requireMatching(String value, String pattern, String message) {
        if (!value.matches(pattern)) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Como {@link #requireMatching}, pero un campo opcional vacío o en blanco se acepta sin mirar el patrón. */
    static void requireMatchingIfPresent(String value, String pattern, String message) {
        if (!value.isBlank()) {
            requireMatching(value, pattern, message);
        }
    }

    /** Exige que {@code value} no esté vacío ni en blanco; si lo está, lo rechaza con {@code message}. */
    static void requireNotBlank(String value, String message) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Exige que {@code code} sea un código ISO 4217 válido; es el único sitio con el mensaje de divisa no válida. */
    static void requireIsoCurrency(String code) {
        if (!isIsoCurrency(code)) {
            throw new IllegalArgumentException("La divisa %s no es un código ISO 4217 válido".formatted(code));
        }
    }

    /**
     * Exige una divisa ISO 4217 válida y un importe positivo después de redondearlo (HALF_UP) a los decimales de esa
     * divisa, igual que hace {@code MonetaryAmount}, y no mayor que {@link #MAX_AMOUNT} tal como llega (sin redondear,
     * igual que el {@code @DecimalMax} de la API y el {@code maximum} del contrato).
     */
    static void requireValidMovement(BigDecimal amount, String currency) {
        requireIsoCurrency(currency);
        requireValidAmount(amount, currency, "El importe");
    }

    /**
     * Mismas reglas de importe para el depósito inicial de una apertura. La divisa ya debe ser un código ISO 4217
     * válido: cada command la comprueba antes con {@link #requireIsoCurrency}.
     */
    static void requireValidInitialDeposit(BigDecimal amount, String currency) {
        requireValidAmount(amount, currency, "El depósito inicial");
    }

    private static void requireValidAmount(BigDecimal amount, String currency, String subject) {
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        BigDecimal rounded = amount.setScale(digits, RoundingMode.HALF_UP);
        if (rounded.signum() <= 0) {
            throw new IllegalArgumentException(subject + " debe ser positivo");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(subject + " no puede superar " + MAX_AMOUNT.toPlainString());
        }
    }
}
