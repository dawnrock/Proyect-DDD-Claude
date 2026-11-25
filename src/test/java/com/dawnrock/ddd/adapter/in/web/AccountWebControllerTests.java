package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;

@WebMvcTest({AccountWebController.class, AccountDetailController.class, DepositWebController.class, WithdrawalWebController.class, TransferWebController.class, AccountNumberWebController.class, OpenAccountWebController.class, AccountListController.class})
@WithMockUser(username = "admin", roles = "ADMIN")
class AccountWebControllerTests extends AccountWebTestSupport {

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

}
