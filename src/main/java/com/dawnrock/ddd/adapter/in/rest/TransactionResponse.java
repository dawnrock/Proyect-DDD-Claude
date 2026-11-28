package com.dawnrock.ddd.adapter.in.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Respuesta REST con un movimiento de la cuenta (esquema {@code Transaction} del OpenAPI). Los campos van en orden
 * alfabético, como los escribía Immutables (#219): en un record mandaría el orden de sus componentes.
 *
 * @param counterpartAccountNumber IBAN normalizado de la otra cuenta si el movimiento es de una transferencia; se omite
 *                                 si no (#72). Es {@code @Nullable} y no {@code Optional} porque Jackson 3 escribe un
 *                                 {@code Optional} vacío como {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"amount", "counterpartAccountNumber", "currency", "id", "occurredOn", "status", "type"})
public record TransactionResponse(UUID id, String type, BigDecimal amount, String currency, Instant occurredOn,
                                  String status, @Nullable String counterpartAccountNumber) {

    public TransactionResponse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(occurredOn, "occurredOn");
        Objects.requireNonNull(status, "status");
    }
}
