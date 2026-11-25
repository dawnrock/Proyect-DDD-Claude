package com.dawnrock.ddd.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.security.test.context.support.WithMockUser;

import com.dawnrock.ddd.application.AccountNotFoundException;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.WithdrawCommand;
import com.dawnrock.ddd.domain.model.CurrencyMismatchException;

/** Retiradas desde el detalle de la cuenta (#177), con y sin htmx. */
@WebMvcTest({WithdrawalWebController.class, TransferWebController.class, AccountDetailController.class, AccountWebController.class})
@WithMockUser(username = "admin", roles = "ADMIN")
class WithdrawalWebControllerTests extends AccountWebTestSupport {

    @Nested
    @DisplayName("Retiradas")
    class Retiradas {

        private MockHttpServletRequestBuilder retiradaDe(String amount, String currency) {
            return post("/accounts/" + ACCOUNT_ID + "/withdrawals")
                    .param("amount", amount)
                    .param("currency", currency);
        }

        @Test
        @DisplayName("El detalle ofrece el formulario de retirada")
        void theDetailsOfferTheWithdrawalForm() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(content().string(containsString("/accounts/" + ACCOUNT_ID + "/withdrawals")));
        }

        @Test
        @DisplayName("Una retirada aprobada redirige al detalle con un mensaje de éxito")
        void anApprovedWithdrawalRedirectsToTheDetailsWithASuccessMessage() throws Exception {
            when(movements.withdraw(any(Caller.class), any())).thenReturn(transaction("WITHDRAWAL", "30.00", "APPROVED"));

            mvc.perform(retiradaDe("30.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                    .andExpect(flash().attribute("message", "Retirada de 30,00 € aprobada"))
                    .andExpect(flash().attributeCount(1));

            verify(movements).withdraw(ADMIN, WithdrawCommand.builder()
                    .accountId(ACCOUNT_ID)
                    .amount(new BigDecimal("30.00"))
                    .currency("EUR")
                    .build());
        }

        @Test
        @DisplayName("Una retirada rechazada redirige al detalle con un aviso de rechazo distinto del de éxito")
        void aRejectedWithdrawalRedirectsWithARejectionNoticeDifferentFromSuccess()
                throws Exception {
            when(movements.withdraw(any(Caller.class), any())).thenReturn(transaction("WITHDRAWAL", "500.00", "REJECTED"));

            mvc.perform(retiradaDe("500.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                    .andExpect(flash().attribute("rejection",
                            "Retirada de 500,00 € rechazada: el saldo resultante no está permitido para esta cuenta"))
                    .andExpect(flash().attributeCount(1));
        }

        @Test
        @DisplayName("El aviso de rechazo se pinta con el estilo de advertencia y no con el de éxito")
        void theRejectionNoticeUsesTheWarningStyleNotTheSuccessOne() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID).flashAttr("rejection", "Retirada de 500.00 EUR rechazada"))
                    .andExpect(content().string(containsString("alert alert-warning")))
                    .andExpect(content().string(containsString("role=\"alert\"")))
                    .andExpect(content().string(containsString("Retirada de 500.00 EUR rechazada")));
        }

        @Test
        @DisplayName("Un importe no positivo vuelve al detalle con el error del campo y sin llamar al servicio")
        void aNonPositiveAmountReturnsToTheDetailsWithTheFieldErrorWithoutCallingTheService()
                throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(retiradaDe("-1", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasFieldErrors("withdrawalForm", "amount"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(content().string(containsString("El importe debe ser positivo")));

            verify(movements, never()).withdraw(any(Caller.class), any());
        }

        @Test
        @DisplayName("Otra divisa vuelve al detalle con el mensaje del dominio y conserva el importe")
        void anotherCurrencyReturnsToTheDetailsWithTheDomainMessageAndKeepsTheAmount() throws Exception {
            var error = new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("GBP"));
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
            when(movements.withdraw(any(Caller.class), any())).thenThrow(error);

            mvc.perform(retiradaDe("20.25", "GBP"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("withdrawalForm"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(content().string(containsString(error.getMessage())))
                    .andExpect(content().string(containsString("20.25")));
        }

        @ParameterizedTest
        @CsvSource({"0.001, EUR, El importe debe ser positivo", "10.00, XYZ, XYZ no es un c"})
        @DisplayName("Un importe que redondea a cero o una divisa inexistente vuelven al detalle con el error del command")
        void anAmountRoundingToZeroOrAMissingCurrencyReturnsToTheDetailsWithTheCommandError(
                String amount, String currency, String message) throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(retiradaDe(amount, currency))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("withdrawalForm"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(content().string(containsString(message)))
                    .andExpect(content().string(containsString(amount)));

            verify(movements, never()).withdraw(any(Caller.class), any());
        }

        @Test
        @DisplayName("Una retirada en una cuenta desconocida devuelve la página 404")
        void aWithdrawalFromAnUnknownAccountReturnsThe404Page() throws Exception {
            when(movements.withdraw(any(Caller.class), any())).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

            mvc.perform(retiradaDe("10.00", "EUR"))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"));
        }
    }

    @Nested
    @DisplayName("Retiradas con htmx (#125)")
    class RetiradasConHtmx {

        @BeforeEach
        void cuenta() {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
        }

        private MockHttpServletRequestBuilder retirada(String amount) {
            return htmx(post("/accounts/" + ACCOUNT_ID + "/withdrawals").param("amount", amount).param("currency", "EUR"));
        }

        @Test
        @DisplayName("Una retirada aprobada responde con el panel y el mensaje de éxito")
        void anApprovedWithdrawalRespondsWithThePanelAndTheMessage() throws Exception {
            when(movements.withdraw(any(Caller.class), any())).thenReturn(transaction("WITHDRAWAL", "30.00", "APPROVED"));

            mvc.perform(retirada("30.00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail :: panel"))
                    .andExpect(model().attribute("message", "Retirada de 30,00 € aprobada"))
                    .andExpect(flash().attributeCount(0));
        }

        @Test
        @DisplayName("Una retirada rechazada responde con el panel y el aviso de advertencia")
        void aRejectedWithdrawalRespondsWithThePanelAndTheNotice() throws Exception {
            when(movements.withdraw(any(Caller.class), any())).thenReturn(transaction("WITHDRAWAL", "500.00", "REJECTED"));

            mvc.perform(retirada("500.00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail :: panel"))
                    .andExpect(model().attributeExists("rejection"))
                    .andExpect(content().string(containsString("alert alert-warning")))
                    .andExpect(content().string(containsString("Retirada de 500,00 € rechazada")));
        }

        @Test
        @DisplayName("Un importe vacío responde con el panel y el formulario de retirada abierto")
        void anEmptyAmountRespondsWithThePanelAndTheFormOpen() throws Exception {
            mvc.perform(retirada(""))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail :: panel"))
                    .andExpect(content().string(containsString("<details id=\"withdrawal-action\" open=\"open\">")));

            verifyNoInteractions(commands, movements);
        }
    }
}
