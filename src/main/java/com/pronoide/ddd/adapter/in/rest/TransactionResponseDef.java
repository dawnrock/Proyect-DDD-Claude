package com.pronoide.ddd.adapter.in.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.immutables.value.Value;

import com.pronoide.ddd.application.ApplicationValue;

import tools.jackson.databind.annotation.JsonSerialize;

/** Respuesta REST con un movimiento de la cuenta (esquema {@code TransactionSummary} del OpenAPI). */
@ApplicationValue
@Value.Immutable
@JsonSerialize(as = TransactionResponse.class)
public interface TransactionResponseDef {

    UUID id();

    String type();

    BigDecimal amount();

    String currency();

    Instant occurredOn();

    String status();
}
