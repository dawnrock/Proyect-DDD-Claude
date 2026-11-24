package com.dawnrock.ddd.adapter.in.rest;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;
import com.dawnrock.ddd.application.AccountDetails;
import com.dawnrock.ddd.application.AccountNotFoundException;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.ForbiddenOperationException;
import com.dawnrock.ddd.application.CheckingAccountCommands;
import com.dawnrock.ddd.application.CheckingAccountQueries;
import com.dawnrock.ddd.application.DepositCommand;
import com.dawnrock.ddd.application.OpenAccountCommand;
import com.dawnrock.ddd.application.TransactionSummary;
import com.dawnrock.ddd.application.WithdrawCommand;
import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CurrencyMismatchException;
import com.dawnrock.ddd.domain.model.DuplicateAccountNumberException;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.domain.model.MonetaryAmount;

@WithMockUser(username = "admin", roles = "ADMIN")
@WebMvcTest(AccountController.class)
@Import({ApiSecurityConfig.class, SecuritySupport.class, TestUsers.class})
class AccountControllerTests {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String IBAN = "ES9121000418450200051332";
    private static final String VALID_BODY = """
            {"holderTaxId": "12345678Z", "holderName": "Ada Lovelace", "holderEmail": "ada@example.com",
             "initialDeposit": 100.00, "ownerUsername": "ada"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CheckingAccountCommands commands;

    @MockitoBean
    private CheckingAccountQueries queries;

    private static AccountSummary summary() {
        return AccountSummary.builder().id(ID).accountNumber(IBAN).holderName("Ada Lovelace").segment("RETAIL")
                .balance(new BigDecimal("100.00")).currency("EUR").build();
    }

    /** Un importe no válido responde 400 con el error del campo y no llega a los puertos (ingresos y retiradas). */
    private void expectAmountRejected(String path, String amount) throws Exception {
        mvc.perform(post(path, ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": " + amount + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"))
                .andExpect(jsonPath("$.errors.amount").exists());

        verifyNoInteractions(commands, queries);
    }

    @Nested
    @DisplayName("POST /api/accounts")
    class OpenAccount {

        @Test
        @DisplayName("Una petición válida responde 201 con Location y el resumen de la cuenta")
        void aValidRequestResponds201WithLocationAndTheAccountSummary() throws Exception {
            given(commands.openAccount(any(Caller.class), any())).willReturn(summary());

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/accounts/" + ID))
                    .andExpect(jsonPath("$.id").value(ID.toString()))
                    .andExpect(jsonPath("$.accountNumber").value(IBAN))
                    .andExpect(jsonPath("$.holderName").value("Ada Lovelace"))
                    .andExpect(jsonPath("$.segment").value("RETAIL"))
                    .andExpect(jsonPath("$.balance").value(100.00))
                    .andExpect(jsonPath("$.currency").value("EUR"));
        }

        @Test
        @DisplayName("La respuesta lleva el segmento que ha calculado la factoría")
        void theResponseCarriesTheCalculatedSegment() throws Exception {
            given(commands.openAccount(any(Caller.class), any())).willReturn(
                    AccountSummary.builder().id(ID).accountNumber(IBAN).holderName("Grace Hopper")
                            .segment("HIGH_NET_WORTH").balance(new BigDecimal("5000.00")).currency("EUR").build());

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("""
                    {"holderName": "Grace Hopper", "holderTaxId": "87654321X",
                     "initialDeposit": 5000, "ownerUsername": "grace"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accountNumber").value(IBAN))
                    .andExpect(jsonPath("$.segment").value("HIGH_NET_WORTH"));
        }

        @Test
        @DisplayName("La petición se convierte en un command con EUR por defecto")
        void theRequestBecomesACommandWithEurByDefault() throws Exception {
            given(commands.openAccount(any(Caller.class), any())).willReturn(summary());

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated());

            ArgumentCaptor<OpenAccountCommand> captor = ArgumentCaptor.forClass(OpenAccountCommand.class);
            verify(commands).openAccount(any(Caller.class), captor.capture());
            OpenAccountCommand command = captor.getValue();
            assertThat(command.holderName()).isEqualTo("Ada Lovelace");
            assertThat(command.holderTaxId()).isEqualTo("12345678Z");
            assertThat(command.initialDeposit()).isEqualByComparingTo("100.00");
            assertThat(command.currency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Una divisa indicada llega al command")
        void aGivenCurrencyReachesTheCommand() throws Exception {
            given(commands.openAccount(any(Caller.class), any())).willReturn(summary());

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("""
                    {"holderName": "Ada Lovelace", "holderTaxId": " 12345678 z ",
                     "initialDeposit": 100, "currency": "USD", "ownerUsername": "ada"}"""))
                    .andExpect(status().isCreated());

            ArgumentCaptor<OpenAccountCommand> captor = ArgumentCaptor.forClass(OpenAccountCommand.class);
            verify(commands).openAccount(any(Caller.class), captor.capture());
            assertThat(captor.getValue().currency()).isEqualTo("USD");
            assertThat(captor.getValue().holderTaxId()).isEqualTo(" 12345678 z ");
        }

        @Test
        @DisplayName("Sin documento del titular responde 400 con el error del campo y no llama al servicio")
        void withoutDocumentResponds400WithTheFieldErrorAndDoesNotCallTheService() throws Exception {
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("""
                    {"holderName": "Ada Lovelace", "initialDeposit": 100, "ownerUsername": "ada"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Petición no válida"))
                    .andExpect(jsonPath("$.errors.holderTaxId").exists());

            verifyNoInteractions(commands, queries);
        }

        @Test
        @DisplayName("Para un cliente existente basta el documento: nombre, correo y usuario llegan en blanco")
        void forAnExistingCustomerTheDocumentIsEnough() throws Exception {
            given(commands.openAccount(any(Caller.class), any())).willReturn(summary());

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("""
                    {"holderTaxId": "12345678Z", "initialDeposit": 75}"""))
                    .andExpect(status().isCreated());

            ArgumentCaptor<OpenAccountCommand> captor = ArgumentCaptor.forClass(OpenAccountCommand.class);
            verify(commands).openAccount(any(Caller.class), captor.capture());
            assertThat(captor.getValue().holderName()).isEmpty();
            assertThat(captor.getValue().holderEmail()).isEmpty();
            assertThat(captor.getValue().ownerUsername()).isEmpty();
        }

        @Test
        @DisplayName("Unos datos que contradicen al cliente existente responden 400 con el mensaje del servicio")
        void dataContradictingTheExistingCustomerResponds400() throws Exception {
            given(commands.openAccount(any(Caller.class), any())).willThrow(new IllegalArgumentException(
                    "El documento 12345678Z ya es de un cliente: deja en blanco nombre, correo y usuario, o indica los suyos"));

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ya es de un cliente")));
        }

        @Test
        @DisplayName("Todos los campos mal formados aparecen en el mapa de errores")
        void everyMalformedFieldAppearsInTheErrorMap() throws Exception {
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("""
                    {"holderTaxId": "1234", "holderEmail": "ada@",
                     "initialDeposit": -5, "currency": "eur", "ownerUsername": "Ada L"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.holderEmail").exists())
                    .andExpect(jsonPath("$.errors.holderTaxId").value("El documento debe ser un DNI o un NIE"))
                    .andExpect(jsonPath("$.errors.initialDeposit").exists())
                    .andExpect(jsonPath("$.errors.currency").exists())
                    .andExpect(jsonPath("$.errors.ownerUsername").exists());

            verifyNoInteractions(commands, queries);
        }

        @Test
        @DisplayName("Un cuerpo vacío responde 400 solo con los campos siempre obligatorios")
        void anEmptyBodyResponds400OnlyWithTheAlwaysRequiredFields() throws Exception {
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.holderTaxId").exists())
                    .andExpect(jsonPath("$.errors.initialDeposit").exists())
                    .andExpect(jsonPath("$.errors.holderName").doesNotExist())
                    .andExpect(jsonPath("$.errors.holderEmail").doesNotExist())
                    .andExpect(jsonPath("$.errors.ownerUsername").doesNotExist())
                    .andExpect(jsonPath("$.errors.accountNumber").doesNotExist())
                    .andExpect(jsonPath("$.errors.segment").doesNotExist())
                    .andExpect(jsonPath("$.errors.currency").doesNotExist());
        }

        @Test
        @DisplayName("El cuerpo antiguo con IBAN y segmento pero sin documento responde 400 con el error del documento")
        void theOldBodyWithoutTaxIdResponds400() throws Exception {
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("""
                    {"accountNumber": "ES9121000418450200051332", "holderName": "Ada Lovelace",
                     "segment": "RETAIL", "initialDeposit": 100, "ownerUsername": "ada"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.holderTaxId").value("El documento del titular es obligatorio"));

            verifyNoInteractions(commands, queries);
        }

        @ParameterizedTest(name = "depósito inicial {0}")
        @DisplayName("Un depósito inicial que redondea a cero o supera el máximo responde 400 con el error del campo")
        @ValueSource(strings = {"0.004", "1000000000.00", "1.7976931348623155E+308"})
        void anInitialDepositOutOfBoundsResponds400(String deposit) throws Exception {
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY.replace("\"initialDeposit\": 100.00", "\"initialDeposit\": " + deposit)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Petición no válida"))
                    .andExpect(jsonPath("$.errors.initialDeposit").exists());

            verifyNoInteractions(commands, queries);
        }

        @Test
        @DisplayName("Un depósito insuficiente responde 422 con el mensaje del dominio")
        void anInsufficientDepositResponds422WithTheDomainMessage() throws Exception {
            given(commands.openAccount(any(Caller.class), any())).willThrow(new InsufficientInitialDepositException(
                    MonetaryAmount.euros("10.00"), MonetaryAmount.euros("50.00")));

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"))
                    .andExpect(jsonPath("$.detail").value(
                            "El depósito inicial de 10.00 EUR no alcanza el mínimo exigido de 50.00 EUR"));
        }

        @Test
        @DisplayName("Un número de cuenta duplicado responde 422")
        void aDuplicateAccountNumberResponds422() throws Exception {
            given(commands.openAccount(any(Caller.class), any())).willThrow(new DuplicateAccountNumberException(new AccountNumber(IBAN)));

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"));
        }

        @Test
        @DisplayName("Una divisa bien formada pero desconocida que escapa a la validación responde 400")
        void aWellFormedButUnknownCurrencyResponds400() throws Exception {
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("""
                    {"holderName": "Ada Lovelace", "holderTaxId": "12345678Z",
                     "initialDeposit": 100, "currency": "XYZ", "ownerUsername": "ada"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Petición no válida"));
        }
    }

    @Nested
    @DisplayName("Métodos no declarados en el contrato")
    class UndeclaredMethods {

        @ParameterizedTest(name = "{0} /api/accounts")
        @ValueSource(strings = {"QUERY", "PUT", "DELETE"})
        @DisplayName("Un método no declarado, también QUERY, responde 405 con la cabecera Allow")
        void anUndeclaredMethodResponds405WithTheAllowHeader(String method) throws Exception {
            mvc.perform(request(HttpMethod.valueOf(method), "/api/accounts"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", containsString("GET")));

            verifyNoInteractions(commands, queries);
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
        void withoutCredentialsAnyOperationResponds401() throws Exception {
            mvc.perform(get("/api/accounts"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("No autenticado"));
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(commands, queries);
        }

        @Test
        @DisplayName("Un usuario que abre una cuenta recibe 403 Acceso denegado y no llama al servicio")
        @WithMockUser(username = "ada", roles = "USER")
        void aUserOpeningAnAccountGets403() throws Exception {
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Acceso denegado"));

            verifyNoInteractions(commands, queries);
        }

        @Test
        @DisplayName("Si el servicio lanza ForbiddenOperationException la respuesta es 403 Acceso denegado")
        void whenTheServiceThrowsForbiddenOperationExceptionTheResponseIs403() throws Exception {
            given(commands.openAccount(any(Caller.class), any())).willThrow(new ForbiddenOperationException("abrir cuentas"));

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Acceso denegado"));
        }

        @Test
        @DisplayName("Un usuario consulta una cuenta ajena: el servicio la trata como inexistente y responde 404")
        @WithMockUser(username = "ada", roles = "USER")
        void aUserViewingAnotherUsersAccountGets404() throws Exception {
            given(queries.findAccount(ADA, ID)).willThrow(new AccountNotFoundException(ID));

            mvc.perform(get("/api/accounts/{id}", ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Recurso inexistente"));
        }

        @Test
        @DisplayName("El controlador pasa al servicio el Caller construido con la autenticación")
        @WithMockUser(username = "ada", roles = "USER")
        void theControllerPassesTheCallerFromTheAuthenticationToTheService() throws Exception {
            given(queries.listAccounts(ADA)).willReturn(List.of(summary()));

            mvc.perform(get("/api/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("Un administrador abre la cuenta y el command lleva el usuario propietario")
        void anAdministratorOpensTheAccountAndTheCommandCarriesTheOwner() throws Exception {
            given(commands.openAccount(eq(ADMIN), any())).willReturn(summary());

            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated());

            ArgumentCaptor<OpenAccountCommand> captor = ArgumentCaptor.forClass(OpenAccountCommand.class);
            verify(commands).openAccount(eq(ADMIN), captor.capture());
            assertThat(captor.getValue().ownerUsername()).isEqualTo("ada");
        }
    }

    @Nested
    @DisplayName("GET /api/accounts")
    class ListAccounts {

        @Test
        @DisplayName("Responde 200 con un array de resúmenes")
        void responds200WithAnArrayOfSummaries() throws Exception {
            given(queries.listAccounts(any(Caller.class))).willReturn(List.of(summary()));

            mvc.perform(get("/api/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(ID.toString()))
                    .andExpect(jsonPath("$[0].accountNumber").value(IBAN))
                    .andExpect(jsonPath("$[0].balance").value(100.00));
        }

        @Test
        @DisplayName("Sin cuentas responde 200 con un array vacío")
        void withoutAccountsResponds200WithAnEmptyArray() throws Exception {
            given(queries.listAccounts(any(Caller.class))).willReturn(List.of());

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
        void responds200WithTheDetailsAndTheirTransactions() throws Exception {
            UUID transactionId = UUID.fromString("66666666-7777-8888-9999-000000000000");
            given(queries.findAccount(any(Caller.class), eq(ID))).willReturn(AccountDetails.builder()
                    .id(ID).accountNumber(IBAN).holderName("Ada Lovelace").holderTaxId("12345678Z")
                    .holderEmail("ada@example.com").segment("RETAIL")
                    .balance(new BigDecimal("100.00")).currency("EUR")
                    .addTransactions(TransactionSummary.builder().id(transactionId).type("DEPOSIT")
                            .amount(new BigDecimal("100.00")).currency("EUR")
                            .occurredOn(Instant.parse("2026-10-23T10:15:30Z")).status("APPROVED").build())
                    .build());

            mvc.perform(get("/api/accounts/{id}", ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ID.toString()))
                    .andExpect(jsonPath("$.segment").value("RETAIL"))
                    .andExpect(jsonPath("$.holderTaxId").value("12345678Z"))
                    .andExpect(jsonPath("$.holderEmail").value("ada@example.com"))
                    .andExpect(jsonPath("$.transactions.length()").value(1))
                    .andExpect(jsonPath("$.transactions[0].id").value(transactionId.toString()))
                    .andExpect(jsonPath("$.transactions[0].type").value("DEPOSIT"))
                    .andExpect(jsonPath("$.transactions[0].amount").value(100.00))
                    .andExpect(jsonPath("$.transactions[0].currency").value("EUR"))
                    .andExpect(jsonPath("$.transactions[0].occurredOn").value("2026-10-23T10:15:30Z"))
                    .andExpect(jsonPath("$.transactions[0].status").value("APPROVED"));
        }

        @Test
        @DisplayName("Un movimiento de transferencia lleva counterpartAccountNumber y los demás no incluyen la clave")
        void aTransferTransactionCarriesTheCounterpartAndTheOthersOmitTheKey() throws Exception {
            given(queries.findAccount(any(Caller.class), eq(ID))).willReturn(AccountDetails.builder()
                    .id(ID).accountNumber(IBAN).holderName("Ada Lovelace").segment("RETAIL")
                    .balance(new BigDecimal("0.00")).currency("EUR")
                    .addTransactions(TransactionSummary.builder().id(UUID.randomUUID()).type("DEPOSIT")
                            .amount(new BigDecimal("100.00")).currency("EUR")
                            .occurredOn(Instant.parse("2026-10-23T10:15:30Z")).status("APPROVED").build())
                    .addTransactions(TransactionSummary.builder().id(UUID.randomUUID()).type("WITHDRAWAL")
                            .amount(new BigDecimal("100.00")).currency("EUR")
                            .occurredOn(Instant.parse("2026-10-23T10:16:30Z")).status("APPROVED")
                            .counterpartAccountNumber("ES7921000813610123456789").build())
                    .build());

            mvc.perform(get("/api/accounts/{id}", ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transactions[0].counterpartAccountNumber").doesNotExist())
                    .andExpect(jsonPath("$.transactions[1].counterpartAccountNumber").value("ES7921000813610123456789"));
        }

        @Test
        @DisplayName("Un id desconocido responde 404 Recurso inexistente")
        void anUnknownIdResponds404() throws Exception {
            given(queries.findAccount(any(Caller.class), eq(ID))).willThrow(new AccountNotFoundException(ID));

            mvc.perform(get("/api/accounts/{id}", ID))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Recurso inexistente"))
                    .andExpect(jsonPath("$.detail").value("No existe la cuenta " + ID));
        }

        @Test
        @DisplayName("Un id que no es un UUID responde 400")
        void anIdThatIsNotAUuidResponds400() throws Exception {
            mvc.perform(get("/api/accounts/{id}", "no-es-un-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Petición no válida"));

            verifyNoInteractions(commands, queries);
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
        void aValidRequestResponds201WithTheTransactionAndNoLocation() throws Exception {
            given(commands.deposit(any(Caller.class), any())).willReturn(approvedDeposit());

            mvc.perform(post("/api/accounts/{id}/deposits", ID).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(jsonPath("$.type").value("DEPOSIT"))
                    .andExpect(jsonPath("$.amount").value(25.50))
                    .andExpect(jsonPath("$.currency").value("EUR"))
                    .andExpect(jsonPath("$.occurredOn").value("2026-10-23T10:15:30Z"))
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            ArgumentCaptor<DepositCommand> captor = ArgumentCaptor.forClass(DepositCommand.class);
            verify(commands).deposit(any(Caller.class), captor.capture());
            assertThat(captor.getValue().accountId()).isEqualTo(ID);
            assertThat(captor.getValue().amount()).isEqualByComparingTo("25.50");
            assertThat(captor.getValue().currency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Sin divisa se usa EUR")
        void withoutCurrencyEurIsUsed() throws Exception {
            given(commands.deposit(any(Caller.class), any())).willReturn(approvedDeposit());

            mvc.perform(post("/api/accounts/{id}/deposits", ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 25.50}"))
                    .andExpect(status().isCreated());

            ArgumentCaptor<DepositCommand> captor = ArgumentCaptor.forClass(DepositCommand.class);
            verify(commands).deposit(any(Caller.class), captor.capture());
            assertThat(captor.getValue().currency()).isEqualTo("EUR");
        }

        @ParameterizedTest(name = "importe {0}")
        @DisplayName("Un importe que redondea a cero o supera el máximo responde 400 con el error del campo")
        @ValueSource(strings = {"0.004", "1000000000.00", "1.7976931348623155E+308"})
        void anAmountOutOfBoundsResponds400WithTheFieldError(String amount) throws Exception {
            expectAmountRejected("/api/accounts/{id}/deposits", amount);
        }

        @ParameterizedTest(name = "importe {0}")
        @DisplayName("Un importe cero o negativo responde 400 con el error del campo")
        @ValueSource(strings = {"0", "-5"})
        void aZeroOrNegativeAmountResponds400WithTheFieldError(String amount) throws Exception {
            expectAmountRejected("/api/accounts/{id}/deposits", amount);
        }

        @Test
        @DisplayName("Sin importe y con la divisa mal formada responde 400 con ambos errores")
        void withoutAmountAndAMalformedCurrencyResponds400WithBothErrors() throws Exception {
            mvc.perform(post("/api/accounts/{id}/deposits", ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currency\": \"eur\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.amount").exists())
                    .andExpect(jsonPath("$.errors.currency").exists());

            verifyNoInteractions(commands, queries);
        }

        @Test
        @DisplayName("Una cuenta desconocida responde 404 Recurso inexistente")
        void anUnknownAccountResponds404() throws Exception {
            given(commands.deposit(any(Caller.class), any())).willThrow(new AccountNotFoundException(ID));

            mvc.perform(post("/api/accounts/{id}/deposits", ID).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Recurso inexistente"));
        }

        @Test
        @DisplayName("Una divisa distinta a la de la cuenta responde 422 Regla de negocio incumplida")
        void aCurrencyDifferentFromTheAccountsResponds422() throws Exception {
            given(commands.deposit(any(Caller.class), any())).willThrow(
                    new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD")));

            mvc.perform(post("/api/accounts/{id}/deposits", ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 10, \"currency\": \"USD\"}"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"));
        }

        @Test
        @DisplayName("Una divisa bien formada pero desconocida responde 400")
        void aWellFormedButUnknownCurrencyResponds400() throws Exception {
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
        void anApprovedWithdrawalResponds201WithTheApprovedTransactionAndNoLocation() throws Exception {
            given(commands.withdraw(any(Caller.class), any())).willReturn(withdrawal("APPROVED"));

            mvc.perform(post("/api/accounts/{id}/withdrawals", ID).contentType(MediaType.APPLICATION_JSON)
                    .content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                    .andExpect(jsonPath("$.amount").value(25.50))
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            ArgumentCaptor<WithdrawCommand> captor = ArgumentCaptor.forClass(WithdrawCommand.class);
            verify(commands).withdraw(any(Caller.class), captor.capture());
            assertThat(captor.getValue().accountId()).isEqualTo(ID);
            assertThat(captor.getValue().amount()).isEqualByComparingTo("25.50");
            assertThat(captor.getValue().currency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Una retirada rechazada no es un error: responde 201 con el movimiento REJECTED")
        void aRejectedWithdrawalResponds201WithTheRejectedTransaction() throws Exception {
            given(commands.withdraw(any(Caller.class), any())).willReturn(withdrawal("REJECTED"));

            mvc.perform(post("/api/accounts/{id}/withdrawals", ID).contentType(MediaType.APPLICATION_JSON)
                    .content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("REJECTED"));
        }

        @ParameterizedTest(name = "importe {0}")
        @DisplayName("Un importe que redondea a cero o supera el máximo responde 400 con el error del campo")
        @ValueSource(strings = {"0.004", "1000000000.00", "1.7976931348623155E+308"})
        void anAmountOutOfBoundsResponds400WithTheFieldError(String amount) throws Exception {
            expectAmountRejected("/api/accounts/{id}/withdrawals", amount);
        }

        @ParameterizedTest(name = "importe {0}")
        @DisplayName("Un importe cero o negativo responde 400 con el error del campo")
        @ValueSource(strings = {"0", "-5"})
        void aZeroOrNegativeAmountResponds400WithTheFieldError(String amount) throws Exception {
            expectAmountRejected("/api/accounts/{id}/withdrawals", amount);
        }

        @Test
        @DisplayName("Una cuenta desconocida responde 404 Recurso inexistente")
        void anUnknownAccountResponds404() throws Exception {
            given(commands.withdraw(any(Caller.class), any())).willThrow(new AccountNotFoundException(ID));

            mvc.perform(post("/api/accounts/{id}/withdrawals", ID).contentType(MediaType.APPLICATION_JSON)
                    .content(BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Recurso inexistente"));
        }

        @Test
        @DisplayName("Una divisa distinta a la de la cuenta responde 422 Regla de negocio incumplida")
        void aCurrencyDifferentFromTheAccountsResponds422() throws Exception {
            given(commands.withdraw(any(Caller.class), any())).willThrow(
                    new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD")));

            mvc.perform(post("/api/accounts/{id}/withdrawals", ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 10, \"currency\": \"USD\"}"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"));
        }
        @Test
        @DisplayName("Con la divisa mal formada responde 400 con el error del campo")
        void aMalformedCurrencyResponds400WithTheFieldError() throws Exception {
            mvc.perform(post("/api/accounts/{id}/withdrawals", ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 10, \"currency\": \"eur\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.currency").exists());

            verifyNoInteractions(commands, queries);
        }

        @Test
        @DisplayName("Una divisa bien formada pero desconocida responde 400")
        void aWellFormedButUnknownCurrencyResponds400() throws Exception {
            mvc.perform(post("/api/accounts/{id}/withdrawals", ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 10, \"currency\": \"XYZ\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Petición no válida"));
        }
    }

    @Nested
    @DisplayName("POST /api/accounts/{id}/account-number")
    class RegenerateAccountNumber {

        private static final String NEW_IBAN = "ES6699990001740000000001";
        private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();

        @Test
        @DisplayName("Un administrador recibe 200 con el resumen y el IBAN nuevo")
        void anAdministratorGets200WithTheSummaryAndTheNewIban() throws Exception {
            given(commands.regenerateAccountNumber(ADMIN, ID)).willReturn(
                    AccountSummary.builder().id(ID).accountNumber(NEW_IBAN).holderName("Ada Lovelace")
                            .segment("RETAIL").balance(new BigDecimal("100.00")).currency("EUR").build());

            mvc.perform(post("/api/accounts/{id}/account-number", ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ID.toString()))
                    .andExpect(jsonPath("$.accountNumber").value(NEW_IBAN));
        }

        @Test
        @DisplayName("Un cliente recibe 403 Acceso denegado y no se llama al puerto")
        @WithMockUser(username = "ada", roles = "USER")
        void aCustomerGets403() throws Exception {
            mvc.perform(post("/api/accounts/{id}/account-number", ID))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Acceso denegado"))
                    .andExpect(jsonPath("$.detail").value("Solo un administrador puede generar un nuevo número de cuenta"));

            verifyNoInteractions(commands);
        }

        @Test
        @DisplayName("Sin credenciales responde 401 No autenticado")
        @WithAnonymousUser
        void withoutCredentialsResponds401() throws Exception {
            mvc.perform(post("/api/accounts/{id}/account-number", ID))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("No autenticado"));

            verifyNoInteractions(commands);
        }

        @Test
        @DisplayName("Si el puerto lanza ForbiddenOperationException la respuesta es 403")
        void whenThePortThrowsForbiddenOperationExceptionTheResponseIs403() throws Exception {
            given(commands.regenerateAccountNumber(any(Caller.class), any()))
                    .willThrow(new ForbiddenOperationException("generar un nuevo número de cuenta"));

            mvc.perform(post("/api/accounts/{id}/account-number", ID))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Acceso denegado"));
        }

        @Test
        @DisplayName("Una cuenta desconocida responde 404 Recurso inexistente")
        void anUnknownAccountResponds404() throws Exception {
            given(commands.regenerateAccountNumber(any(Caller.class), any())).willThrow(new AccountNotFoundException(ID));

            mvc.perform(post("/api/accounts/{id}/account-number", ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Recurso inexistente"));
        }
    }
}
