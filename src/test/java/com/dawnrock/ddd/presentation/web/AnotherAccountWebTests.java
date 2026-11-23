package com.dawnrock.ddd.presentation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.dawnrock.ddd.application.AccountDetails;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.CheckingAccountService;
import com.dawnrock.ddd.application.NotACustomerException;
import com.dawnrock.ddd.application.OpenAnotherAccountCommand;
import com.dawnrock.ddd.domain.model.CurrencyMismatchException;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.presentation.SecuritySupport;

/** «Abrir otra cuenta» para clientes (#95): el botón, el formulario de solo depósito y el rechazo al administrador. */
@WebMvcTest(AccountWebController.class)
@Import({WebFormat.class, WebSecurityConfig.class, SecuritySupport.class})
@WithMockUser(username = "ada", roles = "USER")
class AnotherAccountWebTests {

    private static final Caller ADA = Caller.builder().username("ada").admin(false).build();
    private static final UUID FIRST = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SECOND = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CheckingAccountService service;

    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url).with(csrf());
    }

    private static AccountSummary account(UUID id, String iban) {
        return AccountSummary.builder().id(id).accountNumber(iban).holderName("Ada Lovelace")
                .balance(new BigDecimal("100.00")).currency("EUR").build();
    }

    private static AccountDetails details(UUID id) {
        return AccountDetails.builder().id(id).accountNumber("ES2199990001220000000001").holderName("Ada Lovelace")
                .segment("RETAIL").balance(new BigDecimal("100.00")).currency("EUR").build();
    }

    @Nested
    @DisplayName("El botón «Abrir otra cuenta»")
    class Boton {

        @Test
        @DisplayName("Un cliente con varias cuentas lo ve en el listado")
        void un_cliente_con_varias_cuentas_lo_ve_en_el_listado() throws Exception {
            when(service.listAccounts(ADA)).thenReturn(List.of(
                    account(FIRST, "ES2199990001220000000001"), account(SECOND, "ES6499990001810000000002")));

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/list"))
                    .andExpect(content().string(containsString("href=\"/accounts/another\"")))
                    .andExpect(content().string(containsString("Abrir otra cuenta")));
        }

        @Test
        @DisplayName("Un cliente con una sola cuenta lo ve en el detalle, adonde le lleva su inicio")
        void un_cliente_con_una_sola_cuenta_lo_ve_en_el_detalle() throws Exception {
            when(service.listAccounts(ADA)).thenReturn(List.of(account(FIRST, "ES2199990001220000000001")));
            when(service.findAccount(ADA, FIRST)).thenReturn(details(FIRST));

            mvc.perform(get("/accounts/" + FIRST))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"/accounts/another\"")))
                    .andExpect(content().string(containsString("Abrir otra cuenta")));
        }

        @Test
        @DisplayName("Un usuario sin cuentas no es cliente y no lo ve")
        void un_usuario_sin_cuentas_no_es_cliente_y_no_lo_ve() throws Exception {
            when(service.listAccounts(ADA)).thenReturn(List.of());

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("/accounts/another"))));
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("El administrador no lo ve ni en el listado ni en el detalle")
        void el_administrador_no_lo_ve() throws Exception {
            Caller admin = Caller.builder().username("admin").admin(true).build();
            when(service.listAccounts(admin)).thenReturn(List.of(account(FIRST, "ES2199990001220000000001")));
            when(service.findAccount(admin, FIRST)).thenReturn(details(FIRST));

            mvc.perform(get("/"))
                    .andExpect(content().string(not(containsString("/accounts/another"))));
            mvc.perform(get("/accounts/" + FIRST))
                    .andExpect(content().string(not(containsString("/accounts/another"))));
        }
    }

    @Nested
    @DisplayName("El formulario de otra cuenta")
    class Formulario {

        @Test
        @DisplayName("Un cliente ve solo el depósito y la divisa: ni IBAN ni titular")
        void un_cliente_ve_solo_el_deposito_y_la_divisa() throws Exception {
            when(service.listAccounts(ADA)).thenReturn(List.of(account(FIRST, "ES2199990001220000000001")));

            mvc.perform(get("/accounts/another"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/another"))
                    .andExpect(content().string(containsString("name=\"_csrf\"")))
                    .andExpect(content().string(containsString("name=\"initialDeposit\"")))
                    .andExpect(content().string(containsString("name=\"currency\"")))
                    .andExpect(content().string(not(containsString("name=\"accountNumber\""))))
                    .andExpect(content().string(not(containsString("name=\"holderName\""))));
        }

        @Test
        @DisplayName("Un usuario que no es cliente recibe la página 403")
        void un_usuario_que_no_es_cliente_recibe_la_pagina_403() throws Exception {
            when(service.listAccounts(ADA)).thenReturn(List.of());

            mvc.perform(get("/accounts/another"))
                    .andExpect(status().isForbidden())
                    .andExpect(view().name("error/403"));
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("El administrador recibe 403 en el formulario y en el envío")
        void el_administrador_recibe_403() throws Exception {
            mvc.perform(get("/accounts/another"))
                    .andExpect(status().isForbidden())
                    .andExpect(forwardedUrl("/error/403"));
            mvc.perform(post("/accounts/another").param("initialDeposit", "100.00"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Sin depósito o con un depósito no positivo se queda en el formulario sin llamar al servicio")
        void sin_deposito_o_no_positivo_se_queda_en_el_formulario() throws Exception {
            mvc.perform(post("/accounts/another"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/another"))
                    .andExpect(content().string(containsString("El depósito inicial es obligatorio")));
            mvc.perform(post("/accounts/another").param("initialDeposit", "0"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("El depósito inicial debe ser positivo")));

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Con un depósito válido abre la cuenta y redirige a ella con su IBAN")
        void con_un_deposito_valido_abre_la_cuenta_y_redirige_a_ella_con_su_iban() throws Exception {
            when(service.openAnotherAccount(any(Caller.class), any(OpenAnotherAccountCommand.class)))
                    .thenReturn(account(SECOND, "ES6499990001810000000002"));

            mvc.perform(post("/accounts/another").param("initialDeposit", "75.50").param("currency", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + SECOND))
                    .andExpect(flash().attribute("message",
                            "Nueva cuenta abierta. Tu IBAN es ES6499990001810000000002"));

            ArgumentCaptor<OpenAnotherAccountCommand> command = ArgumentCaptor.forClass(OpenAnotherAccountCommand.class);
            verify(service).openAnotherAccount(any(Caller.class), command.capture());
            assertThat(command.getValue().initialDeposit()).isEqualByComparingTo("75.50");
            assertThat(command.getValue().currency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Un depósito insuficiente o una divisa distinta del euro vuelven al formulario con el mensaje del dominio")
        void un_deposito_insuficiente_o_una_divisa_distinta_vuelven_al_formulario() throws Exception {
            when(service.openAnotherAccount(any(Caller.class), any(OpenAnotherAccountCommand.class)))
                    .thenThrow(new InsufficientInitialDepositException(
                            MonetaryAmount.euros("10.00"), MonetaryAmount.euros("50.00")));

            mvc.perform(post("/accounts/another").param("initialDeposit", "10.00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/another"))
                    .andExpect(content().string(containsString("no alcanza el mínimo exigido")))
                    .andExpect(content().string(containsString("value=\"10.00\"")));

            when(service.openAnotherAccount(any(Caller.class), any(OpenAnotherAccountCommand.class)))
                    .thenThrow(new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD")));

            mvc.perform(post("/accounts/another").param("initialDeposit", "100.00").param("currency", "USD"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/another"))
                    .andExpect(model().attributeHasErrors("form"));
        }

        @Test
        @DisplayName("Si el servicio dice que no es cliente, se muestra la página 403")
        void si_el_servicio_dice_que_no_es_cliente_se_muestra_la_pagina_403() throws Exception {
            when(service.openAnotherAccount(any(Caller.class), any(OpenAnotherAccountCommand.class)))
                    .thenThrow(new NotACustomerException("ada"));

            mvc.perform(post("/accounts/another").param("initialDeposit", "100.00"))
                    .andExpect(status().isForbidden())
                    .andExpect(view().name("error/403"));
        }
    }
}
