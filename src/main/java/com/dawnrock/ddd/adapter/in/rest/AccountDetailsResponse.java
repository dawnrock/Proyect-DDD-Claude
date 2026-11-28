package com.dawnrock.ddd.adapter.in.rest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Respuesta REST con el detalle de una cuenta y sus movimientos (esquema {@code AccountDetails} del OpenAPI). Los
 * campos van en orden alfabético, como los escribía Immutables (#219): en un record mandaría el orden de sus
 * componentes.
 *
 * @param holderTaxId documento (DNI o NIE) normalizado del titular
 * @param holderEmail correo del cliente titular; vacío si la cuenta no es de un cliente registrado
 */
@JsonPropertyOrder({"accountNumber", "balance", "currency", "holderEmail", "holderName", "holderTaxId", "id", "segment",
        "transactions"})
public record AccountDetailsResponse(UUID id, String accountNumber, String holderName, String holderTaxId,
                                     String holderEmail, String segment, BigDecimal balance, String currency,
                                     List<TransactionResponse> transactions) {

    public AccountDetailsResponse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(holderName, "holderName");
        Objects.requireNonNull(holderTaxId, "holderTaxId");
        Objects.requireNonNull(holderEmail, "holderEmail");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(currency, "currency");
        transactions = List.copyOf(transactions);
    }
}
