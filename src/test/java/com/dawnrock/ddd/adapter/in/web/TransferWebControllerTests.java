package com.dawnrock.ddd.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.mockito.Mockito.when;
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
import com.dawnrock.ddd.application.TargetAccountNotFoundException;
import com.dawnrock.ddd.application.TransferCommand;
import com.dawnrock.ddd.domain.model.CurrencyMismatchException;
import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.SameAccountTransferException;

/** Transferencias desde el detalle de la cuenta (#177): envío, errores y respuestas a htmx. */
@WebMvcTest({TransferWebController.class, AccountNumberWebController.class, AccountDetailController.class, AccountWebController.class})
@WithMockUser(username = "admin", roles = "ADMIN")
class TransferWebControllerTests extends AccountWebTestSupport {

    @Nested
    @DisplayName("Envío de transferencias")
    class EnvioDeTransferencias {

        private MockHttpServletRequestBuilder transferenciaDe(String target, String amount, String currency) {
            return post("/accounts/" + ACCOUNT_ID + "/transfers")
                    .param("targetAccountNumber", target)
                    .param("amount", amount)
                    .param("currency", currency);
        }

        @Test
        @DisplayName("Una transferencia a un IBAN escrito llama al servicio con el comando esperado y redirige con un mensaje")
        void aTransferToATypedIbanCallsTheServiceWithTheExpectedCommand()
                throws Exception {
            when(movements.transfer(any(Caller.class), any())).thenReturn(transfer("25.00", "APPROVED"));

            mvc.perform(transferenciaDe("es98 7654 3210 9876 5432 1098", "25.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                    .andExpect(flash().attribute("message",
                            "Transferencia de 25,00 € a ES98\u00A0••••\u00A01098 aprobada"))
                    .andExpect(flash().attributeCount(1));

            verify(movements).transfer(ADMIN, TransferCommand.builder()
                    .sourceAccountId(ACCOUNT_ID)
                    .targetAccountNumber("es98 7654 3210 9876 5432 1098")
                    .amount(new BigDecimal("25.00"))
                    .currency("EUR")
                    .build());
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario transfiere desde su cuenta: el servicio recibe a ada como usuario")
        void aUserTransfersFromTheirAccount() throws Exception {
            when(movements.transfer(eq(ADA), any())).thenReturn(transfer("25.00", "APPROVED"));

            mvc.perform(transferenciaDe(TARGET_IBAN, "25.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID));

            verify(movements).transfer(eq(ADA), any());
        }

        @Test
        @DisplayName("Una transferencia rechazada redirige con un aviso de rechazo distinto del de éxito")
        void aRejectedTransferRedirectsWithARejectionNoticeDifferentFromSuccess() throws Exception {
            when(movements.transfer(any(Caller.class), any())).thenReturn(transfer("900.00", "REJECTED"));

            mvc.perform(transferenciaDe(TARGET_IBAN, "900.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                    .andExpect(flash().attribute("rejection",
                            "Transferencia de 900,00 € a ES98\u00A0••••\u00A01098 rechazada: "
                                    + "el saldo resultante no está permitido para la cuenta de origen"))
                    .andExpect(flash().attributeCount(1));
        }

        @Test
        @DisplayName("Una divisa vacía se trata como el euro por defecto")
        void anEmptyCurrencyIsTreatedAsTheDefaultEuro() throws Exception {
            when(movements.transfer(any(Caller.class), any())).thenReturn(transfer("25.00", "APPROVED"));

            mvc.perform(post("/accounts/" + ACCOUNT_ID + "/transfers")
                            .param("targetAccountNumber", TARGET_IBAN)
                            .param("amount", "25.00")
                            .param("currency", ""))
                    .andExpect(status().is3xxRedirection());

            verify(movements).transfer(ADMIN, TransferCommand.builder()
                    .sourceAccountId(ACCOUNT_ID)
                    .targetAccountNumber(TARGET_IBAN)
                    .amount(new BigDecimal("25.00"))
                    .build());
        }
    }

    @Nested
    @DisplayName("Errores de transferencia")
    class ErroresDeTransferencia {

        private MockHttpServletRequestBuilder transferenciaDe(String target, String amount, String currency) {
            return post("/accounts/" + ACCOUNT_ID + "/transfers")
                    .param("targetAccountNumber", target)
                    .param("amount", amount)
                    .param("currency", currency);
        }

        @BeforeEach
        void cuenta() {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
        }

        @Test
        @DisplayName("Sin IBAN de destino vuelve al detalle con el error del campo y sin llamar al servicio")
        void withoutTargetIbanReturnsToTheDetailsWithTheFieldErrorWithoutCallingTheService()
                throws Exception {
            mvc.perform(transferenciaDe("", "25.00", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasFieldErrors("transferForm", "targetAccountNumber"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(model().attributeHasNoErrors("withdrawalForm"))
                    .andExpect(content().string(containsString("Escribe el número de cuenta de destino")));

            verify(movements, never()).transfer(any(Caller.class), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"ES12-345", "ES12 3456", "FR7630006000011234567890189", "ES98765432109876543210981"})
        @DisplayName("Un IBAN mal formado vuelve al detalle con el error del campo, conserva lo escrito y no llama al servicio")
        void aMalformedIbanReturnsToTheDetailsWithTheFieldError(String iban) throws Exception {
            mvc.perform(transferenciaDe(iban, "25.00", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasFieldErrors("transferForm", "targetAccountNumber"))
                    .andExpect(content().string(containsString("ES seguido de 22 dígitos")))
                    .andExpect(content().string(containsString("value=\"" + iban + "\"")))
                    .andExpect(content().string(containsString("<details id=\"transfer-action\" open=\"open\">")));

            verify(movements, never()).transfer(any(Caller.class), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-5.00"})
        @DisplayName("Un importe no positivo vuelve al detalle con el error del campo y sin llamar al servicio")
        void aNonPositiveAmountReturnsToTheDetailsWithTheFieldErrorWithoutCallingTheService(String amount)
                throws Exception {
            mvc.perform(transferenciaDe(TARGET_IBAN, amount, "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasFieldErrors("transferForm", "amount"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(model().attributeHasNoErrors("withdrawalForm"))
                    .andExpect(content().string(containsString("El importe debe ser positivo")))
                    .andExpect(content().string(containsString("value=\"" + TARGET_IBAN + "\"")));

            verify(movements, never()).transfer(any(Caller.class), any());
        }

        @Test
        @DisplayName("Transferir a la misma cuenta vuelve al detalle con el mensaje del dominio")
        void transferringToTheSameAccountReturnsToTheDetailsWithTheDomainMessage() throws Exception {
            var error = new SameAccountTransferException(new AccountNumber("ES1234567890123456789012"));
            when(movements.transfer(any(Caller.class), any())).thenThrow(error);

            mvc.perform(transferenciaDe("ES1234567890123456789012", "25.00", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("transferForm"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(content().string(containsString(error.getMessage())));
        }

        @Test
        @DisplayName("Otra divisa vuelve al detalle con el mensaje del dominio y conserva el importe")
        void anotherCurrencyReturnsToTheDetailsWithTheDomainMessageAndKeepsTheAmount() throws Exception {
            var error = new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD"));
            when(movements.transfer(any(Caller.class), any())).thenThrow(error);

            mvc.perform(transferenciaDe(TARGET_IBAN, "20.25", "USD"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("transferForm"))
                    .andExpect(model().attributeHasNoErrors("withdrawalForm"))
                    .andExpect(content().string(containsString(error.getMessage())))
                    .andExpect(content().string(containsString("20.25")));
        }

        @Test
        @DisplayName("Un IBAN de destino que no existe se muestra en el formulario, no como página 404")
        void aMissingTargetIbanIsShownInTheFormNotAsA404Page() throws Exception {
            var error = new TargetAccountNotFoundException(TARGET_IBAN);
            when(movements.transfer(any(Caller.class), any())).thenThrow(error);

            mvc.perform(transferenciaDe(TARGET_IBAN, "25.00", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasFieldErrors("transferForm", "targetAccountNumber"))
                    .andExpect(content().string(containsString(error.getMessage())))
                    .andExpect(content().string(containsString("value=\"" + TARGET_IBAN + "\"")))
                    .andExpect(content().string(containsString("<details id=\"transfer-action\" open=\"open\">")));
        }

        @Test
        @DisplayName("Una cuenta de origen desconocida o ajena devuelve la página 404")
        void anUnknownOrForeignSourceAccountReturnsThe404Page() throws Exception {
            when(movements.transfer(any(Caller.class), any())).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

            mvc.perform(transferenciaDe(TARGET_IBAN, "25.00", "EUR"))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"));
        }

        @Test
        @DisplayName("Un error de formato que solo detecta el servicio vuelve al formulario con su mensaje")
        void aFormatErrorOnlyTheServiceDetectsReturnsToTheFormWithItsMessage()
                throws Exception {
            when(movements.transfer(any(Caller.class), any())).thenThrow(new IllegalArgumentException("Dato inválido"));

            mvc.perform(transferenciaDe(TARGET_IBAN, "25.00", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("transferForm"))
                    .andExpect(content().string(containsString("Dato inválido")));
        }

        @ParameterizedTest
        @CsvSource({"0.001, EUR, El importe debe ser positivo", "10.00, XYZ, XYZ no es un c"})
        @DisplayName("Un importe que redondea a cero o una divisa inexistente vuelven al detalle con el error del command")
        void anAmountRoundingToZeroOrAMissingCurrencyReturnsToTheDetailsWithTheCommandError(
                String amount, String currency, String message) throws Exception {
            mvc.perform(transferenciaDe(TARGET_IBAN, amount, currency))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("transferForm"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(content().string(containsString(message)))
                    .andExpect(content().string(containsString(amount)));

            verify(movements, never()).transfer(any(Caller.class), any());
        }
    }

    @Nested
    @DisplayName("Transferencias con htmx (#125)")
    class TransferenciasConHtmx {

        @BeforeEach
        void cuenta() {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
        }

        private MockHttpServletRequestBuilder transferencia(String amount) {
            return htmx(post("/accounts/" + ACCOUNT_ID + "/transfers")
                    .param("targetAccountNumber", TARGET_IBAN).param("amount", amount).param("currency", "EUR"));
        }

        @Test
        @DisplayName("Una transferencia aprobada responde con el panel, el mensaje y el formulario vacío")
        void anApprovedTransferRespondsWithThePanelAndTheMessage() throws Exception {
            when(movements.transfer(any(Caller.class), any())).thenReturn(transfer("25.00", "APPROVED"));

            mvc.perform(transferencia("25.00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail :: panel"))
                    .andExpect(model().attribute("message", "Transferencia de 25,00 € a ES98\u00A0••••\u00A01098 aprobada"))
                    .andExpect(content().string(not(containsString("value=\"" + TARGET_IBAN + "\""))));
        }

        @Test
        @DisplayName("Una transferencia rechazada responde con el panel y el aviso de advertencia")
        void aRejectedTransferRespondsWithThePanelAndTheNotice() throws Exception {
            when(movements.transfer(any(Caller.class), any())).thenReturn(transfer("900.00", "REJECTED"));

            mvc.perform(transferencia("900.00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail :: panel"))
                    .andExpect(model().attributeExists("rejection"))
                    .andExpect(content().string(containsString("alert alert-warning")));
        }

        @Test
        @DisplayName("Un IBAN de destino que no existe responde con el panel y el error en su campo")
        void aMissingTargetIbanRespondsWithThePanelAndTheErrorInItsField() throws Exception {
            var error = new TargetAccountNotFoundException(TARGET_IBAN);
            when(movements.transfer(any(Caller.class), any())).thenThrow(error);

            mvc.perform(transferencia("25.00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail :: panel"))
                    .andExpect(model().attributeHasFieldErrors("transferForm", "targetAccountNumber"))
                    .andExpect(content().string(containsString("<details id=\"transfer-action\" open=\"open\">")))
                    .andExpect(content().string(containsString(error.getMessage())));
        }

        @Test
        @DisplayName("Una cuenta de origen ajena sigue respondiendo la página 404, también a htmx")
        void aForeignSourceAccountStillResponds404() throws Exception {
            when(movements.transfer(any(Caller.class), any())).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

            mvc.perform(transferencia("25.00"))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"));
        }
    }
}
