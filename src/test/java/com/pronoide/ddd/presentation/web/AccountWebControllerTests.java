package com.pronoide.ddd.presentation.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.CheckingAccountService;

@WebMvcTest(AccountWebController.class)
class AccountWebControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CheckingAccountService service;

    @Nested
    @DisplayName("Listado de cuentas")
    class Listado {

        @Test
        @DisplayName("Con cuentas, el listado muestra número, titular y saldo con su divisa")
        void con_cuentas_el_listado_muestra_numero_titular_y_saldo_con_su_divisa() throws Exception {
            when(service.listAccounts()).thenReturn(List.of(AccountSummary.builder()
                    .id(UUID.randomUUID())
                    .accountNumber("ES1234567890123456789012")
                    .holderName("Ana Pérez")
                    .balance(new BigDecimal("150.00"))
                    .currency("EUR")
                    .build()));

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("ES1234567890123456789012")))
                    .andExpect(content().string(containsString("Ana Pérez")))
                    .andExpect(content().string(containsString("150.00 EUR")))
                    .andExpect(content().string(containsString("/accounts/new")));
        }

        @Test
        @DisplayName("Sin cuentas, el listado muestra el mensaje de estado vacío")
        void sin_cuentas_el_listado_muestra_el_mensaje_de_estado_vacio() throws Exception {
            when(service.listAccounts()).thenReturn(List.of());

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Todavía no hay cuentas")))
                    .andExpect(content().string(containsString("Abrir cuenta")));
        }
    }
}
