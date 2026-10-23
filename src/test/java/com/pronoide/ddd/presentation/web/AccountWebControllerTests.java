package com.pronoide.ddd.presentation.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import com.pronoide.ddd.application.OpenAccountCommand;

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

    @Nested
    @DisplayName("Alta de cuenta")
    class Alta {

        @Test
        @DisplayName("El formulario muestra los campos, el segmento en español y el euro por defecto")
        void el_formulario_muestra_los_campos_el_segmento_en_espanol_y_el_euro_por_defecto() throws Exception {
            mvc.perform(get("/accounts/new"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"accountNumber\"")))
                    .andExpect(content().string(containsString("name=\"holderName\"")))
                    .andExpect(content().string(containsString("name=\"initialDeposit\"")))
                    .andExpect(content().string(containsString("value=\"RETAIL\"")))
                    .andExpect(content().string(containsString("value=\"HIGH_NET_WORTH\"")))
                    .andExpect(content().string(containsString("Minorista")))
                    .andExpect(content().string(containsString("Alto patrimonio")))
                    .andExpect(content().string(matchesPattern("(?s).*<option value=\"EUR\" selected=\"selected\".*")));
        }

        @Test
        @DisplayName("Un alta válida llama al servicio con el comando esperado y redirige al listado con un mensaje")
        void un_alta_valida_llama_al_servicio_con_el_comando_esperado_y_redirige_al_listado_con_un_mensaje()
                throws Exception {
            when(service.openAccount(any())).thenReturn(AccountSummary.builder()
                    .id(UUID.randomUUID())
                    .accountNumber("ES1234567890123456789012")
                    .holderName("Ana Pérez")
                    .balance(new BigDecimal("100.00"))
                    .currency("EUR")
                    .build());

            mvc.perform(post("/accounts/new")
                            .param("accountNumber", "es12 3456 7890 1234 5678 9012")
                            .param("holderName", "Ana Pérez")
                            .param("segment", "RETAIL")
                            .param("initialDeposit", "100.00")
                            .param("currency", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"))
                    .andExpect(flash().attributeExists("message"));

            verify(service).openAccount(OpenAccountCommand.builder()
                    .accountNumber("es12 3456 7890 1234 5678 9012")
                    .holderName("Ana Pérez")
                    .segment("RETAIL")
                    .initialDeposit(new BigDecimal("100.00"))
                    .currency("EUR")
                    .build());
        }
    }
}
