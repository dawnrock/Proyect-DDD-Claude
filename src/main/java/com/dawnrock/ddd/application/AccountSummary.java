package com.dawnrock.ddd.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Resumen de una cuenta para listados y para la respuesta de abrir cuenta.
 *
 * @param accountNumber IBAN normalizado: sin espacios y en mayúsculas
 * @param segment       {@code RETAIL} o {@code HIGH_NET_WORTH}: el segmento que decidió la factoría (#18)
 */
public record AccountSummary(UUID id, String accountNumber, String holderName, String segment, BigDecimal balance,
                             String currency) {

    public AccountSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(holderName, "holderName");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(currency, "currency");
    }
}
