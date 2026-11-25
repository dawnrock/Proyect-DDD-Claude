package com.dawnrock.ddd.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.dawnrock.ddd.application.AccountDetails;
import com.dawnrock.ddd.application.AccountNotFoundException;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;

/** El administrador sustituye el IBAN de una cuenta por otro generado (#95, R31); un cliente no puede. */
@WebMvcTest({AccountWebController.class, AccountDetailController.class, DepositWebController.class, WithdrawalWebController.class, TransferWebController.class, AccountListController.class})
class RegenerateAccountNumberWebTests extends AccountWebTestSupport {

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();
    private static final Caller ADA = Caller.builder().username("ada").admin(false).build();
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static AccountDetails details() {
        return AccountDetails.builder().id(ID).accountNumber("ES2199990001220000000001").holderName("Ada Lovelace")
                .segment("RETAIL").balance(new BigDecimal("100.00")).currency("EUR").build();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("El administrador ve «Generar nuevo IBAN» con un paso de confirmación en la página")
    void theAdministratorSeesGenerateNewIbanWithConfirmation() throws Exception {
        when(queries.findAccount(ADMIN, ID)).thenReturn(details());

        mvc.perform(get("/accounts/" + ID))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/detail"))
                .andExpect(content().string(containsString("Generar nuevo IBAN")))
                .andExpect(content().string(containsString("action=\"/accounts/" + ID + "/account-number\"")))
                .andExpect(content().string(containsString("Confirmar")));
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Un cliente no ve «Generar nuevo IBAN»")
    void aCustomerDoesNotSeeGenerateNewIban() throws Exception {
        when(queries.findAccount(ADA, ID)).thenReturn(details());

        mvc.perform(get("/accounts/" + ID))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Generar nuevo IBAN"))))
                .andExpect(content().string(not(containsString("/account-number"))));
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Un cliente que envía el cambio recibe 403 y no se llama al servicio")
    void aCustomerSendingTheChangeGets403() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/accounts/" + ID + "/account-number").with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/error/403"));

        verifyNoInteractions(commands, movements, queries);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("El envío sin token CSRF se rechaza con 403")
    void aSubmissionWithoutCsrfTokenIsRejectedWith403() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/accounts/" + ID + "/account-number"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(commands, movements, queries);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("El administrador cambia el IBAN, vuelve al detalle y ve el aviso con el IBAN nuevo")
    void theAdministratorChangesTheIbanAndReturnsToTheDetailsWithTheNotice() throws Exception {
        when(commands.regenerateAccountNumber(ADMIN, ID)).thenReturn(AccountSummary.builder().id(ID)
                .accountNumber("ES6499990001810000000002").holderName("Ada Lovelace")
                .segment("RETAIL").balance(new BigDecimal("100.00")).currency("EUR").build());

        mvc.perform(MockMvcRequestBuilders.post("/accounts/" + ID + "/account-number").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/accounts/" + ID))
                .andExpect(flash().attribute("message", "Nuevo IBAN asignado: ES6499990001810000000002"));

        verify(commands).regenerateAccountNumber(ADMIN, ID);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Con htmx, el administrador recibe el panel con el aviso y el IBAN nuevo, sin redirigir (#125)")
    void withHtmxTheAdministratorGetsThePanelWithTheNewIban() throws Exception {
        String newIban = "ES6499990001810000000002";
        when(commands.regenerateAccountNumber(ADMIN, ID)).thenReturn(AccountSummary.builder().id(ID)
                .accountNumber(newIban).holderName("Ada Lovelace")
                .segment("RETAIL").balance(new BigDecimal("100.00")).currency("EUR").build());
        when(queries.findAccount(ADMIN, ID)).thenReturn(AccountDetails.builder().id(ID).accountNumber(newIban)
                .holderName("Ada Lovelace").segment("RETAIL").balance(new BigDecimal("100.00")).currency("EUR").build());

        mvc.perform(MockMvcRequestBuilders.post("/accounts/" + ID + "/account-number").with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/detail :: panel"))
                .andExpect(model().attribute("message", "Nuevo IBAN asignado: " + newIban))
                .andExpect(content().string(containsString("<span class=\"text-nowrap\">" + newIban + "</span>")));
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Con htmx, un cliente que envía el cambio sigue recibiendo 403 (#125)")
    void withHtmxACustomerStillGets403() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/accounts/" + ID + "/account-number").with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(commands, movements, queries);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Una cuenta inexistente muestra la página 404")
    void aMissingAccountShowsThe404Page() throws Exception {
        when(commands.regenerateAccountNumber(ADMIN, ID)).thenThrow(new AccountNotFoundException(ID));

        mvc.perform(MockMvcRequestBuilders.post("/accounts/" + ID + "/account-number").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }
}
