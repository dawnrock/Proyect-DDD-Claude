package com.pronoide.ddd.adapter.in.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pronoide.ddd.application.ApplicationValue;

import tools.jackson.databind.annotation.JsonSerialize;

/** Respuesta REST con un movimiento de la cuenta (esquema {@code Transaction} del OpenAPI). */
@ApplicationValue
@Value.Immutable
@JsonSerialize(as = TransactionResponse.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface TransactionResponseDef {

    UUID id();

    String type();

    BigDecimal amount();

    String currency();

    Instant occurredOn();

    String status();

    /**
     * IBAN normalizado de la otra cuenta si el movimiento es de una transferencia; se omite si no (#72). Es
     * {@code @Nullable} y no {@code Optional} porque Jackson 3 escribe un {@code Optional} vacío como {@code null}.
     */
    @Nullable
    String counterpartAccountNumber();
}
