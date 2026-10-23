package com.pronoide.ddd.presentation.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.application.OpenAccountCommand;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.DuplicateAccountNumberException;
import com.pronoide.ddd.domain.model.InsufficientInitialDepositException;
import com.pronoide.ddd.domain.model.MonetaryAmount;

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

    @Nested
    @DisplayName("Errores del alta de cuenta")
    class ErroresDelAlta {

        private MockHttpServletRequestBuilder altaCon(String accountNumber, String holderName, String deposit) {
            return post("/accounts/new")
                    .param("accountNumber", accountNumber)
                    .param("holderName", holderName)
                    .param("segment", "RETAIL")
                    .param("initialDeposit", deposit)
                    .param("currency", "EUR");
        }

        @Test
        @DisplayName("Un titular en blanco vuelve al formulario con el error del campo y sin llamar al servicio")
        void un_titular_en_blanco_vuelve_al_formulario_con_el_error_del_campo_y_sin_llamar_al_servicio()
                throws Exception {
            mvc.perform(altaCon("ES1234567890123456789012", "  ", "100.00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/new"))
                    .andExpect(model().attributeHasFieldErrors("form", "holderName"))
                    .andExpect(content().string(containsString("El titular es obligatorio")));

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Un IBAN mal formado vuelve al formulario con el error del campo y conserva los datos")
        void un_iban_mal_formado_vuelve_al_formulario_con_el_error_del_campo_y_conserva_los_datos()
                throws Exception {
            mvc.perform(altaCon("ES12-345", "Ana Lopez", "100.00"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("form", "accountNumber"))
                    .andExpect(content().string(containsString("ES12-345")))
                    .andExpect(content().string(containsString("Ana Lopez")));

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Un depósito insuficiente vuelve al formulario con el mensaje del dominio y conserva los datos")
        void un_deposito_insuficiente_vuelve_al_formulario_con_el_mensaje_del_dominio_y_conserva_los_datos()
                throws Exception {
            var error = new InsufficientInitialDepositException(
                    MonetaryAmount.euros("10.00"), MonetaryAmount.euros("50.00"));
            when(service.openAccount(any())).thenThrow(error);

            mvc.perform(altaCon("ES1234567890123456789012", "Ana Lopez", "10.00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/new"))
                    .andExpect(model().attributeHasErrors("form"))
                    .andExpect(content().string(containsString(
                            "no alcanza el mínimo exigido")))
                    .andExpect(content().string(containsString("ES1234567890123456789012")))
                    .andExpect(content().string(containsString("Ana Lopez")));
        }

        @Test
        @DisplayName("Un número de cuenta duplicado vuelve al formulario con el mensaje del dominio")
        void un_numero_de_cuenta_duplicado_vuelve_al_formulario_con_el_mensaje_del_dominio() throws Exception {
            var error = new DuplicateAccountNumberException(new AccountNumber("ES1234567890123456789012"));
            when(service.openAccount(any())).thenThrow(error);

            mvc.perform(altaCon("ES1234567890123456789012", "Ana Lopez", "100.00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/new"))
                    .andExpect(content().string(containsString(error.getMessage())));
        }

        @Test
        @DisplayName("Una divisa bien formada pero inexistente vuelve al formulario con el error y sin llamar al servicio")
        void una_divisa_bien_formada_pero_inexistente_vuelve_al_formulario_con_el_error_y_sin_llamar_al_servicio()
                throws Exception {
            mvc.perform(post("/accounts/new")
                            .param("accountNumber", "ES1234567890123456789012")
                            .param("holderName", "Ana Lopez")
                            .param("segment", "RETAIL")
                            .param("initialDeposit", "100.00")
                            .param("currency", "XYZ"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/new"))
                    .andExpect(model().attributeHasErrors("form"))
                    .andExpect(content().string(containsString("XYZ no es un c")))
                    .andExpect(content().string(containsString("Ana Lopez")));

            verifyNoInteractions(service);
        }
    }
}
