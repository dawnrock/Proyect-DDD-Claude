package com.dawnrock.ddd.presentation.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dawnrock.ddd.application.AccountDetails;
import com.dawnrock.ddd.application.AccountNotFoundException;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.CheckingAccountService;
import com.dawnrock.ddd.presentation.SecuritySupport;

/** El administrador sustituye el IBAN de una cuenta por otro generado (#95, R31); un cliente no puede. */
@WebMvcTest(AccountWebController.class)
@Import({WebFormat.class, WebSecurityConfig.class, SecuritySupport.class})
class RegenerateAccountNumberWebTests {

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();
    private static final Caller ADA = Caller.builder().username("ada").admin(false).build();
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CheckingAccountService service;

    private static AccountDetails details() {
        return AccountDetails.builder().id(ID).accountNumber("ES2199990001220000000001").holderName("Ada Lovelace")
                .segment("RETAIL").balance(new BigDecimal("100.00")).currency("EUR").build();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("El administrador ve «Generar nuevo IBAN» con un paso de confirmación en la página")
    void el_administrador_ve_generar_nuevo_iban_con_confirmacion() throws Exception {
        when(service.findAccount(ADMIN, ID)).thenReturn(details());

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
    void un_cliente_no_ve_generar_nuevo_iban() throws Exception {
        when(service.findAccount(ADA, ID)).thenReturn(details());

        mvc.perform(get("/accounts/" + ID))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Generar nuevo IBAN"))))
                .andExpect(content().string(not(containsString("/account-number"))));
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Un cliente que envía el cambio recibe 403 y no se llama al servicio")
    void un_cliente_que_envia_el_cambio_recibe_403() throws Exception {
        mvc.perform(post("/accounts/" + ID + "/account-number").with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/error/403"));

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("El envío sin token CSRF se rechaza con 403")
    void el_envio_sin_token_csrf_se_rechaza_con_403() throws Exception {
        mvc.perform(post("/accounts/" + ID + "/account-number"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("El administrador cambia el IBAN, vuelve al detalle y ve el aviso con el IBAN nuevo")
    void el_administrador_cambia_el_iban_y_vuelve_al_detalle_con_el_aviso() throws Exception {
        when(service.regenerateAccountNumber(ADMIN, ID)).thenReturn(AccountSummary.builder().id(ID)
                .accountNumber("ES6499990001810000000002").holderName("Ada Lovelace")
                .balance(new BigDecimal("100.00")).currency("EUR").build());

        mvc.perform(post("/accounts/" + ID + "/account-number").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/accounts/" + ID))
                .andExpect(flash().attribute("message", "Nuevo IBAN asignado: ES6499990001810000000002"));

        verify(service).regenerateAccountNumber(ADMIN, ID);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Una cuenta inexistente muestra la página 404")
    void una_cuenta_inexistente_muestra_la_pagina_404() throws Exception {
        when(service.regenerateAccountNumber(ADMIN, ID)).thenThrow(new AccountNotFoundException(ID));

        mvc.perform(post("/accounts/" + ID + "/account-number").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }
}
