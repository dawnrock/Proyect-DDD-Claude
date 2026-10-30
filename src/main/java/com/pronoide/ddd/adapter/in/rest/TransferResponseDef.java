package com.pronoide.ddd.adapter.in.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.immutables.value.Value;

import com.pronoide.ddd.application.ApplicationValue;

import tools.jackson.databind.annotation.JsonSerialize;

/** Respuesta REST con el resultado de una transferencia (esquema {@code Transfer} del OpenAPI). */
@ApplicationValue
@Value.Immutable
@JsonSerialize(as = TransferResponse.class)
public interface TransferResponseDef {

    UUID sourceAccountId();

    String sourceAccountNumber();


    String targetAccountNumber();

    BigDecimal amount();

    String currency();

    Instant occurredOn();

    String status();
}
