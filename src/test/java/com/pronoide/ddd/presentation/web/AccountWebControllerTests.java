package com.pronoide.ddd.presentation.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.pronoide.ddd.application.AccountDetails;
import com.pronoide.ddd.application.AccountNotFoundException;
import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.DepositCommand;
import com.pronoide.ddd.application.WithdrawCommand;
import com.pronoide.ddd.domain.model.CurrencyMismatchException;
import com.pronoide.ddd.application.TransactionSummary;
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

    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static TransactionSummary transaction(String type, String amount, String status) {
        return TransactionSummary.builder()
                .id(UUID.randomUUID())
                .type(type)
                .amount(new BigDecimal(amount))
                .currency("EUR")
                .occurredOn(Instant.parse("2026-10-23T10:15:00Z"))
                .status(status)
                .build();
    }

    private static AccountDetails details(TransactionSummary... transactions) {
        return AccountDetails.builder()
                .id(ACCOUNT_ID)
                .accountNumber("ES1234567890123456789012")
                .holderName("Ana Pérez")
                .segment("HIGH_NET_WORTH")
                .balance(new BigDecimal("150.00"))
                .currency("EUR")
                .addTransactions(transactions)
                .build();
    }

    @Nested
    @DisplayName("Detalle de cuenta")
    class Detalle {

        @Test
        @DisplayName("El detalle muestra los datos de la cuenta, el segmento en español y sus movimientos")
        void el_detalle_muestra_los_datos_de_la_cuenta_el_segmento_en_espanol_y_sus_movimientos() throws Exception {
            when(service.findAccount(ACCOUNT_ID)).thenReturn(details(
                    transaction("DEPOSIT", "100.00", "APPROVED"),
                    transaction("WITHDRAWAL", "30.00", "APPROVED")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(content().string(containsString("ES1234567890123456789012")))
                    .andExpect(content().string(containsString("Ana Pérez")))
                    .andExpect(content().string(containsString("Alto patrimonio")))
                    .andExpect(content().string(containsString("150.00 EUR")))
                    .andExpect(content().string(containsString("Ingreso")))
                    .andExpect(content().string(containsString("Retirada")))
                    .andExpect(content().string(containsString("100.00 EUR")))
                    .andExpect(content().string(containsString("Aprobado")))
                    .andExpect(content().string(containsString("/2026")));
        }

        @Test
        @DisplayName("Una retirada rechazada se muestra marcada como rechazada")
        void una_retirada_rechazada_se_muestra_marcada_como_rechazada() throws Exception {
            when(service.findAccount(ACCOUNT_ID)).thenReturn(details(
                    transaction("WITHDRAWAL", "500.00", "REJECTED")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("row-rejected")))
                    .andExpect(content().string(containsString("Rechazado")));
        }

        @Test
        @DisplayName("Una cuenta sin movimientos muestra el mensaje de estado vacío")
        void una_cuenta_sin_movimientos_muestra_el_mensaje_de_estado_vacio() throws Exception {
            when(service.findAccount(ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Todavía no hay movimientos")));
        }

        @Test
        @DisplayName("Una cuenta desconocida devuelve la página 404 con estado 404")
        void una_cuenta_desconocida_devuelve_la_pagina_404_con_estado_404() throws Exception {
            when(service.findAccount(ACCOUNT_ID)).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"))
                    .andExpect(content().string(containsString("Página no encontrada")));
        }

        @Test
        @DisplayName("Cada fila del listado enlaza con el detalle de su cuenta")
        void cada_fila_del_listado_enlaza_con_el_detalle_de_su_cuenta() throws Exception {
            when(service.listAccounts()).thenReturn(List.of(AccountSummary.builder()
                    .id(ACCOUNT_ID)
                    .accountNumber("ES1234567890123456789012")
                    .holderName("Ana Pérez")
                    .balance(new BigDecimal("150.00"))
                    .currency("EUR")
                    .build()));

            mvc.perform(get("/"))
                    .andExpect(content().string(containsString("href=\"/accounts/" + ACCOUNT_ID + "\"")));
        }
    }

    @Nested
    @DisplayName("Ingresos")
    class Ingresos {

        private MockHttpServletRequestBuilder ingresoDe(String amount, String currency) {
            return post("/accounts/" + ACCOUNT_ID + "/deposits")
                    .param("amount", amount)
                    .param("currency", currency);
        }

        @Test
        @DisplayName("El detalle ofrece el formulario de ingreso con euro, dólar y libra, y el euro por defecto")
        void el_detalle_ofrece_el_formulario_de_ingreso_con_euro_dolar_y_libra_y_el_euro_por_defecto()
                throws Exception {
            when(service.findAccount(ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(content().string(containsString("/accounts/" + ACCOUNT_ID + "/deposits")))
                    .andExpect(content().string(containsString("name=\"amount\"")))
                    .andExpect(content().string(containsString("value=\"USD\"")))
                    .andExpect(content().string(containsString("value=\"GBP\"")))
                    .andExpect(content().string(matchesPattern("(?s).*<option value=\"EUR\" selected=\"selected\".*")));
        }

        @Test
        @DisplayName("Un ingreso válido llama al servicio con el comando esperado y redirige al detalle con un mensaje")
        void un_ingreso_valido_llama_al_servicio_con_el_comando_esperado_y_redirige_al_detalle_con_un_mensaje()
                throws Exception {
            when(service.deposit(any())).thenReturn(transaction("DEPOSIT", "50.00", "APPROVED"));

            mvc.perform(ingresoDe("50.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                    .andExpect(flash().attribute("message", "Ingreso de 50.00 EUR aprobado"));

            verify(service).deposit(DepositCommand.builder()
                    .accountId(ACCOUNT_ID)
                    .amount(new BigDecimal("50.00"))
                    .currency("EUR")
                    .build());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-5.00"})
        @DisplayName("Un importe no positivo vuelve al detalle con el error del campo y sin llamar al servicio")
        void un_importe_no_positivo_vuelve_al_detalle_con_el_error_del_campo_y_sin_llamar_al_servicio(String amount)
                throws Exception {
            when(service.findAccount(ACCOUNT_ID)).thenReturn(details());

            mvc.perform(ingresoDe(amount, "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasFieldErrors("depositForm", "amount"))
                    .andExpect(model().attributeHasNoErrors("withdrawalForm"))
                    .andExpect(content().string(containsString("El importe debe ser positivo")))
                    .andExpect(content().string(containsString("ES1234567890123456789012")));

            verify(service, never()).deposit(any());
        }

        @Test
        @DisplayName("Un importe ausente vuelve al detalle con el error del campo")
        void un_importe_ausente_vuelve_al_detalle_con_el_error_del_campo() throws Exception {
            when(service.findAccount(ACCOUNT_ID)).thenReturn(details());

            mvc.perform(post("/accounts/" + ACCOUNT_ID + "/deposits").param("currency", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("El importe es obligatorio")));

            verify(service, never()).deposit(any());
        }

        @Test
        @DisplayName("Otra divisa vuelve al detalle con el mensaje del dominio y conserva el importe")
        void otra_divisa_vuelve_al_detalle_con_el_mensaje_del_dominio_y_conserva_el_importe() throws Exception {
            var error = new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD"));
            when(service.findAccount(ACCOUNT_ID)).thenReturn(details());
            when(service.deposit(any())).thenThrow(error);

            mvc.perform(ingresoDe("75.50", "USD"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("depositForm"))
                    .andExpect(model().attributeHasNoErrors("withdrawalForm"))
                    .andExpect(content().string(containsString(error.getMessage())))
                    .andExpect(content().string(containsString("75.50")));
        }

        @Test
        @DisplayName("Un ingreso en una cuenta desconocida devuelve la página 404")
        void un_ingreso_en_una_cuenta_desconocida_devuelve_la_pagina_404() throws Exception {
            when(service.deposit(any())).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

            mvc.perform(ingresoDe("10.00", "EUR"))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"));
        }
    }

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
        void el_detalle_ofrece_el_formulario_de_retirada() throws Exception {
            when(service.findAccount(ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(content().string(containsString("/accounts/" + ACCOUNT_ID + "/withdrawals")));
        }

        @Test
        @DisplayName("Una retirada aprobada redirige al detalle con un mensaje de éxito")
        void una_retirada_aprobada_redirige_al_detalle_con_un_mensaje_de_exito() throws Exception {
            when(service.withdraw(any())).thenReturn(transaction("WITHDRAWAL", "30.00", "APPROVED"));

            mvc.perform(retiradaDe("30.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                    .andExpect(flash().attribute("message", "Retirada de 30.00 EUR aprobada"))
                    .andExpect(flash().attributeCount(1));

            verify(service).withdraw(WithdrawCommand.builder()
                    .accountId(ACCOUNT_ID)
                    .amount(new BigDecimal("30.00"))
                    .currency("EUR")
                    .build());
        }

        @Test
        @DisplayName("Una retirada rechazada redirige al detalle con un aviso de rechazo distinto del de éxito")
        void una_retirada_rechazada_redirige_al_detalle_con_un_aviso_de_rechazo_distinto_del_de_exito()
                throws Exception {
            when(service.withdraw(any())).thenReturn(transaction("WITHDRAWAL", "500.00", "REJECTED"));

            mvc.perform(retiradaDe("500.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                    .andExpect(flash().attributeExists("rejection"))
                    .andExpect(flash().attributeCount(1));
        }

        @Test
        @DisplayName("El aviso de rechazo se pinta con el estilo de advertencia y no con el de éxito")
        void el_aviso_de_rechazo_se_pinta_con_el_estilo_de_advertencia_y_no_con_el_de_exito() throws Exception {
            when(service.findAccount(ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID).flashAttr("rejection", "Retirada de 500.00 EUR rechazada"))
                    .andExpect(content().string(containsString("flash flash-warning")))
                    .andExpect(content().string(containsString("Retirada de 500.00 EUR rechazada")));
        }

        @Test
        @DisplayName("Un importe no positivo vuelve al detalle con el error del campo y sin llamar al servicio")
        void un_importe_no_positivo_vuelve_al_detalle_con_el_error_del_campo_y_sin_llamar_al_servicio()
                throws Exception {
            when(service.findAccount(ACCOUNT_ID)).thenReturn(details());

            mvc.perform(retiradaDe("-1", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasFieldErrors("withdrawalForm", "amount"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(content().string(containsString("El importe debe ser positivo")));

            verify(service, never()).withdraw(any());
        }

        @Test
        @DisplayName("Otra divisa vuelve al detalle con el mensaje del dominio y conserva el importe")
        void otra_divisa_vuelve_al_detalle_con_el_mensaje_del_dominio_y_conserva_el_importe() throws Exception {
            var error = new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("GBP"));
            when(service.findAccount(ACCOUNT_ID)).thenReturn(details());
            when(service.withdraw(any())).thenThrow(error);

            mvc.perform(retiradaDe("20.25", "GBP"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("withdrawalForm"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(content().string(containsString(error.getMessage())))
                    .andExpect(content().string(containsString("20.25")));
        }

        @Test
        @DisplayName("Una retirada en una cuenta desconocida devuelve la página 404")
        void una_retirada_en_una_cuenta_desconocida_devuelve_la_pagina_404() throws Exception {
            when(service.withdraw(any())).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

            mvc.perform(retiradaDe("10.00", "EUR"))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"));
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
