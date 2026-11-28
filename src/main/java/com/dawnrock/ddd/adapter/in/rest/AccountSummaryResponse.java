package com.dawnrock.ddd.adapter.in.rest;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Respuesta REST con el resumen de una cuenta (esquema {@code AccountSummary} del OpenAPI). Los campos van en orden
 * alfabético, como los escribía Immutables (#219): en un record mandaría el orden de sus componentes.
 *
 * @param segment {@code RETAIL} o {@code HIGH_NET_WORTH}: el segmento que ha calculado la factoría
 */
@JsonPropertyOrder({"accountNumber", "balance", "currency", "holderName", "id", "segment"})
public record AccountSummaryResponse(UUID id, String accountNumber, String holderName, String segment,
                                     BigDecimal balance, String currency) {

    public AccountSummaryResponse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(holderName, "holderName");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(currency, "currency");
    }
}
