package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Set;

import org.immutables.value.Value;

/**
 * Orden de abrir una cuenta corriente. Valida sus campos al construirse ({@code IllegalArgumentException}),
 * antes de llegar al dominio.
 *
 * <p>{@code segment} es {@code RETAIL} o {@code HIGH_NET_WORTH}; {@code currency}, un código ISO 4217
 * ({@code EUR} por defecto).
 */
@ApplicationValue
@Value.Immutable
public interface OpenAccountCommandDef {

    Set<String> SEGMENTS = Set.of("RETAIL", "HIGH_NET_WORTH");

    String accountNumber();

    String holderName();

    String segment();

    BigDecimal initialDeposit();

    @Value.Default
    default String currency() {
        return "EUR";
    }

    @Value.Check
    default void validate() {
        if (accountNumber().isBlank()) {
            throw new IllegalArgumentException("El número de cuenta es obligatorio");
        }
        if (holderName().isBlank()) {
            throw new IllegalArgumentException("El nombre del titular es obligatorio");
        }
        if (!SEGMENTS.contains(segment())) {
            throw new IllegalArgumentException("El segmento debe ser RETAIL o HIGH_NET_WORTH");
        }
        if (initialDeposit().signum() <= 0) {
            throw new IllegalArgumentException("El depósito inicial debe ser positivo");
        }
        if (!isIsoCurrency(currency())) {
            throw new IllegalArgumentException("La divisa %s no es un código ISO 4217 válido".formatted(currency()));
        }
    }

    private static boolean isIsoCurrency(String code) {
        try {
            return code.equals(Currency.getInstance(code).getCurrencyCode());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
