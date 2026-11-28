package com.dawnrock.ddd.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import tools.jackson.databind.json.JsonMapper;

/**
 * El JSON exacto de las respuestas de la API (#219): nombres, orden de los campos (alfabético: el de Jackson 3 por
 * defecto) y campos ausentes. Fija lo que escribía Immutables para que el paso a {@code record} no cambie el contrato
 * ({@code docs/api/openapi.yaml}).
 */
@JsonTest
class RecordJsonTests {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID TRANSACTION_ID = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final Instant WHEN = Instant.parse("2026-11-28T10:15:30Z");
    private static final String IBAN = "ES8099990001450000000001";
    private static final String OTHER_IBAN = "ES1099990001410000000002";

    @Autowired
    private JsonMapper json;

    @Test
    @DisplayName("El resumen de una cuenta se escribe con sus seis campos, en orden alfabético")
    void anAccountSummaryKeepsItsJson() {
        AccountSummaryResponse summary = AccountSummaryResponse.builder().id(ID).accountNumber(IBAN)
                .holderName("Ada Lovelace").segment("RETAIL").balance(new BigDecimal("1000.00")).currency("EUR")
                .build();

        assertThat(json.writeValueAsString(summary)).isEqualTo(
                "{\"accountNumber\":\"" + IBAN + "\",\"balance\":1000.00,\"currency\":\"EUR\","
                        + "\"holderName\":\"Ada Lovelace\",\"id\":\"11111111-2222-3333-4444-555555555555\","
                        + "\"segment\":\"RETAIL\"}");
    }

    @Test
    @DisplayName("Un movimiento sin contrapartida omite counterpartAccountNumber y uno de transferencia lo incluye")
    void aTransactionOmitsTheMissingCounterpart() {
        TransactionResponse deposit = TransactionResponse.builder().id(TRANSACTION_ID).type("DEPOSIT")
                .amount(new BigDecimal("200.00")).currency("EUR").occurredOn(WHEN).status("APPROVED").build();
        TransactionResponse transfer = TransactionResponse.builder().id(TRANSACTION_ID).type("WITHDRAWAL")
                .amount(new BigDecimal("1.00")).currency("EUR").occurredOn(WHEN).status("REJECTED")
                .counterpartAccountNumber(OTHER_IBAN).build();

        assertThat(json.writeValueAsString(deposit)).isEqualTo(
                "{\"amount\":200.00,\"currency\":\"EUR\",\"id\":\"66666666-7777-8888-9999-000000000000\","
                        + "\"occurredOn\":\"2026-11-28T10:15:30Z\",\"status\":\"APPROVED\",\"type\":\"DEPOSIT\"}");
        assertThat(json.writeValueAsString(transfer)).isEqualTo(
                "{\"amount\":1.00,\"counterpartAccountNumber\":\"" + OTHER_IBAN + "\",\"currency\":\"EUR\","
                        + "\"id\":\"66666666-7777-8888-9999-000000000000\",\"occurredOn\":\"2026-11-28T10:15:30Z\","
                        + "\"status\":\"REJECTED\",\"type\":\"WITHDRAWAL\"}");
    }

    @Test
    @DisplayName("El detalle de una cuenta lleva sus movimientos como lista, también vacía")
    void accountDetailsKeepTheirJson() {
        TransactionResponse deposit = TransactionResponse.builder().id(TRANSACTION_ID).type("DEPOSIT")
                .amount(new BigDecimal("200.00")).currency("EUR").occurredOn(WHEN).status("APPROVED").build();
        AccountDetailsResponse details = AccountDetailsResponse.builder().id(ID).accountNumber(IBAN)
                .holderName("Ada Lovelace").holderTaxId("12345678Z").holderEmail("ada@example.com")
                .segment("RETAIL").balance(new BigDecimal("1200.00")).currency("EUR")
                .transactions(List.of(deposit)).build();
        AccountDetailsResponse empty = AccountDetailsResponse.builder().id(ID).accountNumber(IBAN)
                .holderName("Ada Lovelace").holderTaxId("12345678Z").holderEmail("")
                .segment("RETAIL").balance(BigDecimal.ZERO).currency("EUR").transactions(List.of()).build();

        assertThat(json.writeValueAsString(details)).isEqualTo(
                "{\"accountNumber\":\"" + IBAN + "\",\"balance\":1200.00,\"currency\":\"EUR\","
                        + "\"holderEmail\":\"ada@example.com\",\"holderName\":\"Ada Lovelace\","
                        + "\"holderTaxId\":\"12345678Z\",\"id\":\"11111111-2222-3333-4444-555555555555\","
                        + "\"segment\":\"RETAIL\",\"transactions\":[{\"amount\":200.00,\"currency\":\"EUR\","
                        + "\"id\":\"66666666-7777-8888-9999-000000000000\",\"occurredOn\":\"2026-11-28T10:15:30Z\","
                        + "\"status\":\"APPROVED\",\"type\":\"DEPOSIT\"}]}");
        assertThat(json.writeValueAsString(empty)).isEqualTo(
                "{\"accountNumber\":\"" + IBAN + "\",\"balance\":0,\"currency\":\"EUR\",\"holderEmail\":\"\","
                        + "\"holderName\":\"Ada Lovelace\",\"holderTaxId\":\"12345678Z\","
                        + "\"id\":\"11111111-2222-3333-4444-555555555555\",\"segment\":\"RETAIL\",\"transactions\":[]}");
    }

    @Test
    @DisplayName("El resultado de una transferencia se escribe con sus siete campos, en orden alfabético")
    void aTransferKeepsItsJson() {
        TransferResponse transfer = TransferResponse.builder().sourceAccountId(ID).sourceAccountNumber(IBAN)
                .targetAccountNumber(OTHER_IBAN).amount(new BigDecimal("300.00")).currency("EUR").occurredOn(WHEN)
                .status("APPROVED").build();

        assertThat(json.writeValueAsString(transfer)).isEqualTo(
                "{\"amount\":300.00,\"currency\":\"EUR\",\"occurredOn\":\"2026-11-28T10:15:30Z\","
                        + "\"sourceAccountId\":\"11111111-2222-3333-4444-555555555555\",\"sourceAccountNumber\":\""
                        + IBAN + "\",\"status\":\"APPROVED\",\"targetAccountNumber\":\"" + OTHER_IBAN + "\"}");
    }

    @Test
    @DisplayName("El token de acceso se escribe con accessToken, tokenType y expiresIn")
    void theAccessTokenKeepsItsJson() {
        TokenResponse token = TokenResponse.builder().accessToken("a.b.c").tokenType("Bearer").expiresIn(900).build();

        assertThat(json.writeValueAsString(token))
                .isEqualTo("{\"accessToken\":\"a.b.c\",\"expiresIn\":900,\"tokenType\":\"Bearer\"}");
    }
}
