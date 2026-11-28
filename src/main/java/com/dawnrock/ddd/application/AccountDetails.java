package com.dawnrock.ddd.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Detalle de una cuenta, con sus movimientos en el orden en que se registraron.
 *
 * @param holderTaxId documento (DNI o NIE) normalizado del titular. Vacío por defecto ({@code null} → vacío): el
 *                    servicio lo rellena siempre
 * @param holderEmail correo del cliente titular; vacío si la cuenta no es de un cliente registrado ({@code null} →
 *                    vacío)
 * @param segment     {@code RETAIL} o {@code HIGH_NET_WORTH}
 */
public record AccountDetails(UUID id, String accountNumber, String holderName, String holderTaxId, String holderEmail,
                             String segment, BigDecimal balance, String currency,
                             List<TransactionSummary> transactions) {

    public AccountDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(holderName, "holderName");
        holderTaxId = Objects.requireNonNullElse(holderTaxId, "");
        holderEmail = Objects.requireNonNullElse(holderEmail, "");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(currency, "currency");
        transactions = List.copyOf(Objects.requireNonNull(transactions, "transactions"));
    }

    /** El detalle sin documento, correo ni movimientos (los tres vacíos). */
    public static AccountDetails of(UUID id, String accountNumber, String holderName, String segment,
                                    BigDecimal balance, String currency) {
        return new AccountDetails(id, accountNumber, holderName, "", "", segment, balance, currency, List.of());
    }
}
