package com.dawnrock.ddd.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import java.util.Currency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.security.test.context.support.WithMockUser;

import com.dawnrock.ddd.application.AccountNotFoundException;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.DepositCommand;
import com.dawnrock.ddd.domain.model.CurrencyMismatchException;

/** Ingresos desde el detalle de la cuenta (#177), con y sin htmx. */
@WebMvcTest({DepositWebController.class, WithdrawalWebController.class, AccountDetailController.class, AccountWebController.class})
@WithMockUser(username = "admin", roles = "ADMIN")
class DepositWebControllerTests extends AccountWebTestSupport {

    @Nested
    @DisplayName("Ingresos")
    class Ingresos {

        private MockHttpServletRequestBuilder ingresoDe(String amount, String currency) {
            return post("/accounts/" + ACCOUNT_ID + "/deposits")
                    .param("amount", amount)
                    .param("currency", currency);
        }

        @Test
        @DisplayName("El detalle ofrece el formulario de ingreso con euro, dólar y libra, y el euro por defecto")
        void theDetailsOfferTheDepositFormWithEuroDollarAndPoundAndEuroByDefault()
                throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(content().string(containsString("/accounts/" + ACCOUNT_ID + "/deposits")))
                    .andExpect(content().string(containsString("name=\"amount\"")))
                    .andExpect(content().string(containsString("value=\"USD\"")))
                    .andExpect(content().string(containsString("value=\"GBP\"")))
                    .andExpect(content().string(matchesPattern("(?s).*<option value=\"EUR\" selected=\"selected\".*")));
        }

        @Test
        @DisplayName("Un ingreso válido llama al servicio con el comando esperado y redirige al detalle con un mensaje")
        void aValidDepositCallsTheServiceWithTheExpectedCommandAndRedirectsWithAMessage()
                throws Exception {
            when(movements.deposit(any(Caller.class), any())).thenReturn(transaction("DEPOSIT", "50.00", "APPROVED"));

            mvc.perform(ingresoDe("50.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                    .andExpect(flash().attribute("message", "Ingreso de 50,00 € aprobado"));

            verify(movements).deposit(ADMIN, DepositCommand.builder()
                    .accountId(ACCOUNT_ID)
                    .amount(new BigDecimal("50.00"))
                    .currency("EUR")
                    .build());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-5.00"})
        @DisplayName("Un importe no positivo vuelve al detalle con el error del campo y sin llamar al servicio")
        void aNonPositiveAmountReturnsToTheDetailsWithTheFieldErrorWithoutCallingTheService(String amount)
                throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(ingresoDe(amount, "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasFieldErrors("depositForm", "amount"))
                    .andExpect(model().attributeHasNoErrors("withdrawalForm"))
                    .andExpect(content().string(containsString("El importe debe ser positivo")))
                    .andExpect(content().string(containsString("ES1234567890123456789012")));

            verify(movements, never()).deposit(any(Caller.class), any());
        }

        @Test
        @DisplayName("Un importe ausente vuelve al detalle con el error del campo")
        void aMissingAmountReturnsToTheDetailsWithTheFieldError() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(post("/accounts/" + ACCOUNT_ID + "/deposits").param("currency", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("El importe es obligatorio")));

            verify(movements, never()).deposit(any(Caller.class), any());
        }

        @Test
        @DisplayName("Otra divisa vuelve al detalle con el mensaje del dominio y conserva el importe")
        void anotherCurrencyReturnsToTheDetailsWithTheDomainMessageAndKeepsTheAmount() throws Exception {
            var error = new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD"));
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
            when(movements.deposit(any(Caller.class), any())).thenThrow(error);

            mvc.perform(ingresoDe("75.50", "USD"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("depositForm"))
                    .andExpect(model().attributeHasNoErrors("withdrawalForm"))
                    .andExpect(content().string(containsString(error.getMessage())))
                    .andExpect(content().string(containsString("75.50")));
        }

        @ParameterizedTest
        @CsvSource({"0.001, EUR, El importe debe ser positivo", "10.00, XYZ, XYZ no es un c"})
        @DisplayName("Un importe que redondea a cero o una divisa inexistente vuelven al detalle con el error del command")
        void anAmountRoundingToZeroOrAMissingCurrencyReturnsToTheDetailsWithTheCommandError(
                String amount, String currency, String message) throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(ingresoDe(amount, currency))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("depositForm"))
                    .andExpect(model().attributeHasNoErrors("withdrawalForm"))
                    .andExpect(content().string(containsString(message)))
                    .andExpect(content().string(containsString(amount)));

            verify(movements, never()).deposit(any(Caller.class), any());
        }

        @Test
        @DisplayName("Un ingreso en una cuenta desconocida devuelve la página 404")
        void aDepositIntoAnUnknownAccountReturnsThe404Page() throws Exception {
            when(movements.deposit(any(Caller.class), any())).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

            mvc.perform(ingresoDe("10.00", "EUR"))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"));
        }
    }

    @Nested
    @DisplayName("Ingresos con htmx (#125)")
    class IngresosConHtmx {

        @BeforeEach
        void cuenta() {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
        }

        @Test
        @DisplayName("Un ingreso aprobado responde con el panel y el mensaje en el modelo, sin redirigir ni usar flash")
        void anApprovedDepositRespondsWithThePanelAndTheMessage() throws Exception {
            when(movements.deposit(any(Caller.class), any())).thenReturn(transaction("DEPOSIT", "50.00", "APPROVED"));

            mvc.perform(htmx(post("/accounts/" + ACCOUNT_ID + "/deposits").param("amount", "50.00").param("currency", "EUR")))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail :: panel"))
                    .andExpect(model().attribute("message", "Ingreso de 50,00 € aprobado"))
                    .andExpect(flash().attributeCount(0))
                    .andExpect(content().string(containsString("<div id=\"account-panel\"")))
                    .andExpect(content().string(containsString("Ingreso de 50,00 € aprobado")))
                    .andExpect(content().string(not(containsString("value=\"50.00\""))))
                    .andExpect(content().string(not(containsString("<html"))));
        }

        @Test
        @DisplayName("Un importe de cero responde con el panel, el formulario de ingreso abierto y su error")
        void aZeroAmountRespondsWithThePanelAndTheFormOpen() throws Exception {
            mvc.perform(htmx(post("/accounts/" + ACCOUNT_ID + "/deposits").param("amount", "0").param("currency", "EUR")))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail :: panel"))
                    .andExpect(content().string(containsString("<details id=\"deposit-action\" open=\"open\">")))
                    .andExpect(content().string(containsString("class=\"invalid-feedback\"")));

            verifyNoInteractions(commands, movements);
        }

        @Test
        @DisplayName("Un error del dominio responde con el panel y el mensaje del dominio en el formulario")
        void aDomainErrorRespondsWithThePanelAndItsMessage() throws Exception {
            var error = new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD"));
            when(movements.deposit(any(Caller.class), any())).thenThrow(error);

            mvc.perform(htmx(post("/accounts/" + ACCOUNT_ID + "/deposits").param("amount", "10.00").param("currency", "USD")))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail :: panel"))
                    .andExpect(content().string(containsString(error.getMessage())));
        }
    }
}
