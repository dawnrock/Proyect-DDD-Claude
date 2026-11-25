package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.List;
import java.util.UUID;

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
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.TargetAccountNotFoundException;
import com.dawnrock.ddd.application.TransferCommand;
import com.dawnrock.ddd.application.WithdrawCommand;
import com.dawnrock.ddd.domain.model.CurrencyMismatchException;
import com.dawnrock.ddd.application.OpenAccountCommand;
import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.DuplicateAccountNumberException;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.domain.model.SameAccountTransferException;

@WebMvcTest({AccountWebController.class, AccountDetailController.class, DepositWebController.class, AccountListController.class})
@WithMockUser(username = "admin", roles = "ADMIN")
class AccountWebControllerTests extends AccountWebTestSupport {

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

    @Nested
    @DisplayName("Alta de cuenta")
    class Alta {

        private AccountSummary abierta(String segment) {
            return AccountSummary.builder()
                    .id(UUID.randomUUID())
                    .accountNumber("ES1234567890123456789012")
                    .holderName("Ana Pérez").segment(segment)
                    .balance(new BigDecimal("100.00"))
                    .currency("EUR")
                    .build();
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

            verify(commands).openAccount(ADMIN, OpenAccountCommand.builder()
                    .holderName("Ana Pérez")
                    .holderTaxId("12345678z")
                    .holderEmail("ana@example.com")
                    .initialDeposit(new BigDecimal("100.00"))
                    .currency("EUR")
                    .ownerUsername("ana")
                    .build());
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

            verify(commands).openAccount(ADMIN, OpenAccountCommand.builder()
                    .holderTaxId("12345678Z").initialDeposit(new BigDecimal("100.00")).currency("EUR").build());
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
    @DisplayName("Errores accesibles en los formularios de cuentas (#145)")
    class ErroresAccesibles {

        @BeforeEach
        void cuenta() {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
        }

        @Test
        @DisplayName("Sin errores, ningún campo del detalle lleva aria-invalid")
        void withoutErrorsNoDetailsFieldHasAriaInvalid() throws Exception {
            String html = mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).doesNotContain("aria-invalid");
        }

        @Test
        @DisplayName("Un ingreso de cero marca el importe como erróneo y lo liga a su mensaje")
        void aZeroDepositMarksTheAmountAsInvalidAndLinksItToItsMessage() throws Exception {
            String html = mvc.perform(post("/accounts/" + ACCOUNT_ID + "/deposits").param("amount", "0").param("currency", "EUR"))
                    .andReturn().getResponse().getContentAsString();

            FieldTags.assertInvalid(html, "deposit-amount");
            FieldTags.assertValid(html, "deposit-currency");
            FieldTags.assertValid(html, "withdrawal-amount");
        }

        @Test
        @DisplayName("Una retirada sin importe marca el importe de la retirada como erróneo")
        void aWithdrawalWithoutAmountMarksTheAmountAsInvalid() throws Exception {
            String html = mvc.perform(post("/accounts/" + ACCOUNT_ID + "/withdrawals").param("currency", "EUR"))
                    .andReturn().getResponse().getContentAsString();

            FieldTags.assertInvalid(html, "withdrawal-amount");
        }

        @Test
        @DisplayName("Con htmx, una transferencia sin IBAN responde el panel con el IBAN marcado como erróneo")
        void withHtmxATransferWithoutIbanMarksTheIbanAsInvalid() throws Exception {
            String html = mvc.perform(htmx(post("/accounts/" + ACCOUNT_ID + "/transfers")
                            .param("targetAccountNumber", "").param("amount", "25.00").param("currency", "EUR")))
                    .andReturn().getResponse().getContentAsString();

            FieldTags.assertInvalid(html, "transfer-target");
            FieldTags.assertValid(html, "transfer-amount");
        }

        @Test
        @DisplayName("Una apertura sin documento ni depósito marca esos dos campos como erróneos y deja el resto sin marcar")
        void anOpeningWithoutDocumentOrDepositMarksThoseFieldsAsInvalid() throws Exception {
            String html = mvc.perform(post("/accounts/new").param("holderTaxId", "").param("initialDeposit", "0")
                            .param("currency", "EUR"))
                    .andReturn().getResponse().getContentAsString();

            FieldTags.assertInvalid(html, "holderTaxId");
            FieldTags.assertInvalid(html, "initialDeposit");
            FieldTags.assertValid(html, "holderName");
            FieldTags.assertValid(html, "holderEmail");
            FieldTags.assertValid(html, "ownerUsername");
            FieldTags.assertValid(html, "currency");
        }

        @Test
        @DisplayName("Una divisa inexistente marca la divisa del ingreso como errónea")
        void aMissingCurrencyMarksTheCurrencyAsInvalid() throws Exception {
            String html = mvc.perform(post("/accounts/" + ACCOUNT_ID + "/deposits").param("amount", "10.00").param("currency", "xx"))
                    .andReturn().getResponse().getContentAsString();

            FieldTags.assertInvalid(html, "deposit-currency");
        }
    }

    @Nested
    @DisplayName("Campos obligatorios y teclado móvil (#146)")
    class CamposObligatoriosYTeclado {

        @Test
        @DisplayName("En el detalle, los importes y el IBAN de destino son obligatorios y el IBAN se escribe en mayúsculas")
        void inTheDetailsTheAmountsAndTheIbanAreRequired() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            String html = mvc.perform(get("/accounts/" + ACCOUNT_ID)).andReturn().getResponse().getContentAsString();

            for (String id : List.of("deposit-amount", "withdrawal-amount", "transfer-amount", "transfer-target")) {
                assertThat(FieldTags.tag(html, id)).as(id).contains(" required");
            }
            assertThat(FieldTags.tag(html, "transfer-target")).contains("autocapitalize=\"characters\"");
            assertThat(FieldTags.tag(html, "deposit-currency")).doesNotContain(" required");
        }

        @Test
        @DisplayName("Al abrir cuenta, documento y depósito son obligatorios y los datos de cliente nuevo no")
        void whenOpeningAnAccountDocumentAndDepositAreRequired() throws Exception {
            String html = mvc.perform(get("/accounts/new")).andReturn().getResponse().getContentAsString();

            assertThat(FieldTags.tag(html, "holderTaxId")).contains(" required").contains("autocapitalize=\"characters\"");
            assertThat(FieldTags.tag(html, "initialDeposit")).contains(" required");
            for (String id : List.of("holderName", "holderEmail", "ownerUsername")) {
                assertThat(FieldTags.tag(html, id)).as(id).doesNotContain(" required");
            }
        }
    }

    @Nested
    @DisplayName("El usuario del titular se guarda en minúsculas (#152)")
    class UsuarioDelTitularEnMinusculas {

        private AccountSummary cuentaAbierta() {
            return AccountSummary.builder().id(UUID.randomUUID()).accountNumber("ES1234567890123456789012")
                    .holderName("Ana Pérez").segment("RETAIL").balance(new BigDecimal("100.00")).currency("EUR").build();
        }

        @Test
        @DisplayName("Un usuario del titular con mayúsculas y espacios alrededor se envía en minúsculas")
        void aHolderUsernameWithCapitalsIsSentInLowerCase() throws Exception {
            when(commands.openAccount(any(Caller.class), any())).thenReturn(cuentaAbierta());

            mvc.perform(post("/accounts/new").param("holderName", "Ana Pérez").param("holderTaxId", "12345678Z")
                            .param("holderEmail", "ana@example.com").param("ownerUsername", " Ana.Perez ")
                            .param("initialDeposit", "100.00").param("currency", "EUR"))
                    .andExpect(status().is3xxRedirection());

            verify(commands).openAccount(ADMIN, OpenAccountCommand.builder()
                    .holderName("Ana Pérez").holderTaxId("12345678Z").holderEmail("ana@example.com")
                    .ownerUsername("ana.perez").initialDeposit(new BigDecimal("100.00")).currency("EUR").build());
        }

        @Test
        @DisplayName("Sin usuario del titular, la apertura para un cliente existente sigue sin pedirlo")
        void withoutHolderUsernameItIsStillOptional() throws Exception {
            when(commands.openAccount(any(Caller.class), any())).thenReturn(cuentaAbierta());

            mvc.perform(post("/accounts/new").param("holderTaxId", "12345678Z").param("ownerUsername", "")
                            .param("initialDeposit", "100.00").param("currency", "EUR"))
                    .andExpect(status().is3xxRedirection());

            verify(commands).openAccount(ADMIN, OpenAccountCommand.builder()
                    .holderTaxId("12345678Z").initialDeposit(new BigDecimal("100.00")).currency("EUR").build());
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
