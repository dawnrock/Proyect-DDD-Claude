package com.pronoide.ddd.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import org.immutables.value.Value;

import com.pronoide.ddd.application.ApplicationValue;

import tools.jackson.databind.annotation.JsonSerialize;

/** Respuesta REST con el resumen de una cuenta (esquema {@code AccountSummary} del OpenAPI). */
@ApplicationValue
@Value.Immutable
@JsonSerialize(as = AccountSummaryResponse.class)
public interface AccountSummaryResponseDef {

    UUID id();

    String accountNumber();

    String holderName();

    BigDecimal balance();

    String currency();
}
