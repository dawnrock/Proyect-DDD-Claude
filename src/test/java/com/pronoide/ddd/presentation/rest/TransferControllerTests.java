package com.pronoide.ddd.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pronoide.ddd.presentation.SecuritySupport;
import com.pronoide.ddd.application.AccountNotFoundException;
import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.application.TransferCommand;
import com.pronoide.ddd.application.TransferSummary;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CurrencyMismatchException;
import com.pronoide.ddd.domain.model.SameAccountTransferException;

@WebMvcTest(TransferController.class)
@Import({ApiSecurityConfig.class, SecuritySupport.class})
@DisplayName("POST /api/transfers")
class TransferControllerTests {

    private static final UUID SOURCE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID TARGET_ID = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final String BODY = """
            {"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 25.00, "currency": "EUR"}"""
            .formatted(SOURCE_ID, TARGET_ID);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CheckingAccountService service;

    private static TransferSummary transfer(String status) {
        return TransferSummary.builder()
                .sourceAccountId(SOURCE_ID).sourceAccountNumber("ES9121000418450200051332")
                .targetAccountId(TARGET_ID).targetAccountNumber("ES7921000813610123456789")
                .amount(new BigDecimal("25.00")).currency("EUR")
                .occurredOn(Instant.parse("2026-10-25T10:15:30Z")).status(status).build();
    }

    @Test
    @DisplayName("Una transferencia aprobada responde 201 con la transferencia APPROVED y sin Location")
    void una_transferencia_aprobada_responde_201_con_la_transferencia_approved_y_sin_location() throws Exception {
        given(service.transfer(any())).willReturn(transfer("APPROVED"));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.sourceAccountId").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.sourceAccountNumber").value("ES9121000418450200051332"))
                .andExpect(jsonPath("$.targetAccountId").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.targetAccountNumber").value("ES7921000813610123456789"))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.occurredOn").exists())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        ArgumentCaptor<TransferCommand> captor = ArgumentCaptor.forClass(TransferCommand.class);
        verify(service).transfer(captor.capture());
        assertThat(captor.getValue().sourceAccountId()).isEqualTo(SOURCE_ID);
        assertThat(captor.getValue().targetAccountId()).isEqualTo(TARGET_ID);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("25.00");
        assertThat(captor.getValue().currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Una transferencia rechazada no es un error: responde 201 con la transferencia REJECTED")
    void una_transferencia_rechazada_responde_201_con_la_transferencia_rejected() throws Exception {
        given(service.transfer(any())).willReturn(transfer("REJECTED"));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("Sin divisa se usa EUR por defecto")
    void sin_divisa_se_usa_eur_por_defecto() throws Exception {
        given(service.transfer(any())).willReturn(transfer("APPROVED"));
        String body = """
                {"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 25.00}"""
                .formatted(SOURCE_ID, TARGET_ID);

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        ArgumentCaptor<TransferCommand> captor = ArgumentCaptor.forClass(TransferCommand.class);
        verify(service).transfer(captor.capture());
        assertThat(captor.getValue().currency()).isEqualTo("EUR");
    }

    @ParameterizedTest(name = "cuerpo {0}")
    @DisplayName("Ids ausentes o un importe cero o negativo responden 400 con los errores de campo")
    @ValueSource(strings = {
            "{\"targetAccountId\": \"66666666-7777-8888-9999-000000000000\", \"amount\": 10}",
            "{\"sourceAccountId\": \"11111111-2222-3333-4444-555555555555\", \"amount\": 10}",
            "{\"sourceAccountId\": \"11111111-2222-3333-4444-555555555555\","
                    + " \"targetAccountId\": \"66666666-7777-8888-9999-000000000000\", \"amount\": 0}",
            "{\"sourceAccountId\": \"11111111-2222-3333-4444-555555555555\","
                    + " \"targetAccountId\": \"66666666-7777-8888-9999-000000000000\", \"amount\": -5}",
            "{\"sourceAccountId\": \"11111111-2222-3333-4444-555555555555\","
                    + " \"targetAccountId\": \"66666666-7777-8888-9999-000000000000\"}",
            "{\"sourceAccountId\": \"11111111-2222-3333-4444-555555555555\","
                    + " \"targetAccountId\": \"66666666-7777-8888-9999-000000000000\","
                    + " \"amount\": 10, \"currency\": \"eur\"}"})
    void ids_ausentes_o_importe_no_positivo_responden_400(String body) throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"))
                .andExpect(jsonPath("$.errors").isMap());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Una cuenta desconocida responde 404 Recurso inexistente")
    void una_cuenta_desconocida_responde_404_recurso_inexistente() throws Exception {
        given(service.transfer(any())).willThrow(new AccountNotFoundException(TARGET_ID));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso inexistente"));
    }

    @Test
    @DisplayName("Transferir a la misma cuenta responde 422 Regla de negocio incumplida")
    void transferir_a_la_misma_cuenta_responde_422() throws Exception {
        given(service.transfer(any()))
                .willThrow(new SameAccountTransferException(new AccountNumber("ES9121000418450200051332")));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"));
    }

    @Test
    @DisplayName("Una divisa distinta a la de las cuentas responde 422 Regla de negocio incumplida")
    void una_divisa_distinta_a_la_de_las_cuentas_responde_422() throws Exception {
        given(service.transfer(any())).willThrow(
                new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD")));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"));
    }

    @Test
    @DisplayName("Una divisa ISO inexistente rechazada por el command responde 400 Petición no válida")
    void una_divisa_iso_inexistente_responde_400() throws Exception {
        String body = """
                {"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 10, "currency": "XYZ"}"""
                .formatted(SOURCE_ID, TARGET_ID);

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"));
    }
}
