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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.dawnrock.ddd.application.AccountDetails;
import com.dawnrock.ddd.application.AccountNotFoundException;
import com.dawnrock.ddd.application.AccountSummary;

/** El administrador sustituye el IBAN de una cuenta por otro generado (#95, R31); un cliente no puede. */
@WebMvcTest({AccountNumberWebController.class, AccountDetailController.class})
class AccountNumberWebControllerTests extends AccountWebTestSupport {

    private static AccountDetails retailDetails() {
        return AccountDetails.builder().id(ACCOUNT_ID).accountNumber("ES2199990001220000000001").holderName("Ada Lovelace")
                .segment("RETAIL").balance(new BigDecimal("100.00")).currency("EUR").build();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("El administrador ve «Generar nuevo IBAN» con un paso de confirmación en la página")
    void theAdministratorSeesGenerateNewIbanWithConfirmation() throws Exception {
        when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(retailDetails());

        mvc.perform(get("/accounts/" + ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/detail"))
                .andExpect(content().string(containsString("Generar nuevo IBAN")))
                .andExpect(content().string(containsString("action=\"/accounts/" + ACCOUNT_ID + "/account-number\"")))
                .andExpect(content().string(containsString("Confirmar")));
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Un cliente no ve «Generar nuevo IBAN»")
    void aCustomerDoesNotSeeGenerateNewIban() throws Exception {
        when(queries.findAccount(ADA, ACCOUNT_ID)).thenReturn(retailDetails());

        mvc.perform(get("/accounts/" + ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Generar nuevo IBAN"))))
                .andExpect(content().string(not(containsString("/account-number"))));
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Un cliente que envía el cambio recibe 403 y no se llama al servicio")
    void aCustomerSendingTheChangeGets403() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/accounts/" + ACCOUNT_ID + "/account-number").with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/error/403"));

        verifyNoInteractions(commands, movements, queries);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("El envío sin token CSRF se rechaza con 403")
    void aSubmissionWithoutCsrfTokenIsRejectedWith403() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/accounts/" + ACCOUNT_ID + "/account-number"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(commands, movements, queries);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("El administrador cambia el IBAN, vuelve al detalle y ve el aviso con el IBAN nuevo")
    void theAdministratorChangesTheIbanAndReturnsToTheDetailsWithTheNotice() throws Exception {
        when(commands.regenerateAccountNumber(ADMIN, ACCOUNT_ID)).thenReturn(new AccountSummary(ACCOUNT_ID,
                "ES6499990001810000000002", "Ada Lovelace", "RETAIL", new BigDecimal("100.00"), "EUR"));

        mvc.perform(MockMvcRequestBuilders.post("/accounts/" + ACCOUNT_ID + "/account-number").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                .andExpect(flash().attribute("message", "Nuevo IBAN asignado: ES6499990001810000000002"));

        verify(commands).regenerateAccountNumber(ADMIN, ACCOUNT_ID);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Con htmx, el administrador recibe el panel con el aviso y el IBAN nuevo, sin redirigir (#125)")
    void withHtmxTheAdministratorGetsThePanelWithTheNewIban() throws Exception {
        String newIban = "ES6499990001810000000002";
        when(commands.regenerateAccountNumber(ADMIN, ACCOUNT_ID)).thenReturn(new AccountSummary(ACCOUNT_ID, newIban,
                "Ada Lovelace", "RETAIL", new BigDecimal("100.00"), "EUR"));
        when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(AccountDetails.builder().id(ACCOUNT_ID).accountNumber(newIban)
                .holderName("Ada Lovelace").segment("RETAIL").balance(new BigDecimal("100.00")).currency("EUR").build());

        mvc.perform(MockMvcRequestBuilders.post("/accounts/" + ACCOUNT_ID + "/account-number").with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/detail :: panel"))
                .andExpect(model().attribute("message", "Nuevo IBAN asignado: " + newIban))
                .andExpect(content().string(containsString("<span class=\"text-nowrap\">" + newIban + "</span>")));
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Con htmx, un cliente que envía el cambio sigue recibiendo 403 (#125)")
    void withHtmxACustomerStillGets403() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/accounts/" + ACCOUNT_ID + "/account-number").with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(commands, movements, queries);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Una cuenta inexistente muestra la página 404")
    void aMissingAccountShowsThe404Page() throws Exception {
        when(commands.regenerateAccountNumber(ADMIN, ACCOUNT_ID)).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

        mvc.perform(MockMvcRequestBuilders.post("/accounts/" + ACCOUNT_ID + "/account-number").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }
}
