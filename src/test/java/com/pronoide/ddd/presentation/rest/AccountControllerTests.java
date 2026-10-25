package com.pronoide.ddd.presentation.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import com.pronoide.ddd.application.AccountDetails;
import com.pronoide.ddd.application.AccountNotFoundException;
import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.Caller;
import com.pronoide.ddd.application.ForbiddenOperationException;
import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.application.DepositCommand;
import com.pronoide.ddd.application.OpenAccountCommand;
import com.pronoide.ddd.application.TransactionSummary;
import com.pronoide.ddd.application.WithdrawCommand;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CurrencyMismatchException;
import com.pronoide.ddd.domain.model.DuplicateAccountNumberException;
import com.pronoide.ddd.domain.model.InsufficientInitialDepositException;
import com.pronoide.ddd.domain.model.MonetaryAmount;

@WithMockUser(username = "admin", roles = "ADMIN")
@WebMvcTest(AccountController.class)
@Import({ApiSecurityConfig.class, SecuritySupport.class})
class AccountControllerTests {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String IBAN = "ES9121000418450200051332";
    private static final String VALID_BODY = """
            {"accountNumber": "es91 2100 0418 4502 0005 1332", "holderName": "Ada Lovelace",
             "segment": "RETAIL", "initialDeposit": 100.00, "ownerUsername": "ada"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CheckingAccountService service;

    private static AccountSummary summary() {
        return AccountSummary.builder().id(ID).accountNumber(IBAN).holderName("Ada Lovelace")
                .balance(new BigDecimal("100.00")).currency("EUR").build();
    }

    @Nested
    @DisplayName("POST /api/accounts")
    class OpenAccount {

        @Test
        @DisplayName("Una petición válida responde 201 con Location y el resumen de la cuenta")
        void una_peticion_valida_responde_201_con_location_y_el_resumen_de_la_cuenta() throws Exception {
            given(service.openAccount(any(Caller.class), any())).willReturn(summary());

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/accounts/" + ID))
                    .andExpect(jsonPath("$.id").value(ID.toString()))
                    .andExpect(jsonPath("$.accountNumber").value(IBAN))
                    .andExpect(jsonPath("$.holderName").value("Ada Lovelace"))
                    .andExpect(jsonPath("$.balance").value(100.00))
                    .andExpect(jsonPath("$.currency").value("EUR"));
        }

        @Test
        @DisplayName("La petición se convierte en un command con EUR por defecto")
        void la_peticion_se_convierte_en_un_command_con_eur_por_defecto() throws Exception {
            given(service.openAccount(any(Caller.class), any())).willReturn(summary());

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated());

            ArgumentCaptor<OpenAccountCommand> captor = ArgumentCaptor.forClass(OpenAccountCommand.class);
            verify(service).openAccount(any(Caller.class), captor.capture());
            OpenAccountCommand command = captor.getValue();
            assertThat(command.accountNumber()).isEqualTo("es91 2100 0418 4502 0005 1332");
            assertThat(command.holderName()).isEqualTo("Ada Lovelace");
            assertThat(command.segment()).isEqualTo("RETAIL");
            assertThat(command.initialDeposit()).isEqualByComparingTo("100.00");
            assertThat(command.currency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Una divisa indicada llega al command")
        void una_divisa_indicada_llega_al_command() throws Exception {
            given(service.openAccount(any(Caller.class), any())).willReturn(summary());

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("""
                    {"accountNumber": "ES9121000418450200051332", "holderName": "Ada Lovelace",
                     "segment": "HIGH_NET_WORTH", "initialDeposit": 100, "currency": "USD", "ownerUsername": "ada"}"""))
                    .andExpect(status().isCreated());

            ArgumentCaptor<OpenAccountCommand> captor = ArgumentCaptor.forClass(OpenAccountCommand.class);
            verify(service).openAccount(any(Caller.class), captor.capture());
            assertThat(captor.getValue().currency()).isEqualTo("USD");
            assertThat(captor.getValue().segment()).isEqualTo("HIGH_NET_WORTH");
        }

        @Test
        @DisplayName("Sin titular responde 400 con el error del campo y no llama al servicio")
        void sin_titular_responde_400_con_el_error_del_campo_y_no_llama_al_servicio() throws Exception {
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("""
                    {"accountNumber": "ES9121000418450200051332", "segment": "RETAIL", "initialDeposit": 100}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Petición no válida"))
                    .andExpect(jsonPath("$.errors.holderName").exists());

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Todos los campos mal formados aparecen en el mapa de errores")
        void todos_los_campos_mal_formados_aparecen_en_el_mapa_de_errores() throws Exception {
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("""
                    {"accountNumber": "ES12", "holderName": " ", "segment": "VIP",
                     "initialDeposit": -5, "currency": "eur", "ownerUsername": "Ada L"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.accountNumber").exists())
                    .andExpect(jsonPath("$.errors.holderName").exists())
                    .andExpect(jsonPath("$.errors.segment").exists())
                    .andExpect(jsonPath("$.errors.initialDeposit").exists())
                    .andExpect(jsonPath("$.errors.currency").exists())
                    .andExpect(jsonPath("$.errors.ownerUsername").exists());

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Un cuerpo vacío responde 400 con un error por cada campo obligatorio")
        void un_cuerpo_vacio_responde_400_con_un_error_por_cada_campo_obligatorio() throws Exception {
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.accountNumber").exists())
                    .andExpect(jsonPath("$.errors.holderName").exists())
                    .andExpect(jsonPath("$.errors.segment").exists())
                    .andExpect(jsonPath("$.errors.initialDeposit").exists())
                    .andExpect(jsonPath("$.errors.ownerUsername").exists())
                    .andExpect(jsonPath("$.errors.currency").doesNotExist());
        }

        @Test
        @DisplayName("Un depósito insuficiente responde 422 con el mensaje del dominio")
        void un_deposito_insuficiente_responde_422_con_el_mensaje_del_dominio() throws Exception {
            given(service.openAccount(any(Caller.class), any())).willThrow(new InsufficientInitialDepositException(
                    MonetaryAmount.euros("10.00"), MonetaryAmount.euros("50.00")));

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"))
                    .andExpect(jsonPath("$.detail").value(
                            "El depósito inicial de 10.00 EUR no alcanza el mínimo exigido de 50.00 EUR"));
        }

        @Test
        @DisplayName("Un número de cuenta duplicado responde 422")
        void un_numero_de_cuenta_duplicado_responde_422() throws Exception {
            given(service.openAccount(any(Caller.class), any())).willThrow(new DuplicateAccountNumberException(new AccountNumber(IBAN)));

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"));
        }

        @Test
        @DisplayName("Una divisa bien formada pero desconocida que escapa a la validación responde 400")
        void una_divisa_bien_formada_pero_desconocida_responde_400() throws Exception {
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("""
                    {"accountNumber": "ES9121000418450200051332", "holderName": "Ada Lovelace",
                     "segment": "RETAIL", "initialDeposit": 100, "currency": "XYZ", "ownerUsername": "ada"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Petición no válida"));
        }
    }

    @Nested
    @DisplayName("Seguridad (#50)")
    class Security {

        private static final Caller ADA = Caller.builder().username("ada").admin(false).build();
        private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();

        @Test
        @DisplayName("Sin credenciales cualquier operación responde 401 No autenticado y no llama al servicio")
        @WithAnonymousUser
        void sin_credenciales_cualquier_operacion_responde_401_no_autenticado() throws Exception {
            mvc.perform(get("/api/accounts"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("No autenticado"));
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Un usuario que abre una cuenta recibe 403 Acceso denegado y no llama al servicio")
        @WithMockUser(username = "ada", roles = "USER")
        void un_usuario_que_abre_una_cuenta_recibe_403_acceso_denegado() throws Exception {
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Acceso denegado"));

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Si el servicio lanza ForbiddenOperationException la respuesta es 403 Acceso denegado")
        void si_el_servicio_lanza_forbidden_operation_exception_la_respuesta_es_403() throws Exception {
            given(service.openAccount(any(Caller.class), any())).willThrow(new ForbiddenOperationException("abrir cuentas"));

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Acceso denegado"));
        }

        @Test
        @DisplayName("Un usuario consulta una cuenta ajena: el servicio la trata como inexistente y responde 404")
        @WithMockUser(username = "ada", roles = "USER")
        void un_usuario_consulta_una_cuenta_ajena_responde_404() throws Exception {
            given(service.findAccount(ADA, ID)).willThrow(new AccountNotFoundException(ID));

            mvc.perform(get("/api/accounts/{id}", ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Recurso inexistente"));
        }

        @Test
        @DisplayName("El controlador pasa al servicio el Caller construido con la autenticación")
        @WithMockUser(username = "ada", roles = "USER")
        void el_controlador_pasa_al_servicio_el_caller_de_la_autenticacion() throws Exception {
            given(service.listAccounts(ADA)).willReturn(List.of(summary()));

            mvc.perform(get("/api/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("Un administrador abre la cuenta y el command lleva el usuario propietario")
        void un_administrador_abre_la_cuenta_y_el_command_lleva_el_usuario_propietario() throws Exception {
            given(service.openAccount(eq(ADMIN), any())).willReturn(summary());

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated());

            ArgumentCaptor<OpenAccountCommand> captor = ArgumentCaptor.forClass(OpenAccountCommand.class);
            verify(service).openAccount(eq(ADMIN), captor.capture());
            assertThat(captor.getValue().ownerUsername()).isEqualTo("ada");
        }
    }

    @Nested
    @DisplayName("GET /api/accounts")
    class ListAccounts {

        @Test
        @DisplayName("Responde 200 con un array de resúmenes")
        void responde_200_con_un_array_de_resumenes() throws Exception {
            given(service.listAccounts(any(Caller.class))).willReturn(List.of(summary()));

            mvc.perform(get("/api/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(ID.toString()))
                    .andExpect(jsonPath("$[0].accountNumber").value(IBAN))
                    .andExpect(jsonPath("$[0].balance").value(100.00));
        }

        @Test
        @DisplayName("Sin cuentas responde 200 con un array vacío")
        void sin_cuentas_responde_200_con_un_array_vacio() throws Exception {
            given(service.listAccounts(any(Caller.class))).willReturn(List.of());

            mvc.perform(get("/api/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/accounts/{id}")
    class FindAccount {

        @Test
        @DisplayName("Responde 200 con el detalle y sus movimientos")
        void responde_200_con_el_detalle_y_sus_movimientos() throws Exception {
            UUID transactionId = UUID.fromString("66666666-7777-8888-9999-000000000000");
            given(service.findAccount(any(Caller.class), eq(ID))).willReturn(AccountDetails.builder()
                    .id(ID).accountNumber(IBAN).holderName("Ada Lovelace").segment("RETAIL")
                    .balance(new BigDecimal("100.00")).currency("EUR")
                    .addTransactions(TransactionSummary.builder().id(transactionId).type("DEPOSIT")
                            .amount(new BigDecimal("100.00")).currency("EUR")
                            .occurredOn(Instant.parse("2026-10-23T10:15:30Z")).status("APPROVED").build())
                    .build());

            mvc.perform(get("/api/accounts/{id}", ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ID.toString()))
                    .andExpect(jsonPath("$.segment").value("RETAIL"))
                    .andExpect(jsonPath("$.transactions.length()").value(1))
                    .andExpect(jsonPath("$.transactions[0].id").value(transactionId.toString()))
                    .andExpect(jsonPath("$.transactions[0].type").value("DEPOSIT"))
                    .andExpect(jsonPath("$.transactions[0].amount").value(100.00))
                    .andExpect(jsonPath("$.transactions[0].currency").value("EUR"))
                    .andExpect(jsonPath("$.transactions[0].occurredOn").value("2026-10-23T10:15:30Z"))
                    .andExpect(jsonPath("$.transactions[0].status").value("APPROVED"));
        }

        @Test
        @DisplayName("Un id desconocido responde 404 Recurso inexistente")
        void un_id_desconocido_responde_404_recurso_inexistente() throws Exception {
            given(service.findAccount(any(Caller.class), eq(ID))).willThrow(new AccountNotFoundException(ID));

            mvc.perform(get("/api/accounts/{id}", ID))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Recurso inexistente"))
                    .andExpect(jsonPath("$.detail").value("No existe la cuenta " + ID));
        }

        @Test
        @DisplayName("Un id que no es un UUID responde 400")
        void un_id_que_no_es_un_uuid_responde_400() throws Exception {
            mvc.perform(get("/api/accounts/{id}", "no-es-un-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Petición no válida"));

            verifyNoInteractions(service);
        }
    }

    @Nested
    @DisplayName("POST /api/accounts/{id}/deposits")
    class Deposit {

        private static final String BODY = "{\"amount\": 25.50, \"currency\": \"EUR\"}";

        private TransactionSummary approvedDeposit() {
            return TransactionSummary.builder().id(UUID.fromString("66666666-7777-8888-9999-000000000000"))
                    .type("DEPOSIT").amount(new BigDecimal("25.50")).currency("EUR")
                    .occurredOn(Instant.parse("2026-10-23T10:15:30Z")).status("APPROVED").build();
        }

        @Test
        @DisplayName("Una petición válida responde 201 con el movimiento y sin Location")
        void una_peticion_valida_responde_201_con_el_movimiento_y_sin_location() throws Exception {
            given(service.deposit(any(Caller.class), any())).willReturn(approvedDeposit());

            mvc.perform(post("/api/accounts/{id}/deposits", ID).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(jsonPath("$.type").value("DEPOSIT"))
                    .andExpect(jsonPath("$.amount").value(25.50))
                    .andExpect(jsonPath("$.currency").value("EUR"))
                    .andExpect(jsonPath("$.occurredOn").value("2026-10-23T10:15:30Z"))
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            ArgumentCaptor<DepositCommand> captor = ArgumentCaptor.forClass(DepositCommand.class);
            verify(service).deposit(any(Caller.class), captor.capture());
            assertThat(captor.getValue().accountId()).isEqualTo(ID);
            assertThat(captor.getValue().amount()).isEqualByComparingTo("25.50");
            assertThat(captor.getValue().currency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Sin divisa se usa EUR")
        void sin_divisa_se_usa_eur() throws Exception {
            given(service.deposit(any(Caller.class), any())).willReturn(approvedDeposit());

            mvc.perform(post("/api/accounts/{id}/deposits", ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 25.50}"))
                    .andExpect(status().isCreated());

            ArgumentCaptor<DepositCommand> captor = ArgumentCaptor.forClass(DepositCommand.class);
            verify(service).deposit(any(Caller.class), captor.capture());
            assertThat(captor.getValue().currency()).isEqualTo("EUR");
        }

        @ParameterizedTest(name = "importe {0}")
        @DisplayName("Un importe cero o negativo responde 400 con el error del campo")
        @ValueSource(strings = {"0", "-5"})
        void un_importe_cero_o_negativo_responde_400_con_el_error_del_campo(String amount) throws Exception {
            mvc.perform(post("/api/accounts/{id}/deposits", ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": " + amount + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Petición no válida"))
                    .andExpect(jsonPath("$.errors.amount").exists());

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Sin importe y con la divisa mal formada responde 400 con ambos errores")
        void sin_importe_y_con_la_divisa_mal_formada_responde_400_con_ambos_errores() throws Exception {
            mvc.perform(post("/api/accounts/{id}/deposits", ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currency\": \"eur\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.amount").exists())
                    .andExpect(jsonPath("$.errors.currency").exists());

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Una cuenta desconocida responde 404 Recurso inexistente")
        void una_cuenta_desconocida_responde_404_recurso_inexistente() throws Exception {
            given(service.deposit(any(Caller.class), any())).willThrow(new AccountNotFoundException(ID));

            mvc.perform(post("/api/accounts/{id}/deposits", ID).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Recurso inexistente"));
        }

        @Test
        @DisplayName("Una divisa distinta a la de la cuenta responde 422 Regla de negocio incumplida")
        void una_divisa_distinta_a_la_de_la_cuenta_responde_422() throws Exception {
            given(service.deposit(any(Caller.class), any())).willThrow(
                    new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD")));

            mvc.perform(post("/api/accounts/{id}/deposits", ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 10, \"currency\": \"USD\"}"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"));
        }

        @Test
        @DisplayName("Una divisa bien formada pero desconocida responde 400")
        void una_divisa_bien_formada_pero_desconocida_responde_400() throws Exception {
            mvc.perform(post("/api/accounts/{id}/deposits", ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 10, \"currency\": \"XYZ\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Petición no válida"));
        }
    }

    @Nested
    @DisplayName("POST /api/accounts/{id}/withdrawals")
    class Withdraw {

        private static final String BODY = "{\"amount\": 25.50, \"currency\": \"EUR\"}";

        private TransactionSummary withdrawal(String status) {
            return TransactionSummary.builder().id(UUID.fromString("66666666-7777-8888-9999-000000000000"))
                    .type("WITHDRAWAL").amount(new BigDecimal("25.50")).currency("EUR")
                    .occurredOn(Instant.parse("2026-10-23T10:15:30Z")).status(status).build();
        }

        @Test
        @DisplayName("Una retirada aprobada responde 201 con el movimiento APPROVED y sin Location")
        void una_retirada_aprobada_responde_201_con_el_movimiento_approved_y_sin_location() throws Exception {
            given(service.withdraw(any(Caller.class), any())).willReturn(withdrawal("APPROVED"));

            mvc.perform(post("/api/accounts/{id}/withdrawals", ID).contentType(MediaType.APPLICATION_JSON)
                    .content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                    .andExpect(jsonPath("$.amount").value(25.50))
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            ArgumentCaptor<WithdrawCommand> captor = ArgumentCaptor.forClass(WithdrawCommand.class);
            verify(service).withdraw(any(Caller.class), captor.capture());
            assertThat(captor.getValue().accountId()).isEqualTo(ID);
            assertThat(captor.getValue().amount()).isEqualByComparingTo("25.50");
            assertThat(captor.getValue().currency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Una retirada rechazada no es un error: responde 201 con el movimiento REJECTED")
        void una_retirada_rechazada_responde_201_con_el_movimiento_rejected() throws Exception {
            given(service.withdraw(any(Caller.class), any())).willReturn(withdrawal("REJECTED"));

            mvc.perform(post("/api/accounts/{id}/withdrawals", ID).contentType(MediaType.APPLICATION_JSON)
                    .content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("REJECTED"));
        }

        @ParameterizedTest(name = "importe {0}")
        @DisplayName("Un importe cero o negativo responde 400 con el error del campo")
        @ValueSource(strings = {"0", "-5"})
        void un_importe_cero_o_negativo_responde_400_con_el_error_del_campo(String amount) throws Exception {
            mvc.perform(post("/api/accounts/{id}/withdrawals", ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": " + amount + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Petición no válida"))
                    .andExpect(jsonPath("$.errors.amount").exists());

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Una cuenta desconocida responde 404 Recurso inexistente")
        void una_cuenta_desconocida_responde_404_recurso_inexistente() throws Exception {
            given(service.withdraw(any(Caller.class), any())).willThrow(new AccountNotFoundException(ID));

            mvc.perform(post("/api/accounts/{id}/withdrawals", ID).contentType(MediaType.APPLICATION_JSON)
                    .content(BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Recurso inexistente"));
        }

        @Test
        @DisplayName("Una divisa distinta a la de la cuenta responde 422 Regla de negocio incumplida")
        void una_divisa_distinta_a_la_de_la_cuenta_responde_422() throws Exception {
            given(service.withdraw(any(Caller.class), any())).willThrow(
                    new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD")));

            mvc.perform(post("/api/accounts/{id}/withdrawals", ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 10, \"currency\": \"USD\"}"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"));
        }
    }
}
