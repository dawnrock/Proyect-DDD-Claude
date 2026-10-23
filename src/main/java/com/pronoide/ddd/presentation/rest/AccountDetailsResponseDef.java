package com.pronoide.ddd.presentation.rest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.immutables.value.Value;

import com.pronoide.ddd.application.ApplicationValue;

import tools.jackson.databind.annotation.JsonSerialize;

/** Respuesta REST con el detalle de una cuenta y sus movimientos (esquema {@code AccountDetails} del OpenAPI). */
@ApplicationValue
@Value.Immutable
@JsonSerialize(as = AccountDetailsResponse.class)
public interface AccountDetailsResponseDef {

    UUID id();

    String accountNumber();

    String holderName();

    String segment();

    BigDecimal balance();

    String currency();

    List<TransactionResponse> transactions();
}
