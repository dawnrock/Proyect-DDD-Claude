package com.dawnrock.ddd.adapter.in.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Respuesta REST con el resultado de una transferencia (esquema {@code Transfer} del OpenAPI). Los campos van en orden
 * alfabético, como los escribía Immutables (#219): en un record mandaría el orden de sus componentes.
 */
@JsonPropertyOrder({"amount", "currency", "occurredOn", "sourceAccountId", "sourceAccountNumber", "status",
        "targetAccountNumber"})
public record TransferResponse(UUID sourceAccountId, String sourceAccountNumber, String targetAccountNumber,
                               BigDecimal amount, String currency, Instant occurredOn, String status) {

    public TransferResponse {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        Objects.requireNonNull(sourceAccountNumber, "sourceAccountNumber");
        Objects.requireNonNull(targetAccountNumber, "targetAccountNumber");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(occurredOn, "occurredOn");
        Objects.requireNonNull(status, "status");
    }
}
