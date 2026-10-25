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
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pronoide.ddd.presentation.SecuritySupport;
import com.pronoide.ddd.application.AccountNotFoundException;
import com.pronoide.ddd.application.Caller;
import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.application.TargetAccountNotFoundException;
import com.pronoide.ddd.application.TransferCommand;
import com.pronoide.ddd.application.TransferSummary;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CurrencyMismatchException;
import com.pronoide.ddd.domain.model.SameAccountTransferException;

@WithMockUser(username = "admin", roles = "ADMIN")
@WebMvcTest(TransferController.class)
@Import({ApiSecurityConfig.class, SecuritySupport.class})
@DisplayName("POST /api/transfers")
class TransferControllerTests {

    private static final UUID SOURCE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID TARGET_ID = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final String TARGET_IBAN = "ES7921000813610123456789";
    private static final String BODY = """
            {"sourceAccountId": "%s", "targetAccountNumber": "%s", "amount": 25.00, "currency": "EUR"}"""
            .formatted(SOURCE_ID, TARGET_IBAN);

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
    @WithAnonymousUser
    @DisplayName("Sin credenciales responde 401 No autenticado y no llama al servicio")
    void sin_credenciales_responde_401_no_autenticado_y_no_llama_al_servicio() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Una transferencia aprobada responde 201 con la transferencia APPROVED y sin Location")
    void una_transferencia_aprobada_responde_201_con_la_transferencia_approved_y_sin_location() throws Exception {
        given(service.transfer(any(Caller.class), any())).willReturn(transfer("APPROVED"));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.sourceAccountId").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.sourceAccountNumber").value("ES9121000418450200051332"))
                .andExpect(jsonPath("$.targetAccountId").doesNotExist())
                .andExpect(jsonPath("$.targetAccountNumber").value("ES7921000813610123456789"))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.occurredOn").exists())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        ArgumentCaptor<TransferCommand> captor = ArgumentCaptor.forClass(TransferCommand.class);
        verify(service).transfer(any(Caller.class), captor.capture());
        assertThat(captor.getValue().sourceAccountId()).isEqualTo(SOURCE_ID);
        assertThat(captor.getValue().targetAccountNumber()).isEqualTo(TARGET_IBAN);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("25.00");
        assertThat(captor.getValue().currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Una transferencia rechazada no es un error: responde 201 con la transferencia REJECTED")
    void una_transferencia_rechazada_responde_201_con_la_transferencia_rejected() throws Exception {
        given(service.transfer(any(Caller.class), any())).willReturn(transfer("REJECTED"));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("Sin divisa se usa EUR por defecto")
    void sin_divisa_se_usa_eur_por_defecto() throws Exception {
        given(service.transfer(any(Caller.class), any())).willReturn(transfer("APPROVED"));
        String body = """
                {"sourceAccountId": "%s", "targetAccountNumber": "%s", "amount": 25.00}"""
                .formatted(SOURCE_ID, TARGET_IBAN);

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        ArgumentCaptor<TransferCommand> captor = ArgumentCaptor.forClass(TransferCommand.class);
        verify(service).transfer(any(Caller.class), captor.capture());
        assertThat(captor.getValue().currency()).isEqualTo("EUR");
    }

    @ParameterizedTest(name = "cuerpo {0}")
    @DisplayName("Ids ausentes o un importe cero o negativo responden 400 con los errores de campo")
    @ValueSource(strings = {
            "{\"targetAccountNumber\": \"ES7921000813610123456789\", \"amount\": 10}",
            "{\"sourceAccountId\": \"11111111-2222-3333-4444-555555555555\", \"amount\": 10}",
            "{\"sourceAccountId\": \"11111111-2222-3333-4444-555555555555\","
                    + " \"targetAccountNumber\": \"ES7921000813610123456789\", \"amount\": 0}",
            "{\"sourceAccountId\": \"11111111-2222-3333-4444-555555555555\","
                    + " \"targetAccountNumber\": \"ES7921000813610123456789\", \"amount\": -5}",
            "{\"sourceAccountId\": \"11111111-2222-3333-4444-555555555555\","
                    + " \"targetAccountNumber\": \"ES7921000813610123456789\"}",
            "{\"sourceAccountId\": \"11111111-2222-3333-4444-555555555555\","
                    + " \"targetAccountNumber\": \"ES7921000813610123456789\","
                    + " \"amount\": 10, \"currency\": \"eur\"}"})
    void ids_ausentes_o_importe_no_positivo_responden_400(String body) throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"))
                .andExpect(jsonPath("$.errors").isMap());

        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "IBAN {0}")
    @DisplayName("Un IBAN de destino ausente o mal formado responde 400 con el error del campo")
    @ValueSource(strings = {"", " ", "ES12", "XX7921000813610123456789", "ES79210008136101234567AB",
            "ES792100081361012345678"})
    void un_iban_de_destino_mal_formado_responde_400(String iban) throws Exception {
        String body = """
                {"sourceAccountId": "%s", "targetAccountNumber": "%s", "amount": 10}"""
                .formatted(SOURCE_ID, iban);

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.targetAccountNumber").exists());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Sin IBAN de destino responde 400 con el error del campo")
    void sin_iban_de_destino_responde_400() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\": \"%s\", \"amount\": 10}".formatted(SOURCE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.targetAccountNumber").exists());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Un IBAN con espacios y minúsculas se acepta y llega tal como se escribió al command")
    void un_iban_con_espacios_y_minusculas_se_acepta() throws Exception {
        given(service.transfer(any(Caller.class), any())).willReturn(transfer("APPROVED"));
        String body = """
                {"sourceAccountId": "%s", "targetAccountNumber": " es79 2100 0813 6101 2345 6789 ", "amount": 10}"""
                .formatted(SOURCE_ID);

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        ArgumentCaptor<TransferCommand> captor = ArgumentCaptor.forClass(TransferCommand.class);
        verify(service).transfer(any(Caller.class), captor.capture());
        assertThat(captor.getValue().targetAccountNumber()).isEqualToIgnoringWhitespace("es79210008136101234567 89");
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Un usuario puede transferir: el servicio recibe su Caller de usuario")
    void un_usuario_puede_transferir_con_su_caller() throws Exception {
        Caller ada = Caller.builder().username("ada").admin(false).build();
        given(service.transfer(any(Caller.class), any())).willReturn(transfer("APPROVED"));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        verify(service).transfer(org.mockito.ArgumentMatchers.eq(ada), any());
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Una cuenta de origen ajena responde 404 Recurso inexistente")
    void una_cuenta_de_origen_ajena_responde_404() throws Exception {
        given(service.transfer(any(Caller.class), any())).willThrow(new AccountNotFoundException(SOURCE_ID));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso inexistente"));
    }

    @Test
    @DisplayName("Un IBAN de destino inexistente responde 404 Recurso inexistente")
    void un_iban_de_destino_inexistente_responde_404() throws Exception {
        given(service.transfer(any(Caller.class), any())).willThrow(new TargetAccountNotFoundException(TARGET_IBAN));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso inexistente"))
                .andExpect(jsonPath("$.detail").value("No existe ninguna cuenta con el número " + TARGET_IBAN));
    }

    @Test
    @DisplayName("Una cuenta desconocida responde 404 Recurso inexistente")
    void una_cuenta_desconocida_responde_404_recurso_inexistente() throws Exception {
        given(service.transfer(any(Caller.class), any())).willThrow(new AccountNotFoundException(TARGET_ID));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso inexistente"));
    }

    @Test
    @DisplayName("Transferir a la misma cuenta responde 422 Regla de negocio incumplida")
    void transferir_a_la_misma_cuenta_responde_422() throws Exception {
        given(service.transfer(any(Caller.class), any()))
                .willThrow(new SameAccountTransferException(new AccountNumber("ES9121000418450200051332")));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"));
    }

    @Test
    @DisplayName("Una divisa distinta a la de las cuentas responde 422 Regla de negocio incumplida")
    void una_divisa_distinta_a_la_de_las_cuentas_responde_422() throws Exception {
        given(service.transfer(any(Caller.class), any())).willThrow(
                new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD")));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"));
    }

    @Test
    @DisplayName("Una divisa ISO inexistente rechazada por el command responde 400 Petición no válida")
    void una_divisa_iso_inexistente_responde_400() throws Exception {
        String body = """
                {"sourceAccountId": "%s", "targetAccountNumber": "%s", "amount": 10, "currency": "XYZ"}"""
                .formatted(SOURCE_ID, TARGET_IBAN);

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"));
    }
}
