package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.security.test.context.support.WithMockUser;

import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.OpenAccountCommand;
import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.DuplicateAccountNumberException;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.domain.model.MonetaryAmount;

/** Alta de cuenta por el administrador (#177): formulario, errores y usuario del titular en minúsculas. */
@WebMvcTest(OpenAccountWebController.class)
@WithMockUser(username = "admin", roles = "ADMIN")
class OpenAccountWebControllerTests extends AccountWebTestSupport {

    @Nested
    @DisplayName("Alta de cuenta")
    class Alta {

        private AccountSummary abierta(String segment) {
            return new AccountSummary(UUID.randomUUID(), "ES1234567890123456789012", "Ana Pérez", segment,
                    new BigDecimal("100.00"), "EUR");
        }

        private MockHttpServletRequestBuilder altaValida() {
            return post("/accounts/new")
                    .param("holderName", "Ana Pérez")
                    .param("holderTaxId", "12345678z")
                    .param("holderEmail", "ana@example.com")
                    .param("ownerUsername", "ana")
                    .param("initialDeposit", "100.00")
                    .param("currency", "EUR");
        }

        @Test
        @DisplayName("El formulario muestra el NIF, la nota del IBAN generado y el euro por defecto, sin IBAN ni segmento")
        void theFormShowsTheTaxIdAndDoesNotAskForIbanOrSegment() throws Exception {
            mvc.perform(get("/accounts/new"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"holderName\"")))
                    .andExpect(content().string(containsString("name=\"holderTaxId\"")))
                    .andExpect(content().string(containsString("name=\"holderEmail\"")))
                    .andExpect(content().string(containsString("deja en blanco nombre, correo y usuario")))
                    .andExpect(content().string(containsString("name=\"initialDeposit\"")))
                    .andExpect(content().string(containsString("se genera automáticamente")))
                    .andExpect(content().string(containsString("ingresos verificados")))
                    .andExpect(content().string(not(containsString("name=\"accountNumber\""))))
                    .andExpect(content().string(not(containsString("name=\"segment\""))))
                    .andExpect(model().attributeDoesNotExist("segments"))
                    .andExpect(content().string(matchesPattern("(?s).*<option value=\"EUR\" selected=\"selected\".*")));
        }

        @Test
        @DisplayName("Un alta válida envía el NIF sin IBAN ni segmento y redirige al listado")
        void aValidOpeningSendsTheTaxIdWithoutIbanOrSegmentAndRedirectsToTheList() throws Exception {
            when(commands.openAccount(any(Caller.class), any())).thenReturn(abierta("RETAIL"));

            mvc.perform(altaValida())
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"))
                    .andExpect(flash().attributeExists("message"));

            verify(commands).openAccount(ADMIN, new OpenAccountCommand("Ana Pérez", "12345678z", "ana@example.com",
                    new BigDecimal("100.00"), "ana", "EUR"));
        }

        @Test
        @DisplayName("El mensaje muestra el IBAN generado completo y el segmento Minorista")
        void theMessageShowsTheGeneratedIbanAndTheRetailSegment() throws Exception {
            when(commands.openAccount(any(Caller.class), any())).thenReturn(abierta("RETAIL"));

            mvc.perform(altaValida())
                    .andExpect(flash().attribute("message",
                            "Cuenta ES1234567890123456789012 abierta correctamente como Minorista"));
        }

        @Test
        @DisplayName("El mensaje muestra el segmento Alto patrimonio cuando la fábrica lo calcula")
        void theMessageShowsTheHighNetWorthSegment() throws Exception {
            when(commands.openAccount(any(Caller.class), any())).thenReturn(abierta("HIGH_NET_WORTH"));

            mvc.perform(altaValida())
                    .andExpect(flash().attribute("message",
                            "Cuenta ES1234567890123456789012 abierta correctamente como Alto patrimonio"));
        }

        @Test
        @DisplayName("Un segmento desconocido se muestra tal cual")
        void anUnknownSegmentIsShownAsIs() throws Exception {
            when(commands.openAccount(any(Caller.class), any())).thenReturn(abierta("PRIVATE"));

            mvc.perform(altaValida())
                    .andExpect(flash().attribute("message",
                            "Cuenta ES1234567890123456789012 abierta correctamente como PRIVATE"));
        }

        @Test
        @DisplayName("El formulario pide el usuario del titular")
        void theFormAsksForTheHolderUsername() throws Exception {
            mvc.perform(get("/accounts/new"))
                    .andExpect(content().string(containsString("Usuario del titular")))
                    .andExpect(content().string(containsString("name=\"ownerUsername\"")));
        }
    }

    @Nested
    @DisplayName("Errores del alta de cuenta")
    class ErroresDelAlta {

        private MockHttpServletRequestBuilder altaCon(String taxId, String holderName, String deposit) {
            return altaDe(taxId, holderName, deposit, "ana").param("holderEmail", "ana@example.com");
        }

        @Test
        @DisplayName("Sin nombre, correo ni usuario el formulario llega al servicio: es él quien sabe si el cliente existe")
        void withoutCustomerDataTheFormReachesTheService() throws Exception {
            when(commands.openAccount(any(Caller.class), any())).thenThrow(new IllegalArgumentException(
                    "El usuario del titular es obligatorio para un cliente nuevo"));

            mvc.perform(altaDe("12345678Z", "", "100.00", ""))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/new"))
                    .andExpect(model().attributeHasErrors("form"))
                    .andExpect(content().string(containsString("obligatorio para un cliente nuevo")));

            verify(commands).openAccount(ADMIN, new OpenAccountCommand(null, "12345678Z", null,
                    new BigDecimal("100.00"), null, "EUR"));
        }

        @Test
        @DisplayName("Un correo mal formado vuelve al formulario con el error del campo y sin llamar al servicio")
        void aMalformedEmailReturnsToTheForm() throws Exception {
            mvc.perform(altaDe("12345678Z", "Ana Lopez", "100.00", "ana").param("holderEmail", "ana@"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("form", "holderEmail"))
                    .andExpect(content().string(containsString("El correo electrónico no es válido")));

            verifyNoInteractions(commands, movements, queries);
        }

        @Test
        @DisplayName("El campo con error se marca como inválido de Bootstrap y su mensaje va debajo (#124)")
        void theFieldWithAnErrorIsMarkedAsBootstrapInvalid() throws Exception {
            mvc.perform(altaDe("12345678Z", "Ana Lopez", "100.00", "ana").param("holderEmail", "ana@"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(matchesPattern(
                            "(?s).*<input class=\"form-control is-invalid\" type=\"email\" id=\"holderEmail\".*")))
                    .andExpect(content().string(containsString(
                            "<p class=\"invalid-feedback\" id=\"holderEmail-error\">El correo electrónico no es válido")))
                    .andExpect(content().string(matchesPattern(
                            "(?s).*<input class=\"form-control\" type=\"text\" id=\"holderName\".*")));
        }

        @Test
        @DisplayName("Un usuario del titular con formato inválido vuelve al formulario y conserva lo escrito")
        void aHolderUsernameWithAnInvalidFormatReturnsToTheForm() throws Exception {
            mvc.perform(altaDe("12345678Z", "Ana Lopez", "100.00", "Ana Lopez"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("form", "ownerUsername"))
                    .andExpect(content().string(containsString("minúsculas sin tildes ni ñ, dígitos, puntos, guiones bajos o guiones")))
                    .andExpect(content().string(containsString("Ana Lopez")));

            verifyNoInteractions(commands, movements, queries);
        }

        @Test
        @DisplayName("Unos datos que contradicen al cliente existente vuelven al formulario con el mensaje del servicio")
        void dataContradictingTheExistingCustomerReturnsToTheForm() throws Exception {
            when(commands.openAccount(any(Caller.class), any())).thenThrow(new IllegalArgumentException(
                    "El documento 12345678Z ya es de un cliente: deja en blanco nombre, correo y usuario, o indica los suyos"));

            mvc.perform(altaCon("12345678Z", "Ana Lopez", "100.00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/new"))
                    .andExpect(model().attributeHasErrors("form"))
                    .andExpect(content().string(containsString("ya es de un cliente")));
        }

        private MockHttpServletRequestBuilder altaDe(String taxId, String holderName, String deposit,
                                                     String owner) {
            return post("/accounts/new")
                    .param("holderTaxId", taxId)
                    .param("ownerUsername", owner)
                    .param("holderName", holderName)
                    .param("initialDeposit", deposit)
                    .param("currency", "EUR");
        }

        @Test
        @DisplayName("Un documento mal formado vuelve al formulario con el error del campo y conserva los datos")
        void aMalformedDocumentReturnsToTheFormWithTheFieldErrorAndKeepsTheData()
                throws Exception {
            mvc.perform(altaCon("1234-Z", "Ana Lopez", "100.00"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("form", "holderTaxId"))
                    .andExpect(content().string(containsString("El documento debe ser un DNI o un NIE")))
                    .andExpect(content().string(containsString("1234-Z")))
                    .andExpect(content().string(containsString("Ana Lopez")));

            verifyNoInteractions(commands, movements, queries);
        }

        @Test
        @DisplayName("Un depósito insuficiente vuelve al formulario con el mensaje del dominio y conserva los datos")
        void anInsufficientDepositReturnsToTheFormWithTheDomainMessageAndKeepsTheData()
                throws Exception {
            var error = new InsufficientInitialDepositException(
                    MonetaryAmount.euros("10.00"), MonetaryAmount.euros("50.00"));
            when(commands.openAccount(any(Caller.class), any())).thenThrow(error);

            mvc.perform(altaCon("12345678Z", "Ana Lopez", "10.00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/new"))
                    .andExpect(model().attributeHasErrors("form"))
                    .andExpect(content().string(containsString(
                            "no alcanza el mínimo exigido")))
                    .andExpect(content().string(containsString("12345678Z")))
                    .andExpect(content().string(containsString("Ana Lopez")));
        }

        @Test
        @DisplayName("Un número de cuenta generado y duplicado vuelve al formulario con el mensaje del dominio")
        void aDuplicateAccountNumberReturnsToTheFormWithTheDomainMessage() throws Exception {
            var error = new DuplicateAccountNumberException(new AccountNumber("ES1234567890123456789012"));
            when(commands.openAccount(any(Caller.class), any())).thenThrow(error);

            mvc.perform(altaCon("12345678Z", "Ana Lopez", "100.00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/new"))
                    .andExpect(content().string(containsString(error.getMessage())));
        }

        @Test
        @DisplayName("Una divisa bien formada pero inexistente vuelve al formulario con el error y sin llamar al servicio")
        void aWellFormedButMissingCurrencyReturnsToTheFormWithoutCallingTheService()
                throws Exception {
            mvc.perform(post("/accounts/new")
                            .param("holderTaxId", "12345678Z")
                            .param("ownerUsername", "ana")
                            .param("holderName", "Ana Lopez")
                            .param("initialDeposit", "100.00")
                            .param("currency", "XYZ"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/new"))
                    .andExpect(model().attributeHasErrors("form"))
                    .andExpect(content().string(containsString("XYZ no es un c")))
                    .andExpect(content().string(containsString("Ana Lopez")));

            verifyNoInteractions(commands, movements, queries);
        }
    }

    @Nested
    @DisplayName("El usuario del titular se guarda en minúsculas (#152)")
    class UsuarioDelTitularEnMinusculas {

        private AccountSummary cuentaAbierta() {
            return new AccountSummary(UUID.randomUUID(), "ES1234567890123456789012", "Ana Pérez", "RETAIL",
                    new BigDecimal("100.00"), "EUR");
        }

        @Test
        @DisplayName("Un usuario del titular con mayúsculas y espacios alrededor se envía en minúsculas")
        void aHolderUsernameWithCapitalsIsSentInLowerCase() throws Exception {
            when(commands.openAccount(any(Caller.class), any())).thenReturn(cuentaAbierta());

            mvc.perform(post("/accounts/new").param("holderName", "Ana Pérez").param("holderTaxId", "12345678Z")
                            .param("holderEmail", "ana@example.com").param("ownerUsername", " Ana.Perez ")
                            .param("initialDeposit", "100.00").param("currency", "EUR"))
                    .andExpect(status().is3xxRedirection());

            verify(commands).openAccount(ADMIN, new OpenAccountCommand("Ana Pérez", "12345678Z", "ana@example.com",
                    new BigDecimal("100.00"), "ana.perez", "EUR"));
        }

        @Test
        @DisplayName("Sin usuario del titular, la apertura para un cliente existente sigue sin pedirlo")
        void withoutHolderUsernameItIsStillOptional() throws Exception {
            when(commands.openAccount(any(Caller.class), any())).thenReturn(cuentaAbierta());

            mvc.perform(post("/accounts/new").param("holderTaxId", "12345678Z").param("ownerUsername", "")
                            .param("initialDeposit", "100.00").param("currency", "EUR"))
                    .andExpect(status().is3xxRedirection());

            verify(commands).openAccount(ADMIN, new OpenAccountCommand(null, "12345678Z", null,
                    new BigDecimal("100.00"), null, "EUR"));
        }

        @Test
        @DisplayName("Abrir cuenta explica el formato del usuario del titular bajo el campo y lo liga al campo")
        void openAccountExplainsTheHolderUsernameFormat() throws Exception {
            String html = mvc.perform(get("/accounts/new")).andReturn().getResponse().getContentAsString();

            assertThat(html).containsPattern("<p class=\"form-text[^\"]*\" id=\"ownerUsername-help\">[^<]*guiones bajos o guiones[^<]*minúsculas");
            assertThat(FieldTags.tag(html, "ownerUsername"))
                    .contains("aria-describedby=\"ownerUsername-help\"")
                    .doesNotContain("maxlength=");
        }
    }
}
