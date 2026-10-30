package com.pronoide.ddd.adapter.in.web;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import com.pronoide.ddd.adapter.in.SecuritySupport;
import com.pronoide.ddd.application.AccountDetails;
import com.pronoide.ddd.application.AccountNotFoundException;
import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.Caller;
import com.pronoide.ddd.application.ForbiddenOperationException;
import com.pronoide.ddd.application.DepositCommand;
import com.pronoide.ddd.application.TargetAccountNotFoundException;
import com.pronoide.ddd.application.TransferCommand;
import com.pronoide.ddd.application.TransferSummary;
import com.pronoide.ddd.application.WithdrawCommand;
import com.pronoide.ddd.domain.model.CurrencyMismatchException;
import com.pronoide.ddd.application.TransactionSummary;
import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.application.OpenAccountCommand;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.DuplicateAccountNumberException;
import com.pronoide.ddd.domain.model.InsufficientInitialDepositException;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.SameAccountTransferException;

@WebMvcTest(AccountWebController.class)
@Import({WebFormat.class, WebSecurityConfig.class, SecuritySupport.class})
@WithMockUser(username = "admin", roles = "ADMIN")
class AccountWebControllerTests {

    @Autowired
    private MockMvc mvc;

    /** POST con token CSRF: la cadena web lo exige (ADR 0008). */
    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url).with(csrf());
    }

    @MockitoBean
    private CheckingAccountService service;

    @Nested
    @DisplayName("Listado de cuentas")
    class Listado {

        @Test
        @DisplayName("Con cuentas, el listado muestra el saldo total y cada cuenta con iniciales, IBAN abreviado y saldo")
        void con_cuentas_el_listado_muestra_el_saldo_total_y_cada_cuenta() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.listAccounts(any(Caller.class))).thenReturn(List.of(
                    summary(id, "ES1234567890123456789012", "Ana Pérez", "150.00"),
                    summary(UUID.randomUUID(), "ES7921000813610123456789", "Grace Hopper", "5000.00")));

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Saldo total")))
                    .andExpect(content().string(containsString("5.150,00 €")))
                    .andExpect(content().string(containsString("ES12 •••• 9012")))
                    .andExpect(content().string(containsString("title=\"ES1234567890123456789012\"")))
                    .andExpect(content().string(containsString(">AP<")))
                    .andExpect(content().string(containsString("Ana Pérez")))
                    .andExpect(content().string(containsString("150,00 €")))
                    .andExpect(content().string(containsString("href=\"/accounts/" + id + "\"")))
                    .andExpect(content().string(containsString("/accounts/new")));
        }

        private static AccountSummary summary(UUID id, String accountNumber, String holder, String balance) {
            return AccountSummary.builder()
                    .id(id)
                    .accountNumber(accountNumber)
                    .holderName(holder)
                    .balance(new BigDecimal(balance))
                    .currency("EUR")
                    .build();
        }

        @Test
        @DisplayName("Sin cuentas, el listado muestra el mensaje de estado vacío")
        void sin_cuentas_el_listado_muestra_el_mensaje_de_estado_vacio() throws Exception {
            when(service.listAccounts(any(Caller.class))).thenReturn(List.of());

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Todavía no hay cuentas")))
                    .andExpect(content().string(containsString("Abrir cuenta")));
        }
    }

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();

    private static final Caller ADA = Caller.builder().username("ada").admin(false).build();

    @Nested
    @DisplayName("Detalle y operaciones con el usuario")
    class DetalleConUsuario {

        private static final UUID FOREIGN_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario consulta el detalle de su cuenta: el servicio recibe a ada como usuario")
        void un_usuario_consulta_el_detalle_de_su_cuenta() throws Exception {
            when(service.findAccount(ADA, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"));
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario que pide una cuenta ajena recibe la página 404")
        void un_usuario_que_pide_una_cuenta_ajena_recibe_la_pagina_404() throws Exception {
            when(service.findAccount(ADA, FOREIGN_ID)).thenThrow(new AccountNotFoundException(FOREIGN_ID));

            mvc.perform(get("/accounts/" + FOREIGN_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"))
                    .andExpect(content().string(containsString("Página no encontrada")))
                    .andExpect(content().string(containsString("ada")));
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario que ingresa en una cuenta ajena recibe la página 404")
        void un_usuario_que_ingresa_en_una_cuenta_ajena_recibe_la_pagina_404() throws Exception {
            when(service.deposit(eq(ADA), any())).thenThrow(new AccountNotFoundException(FOREIGN_ID));

            mvc.perform(post("/accounts/" + FOREIGN_ID + "/deposits").param("amount", "10.00").param("currency", "EUR"))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"));
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario con varias cuentas ve el enlace «← Mis cuentas»")
        void un_usuario_con_varias_cuentas_ve_el_enlace_mis_cuentas() throws Exception {
            when(service.findAccount(ADA, ACCOUNT_ID)).thenReturn(details());
            when(service.listAccounts(ADA)).thenReturn(List.of(
                    ownAccount(ACCOUNT_ID, "ES1234567890123456789012", "Ada Lovelace"),
                    ownAccount(UUID.randomUUID(), "ES9876543210987654321098", "Ada Lovelace")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(content().string(containsString("<a class=\"back\" href=\"/\">← Mis cuentas</a>")));
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario con una sola cuenta no ve enlace de vuelta")
        void un_usuario_con_una_sola_cuenta_no_ve_enlace_de_vuelta() throws Exception {
            when(service.findAccount(ADA, ACCOUNT_ID)).thenReturn(details());
            when(service.listAccounts(ADA)).thenReturn(List.of(
                    ownAccount(ACCOUNT_ID, "ES1234567890123456789012", "Ada Lovelace")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("class=\"back\""))));
        }

        @Test
        @DisplayName("Una operación que el servicio prohíbe por el rol (403) se muestra con la página 403")
        void una_operacion_prohibida_por_el_rol_se_muestra_con_la_pagina_403() throws Exception {
            when(service.deposit(any(Caller.class), any())).thenThrow(new ForbiddenOperationException("abrir cuentas"));

            mvc.perform(post("/accounts/" + ACCOUNT_ID + "/deposits").param("amount", "10.00").param("currency", "EUR"))
                    .andExpect(status().isForbidden())
                    .andExpect(view().name("error/403"))
                    .andExpect(content().string(containsString("Acceso denegado")));
        }
    }

    private static AccountSummary ownAccount(UUID id, String number, String holder) {
        return AccountSummary.builder()
                .id(id)
                .accountNumber(number)
                .holderName(holder)
                .balance(new BigDecimal("150.00"))
                .currency("EUR")
                .build();
    }

    @Nested
    @DisplayName("Cabecera con el usuario")
    class Cabecera {

        @Test
        @DisplayName("Toda página con sesión muestra el usuario y un botón Cerrar sesión que envía un POST con CSRF")
        void toda_pagina_con_sesion_muestra_el_usuario_y_un_boton_cerrar_sesion_con_csrf() throws Exception {
            when(service.listAccounts(any(Caller.class))).thenReturn(List.of());

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("admin")))
                    .andExpect(content().string(containsString("action=\"/logout\"")))
                    .andExpect(content().string(containsString("method=\"post\"")))
                    .andExpect(content().string(containsString("name=\"_csrf\"")))
                    .andExpect(content().string(containsString("Cerrar sesión")));
        }

        @Test
        @DisplayName("La página 404 también lleva la cabecera con el usuario")
        void la_pagina_404_tambien_lleva_la_cabecera_con_el_usuario() throws Exception {
            when(service.findAccount(any(Caller.class), any(UUID.class)))
                    .thenThrow(new AccountNotFoundException(UUID.randomUUID()));

            mvc.perform(get("/accounts/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(content().string(containsString("Cerrar sesión")));
        }

        @Test
        @DisplayName("La página de login, anónima, no muestra el botón Cerrar sesión")
        @WithAnonymousUser
        void la_pagina_de_login_anonima_no_muestra_el_boton_cerrar_sesion() throws Exception {
            mvc.perform(get("/error/403"))
                    .andExpect(content().string(not(containsString("Cerrar sesión"))));
        }
    }

    @Nested
    @DisplayName("Inicio según el rol")
    class InicioSegunElRol {

        private static final UUID ADA_ONE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        private static final UUID ADA_TWO = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        @Test
        @DisplayName("El administrador ve «Cuentas» con el botón de abrir cuenta y pide el listado como administrador")
        void el_administrador_ve_cuentas_con_el_boton_de_abrir_cuenta() throws Exception {
            when(service.listAccounts(any(Caller.class))).thenReturn(List.of(
                    ownAccount(ADA_ONE, "ES1234567890123456789012", "Ada Lovelace")));

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<h1>Cuentas</h1>")))
                    .andExpect(content().string(containsString("+ Abrir cuenta")))
                    .andExpect(content().string(not(containsString("Mis cuentas"))));

            verify(service).listAccounts(Caller.builder().username("admin").admin(true).build());
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario con varias cuentas ve «Mis cuentas» sin abrir cuenta")
        void un_usuario_con_varias_cuentas_ve_mis_cuentas_sin_abrir_cuenta() throws Exception {
            when(service.listAccounts(any(Caller.class))).thenReturn(List.of(
                    ownAccount(ADA_ONE, "ES1234567890123456789012", "Ada Lovelace"),
                    ownAccount(ADA_TWO, "ES9876543210987654321098", "Ada Lovelace")));

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<h1>Mis cuentas</h1>")))
                    .andExpect(content().string(containsString("Saldo total")))
                    .andExpect(content().string(containsString("300,00 €")))
                    .andExpect(content().string(not(containsString("Abrir cuenta"))))
                    .andExpect(content().string(not(containsString("/accounts/new"))));

            verify(service).listAccounts(Caller.builder().username("ada").admin(false).build());
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario con una sola cuenta es redirigido a su detalle")
        void un_usuario_con_una_sola_cuenta_es_redirigido_a_su_detalle() throws Exception {
            when(service.listAccounts(any(Caller.class))).thenReturn(List.of(
                    ownAccount(ADA_ONE, "ES1234567890123456789012", "Ada Lovelace")));

            mvc.perform(get("/"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ADA_ONE));
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario sin cuentas ve «Mis cuentas» con el mensaje de estado vacío y sin abrir cuenta")
        void un_usuario_sin_cuentas_ve_mis_cuentas_con_el_mensaje_de_estado_vacio() throws Exception {
            when(service.listAccounts(any(Caller.class))).thenReturn(List.of());

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<h1>Mis cuentas</h1>")))
                    .andExpect(content().string(containsString("Todavía no tienes cuentas")))
                    .andExpect(content().string(not(containsString("Abrir cuenta"))));
        }

        @Test
        @DisplayName("Un administrador con una sola cuenta sigue viendo el listado")
        void un_administrador_con_una_sola_cuenta_sigue_viendo_el_listado() throws Exception {
            when(service.listAccounts(any(Caller.class))).thenReturn(List.of(
                    ownAccount(ADA_ONE, "ES1234567890123456789012", "Ada Lovelace")));

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/list"));
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
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details(
                    transaction("DEPOSIT", "100.00", "APPROVED"),
                    transaction("WITHDRAWAL", "30.00", "APPROVED")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(content().string(containsString("ES1234567890123456789012")))
                    .andExpect(content().string(containsString("Ana Pérez")))
                    .andExpect(content().string(containsString("Alto patrimonio")))
                    .andExpect(content().string(containsString("150,00 €")))
                    .andExpect(content().string(containsString("Ingreso")))
                    .andExpect(content().string(containsString("Retirada")))
                    .andExpect(content().string(containsString("+100,00 €")))
                    .andExpect(content().string(containsString("−30,00 €")))
                    .andExpect(content().string(containsString("/2026")));
        }

        @Test
        @DisplayName("El detalle enlaza de vuelta al listado")
        void el_detalle_enlaza_de_vuelta_al_listado() throws Exception {
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<a class=\"back\" href=\"/\">← Cuentas</a>")));
        }

        @Test
        @DisplayName("Una retirada rechazada se muestra marcada como rechazada")
        void una_retirada_rechazada_se_muestra_marcada_como_rechazada() throws Exception {
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details(
                    transaction("WITHDRAWAL", "500.00", "REJECTED")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("row-rejected")))
                    .andExpect(content().string(containsString(">500,00 €<")))
                    .andExpect(content().string(not(containsString("−500,00 €"))))
                    .andExpect(content().string(containsString("Rechazado")));
        }

        @Test
        @DisplayName("Una cuenta sin movimientos muestra el mensaje de estado vacío")
        void una_cuenta_sin_movimientos_muestra_el_mensaje_de_estado_vacio() throws Exception {
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Todavía no hay movimientos")));
        }

        @Test
        @DisplayName("Una cuenta desconocida devuelve la página 404 con estado 404")
        void una_cuenta_desconocida_devuelve_la_pagina_404_con_estado_404() throws Exception {
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"))
                    .andExpect(content().string(containsString("Página no encontrada")));
        }

        @Test
        @DisplayName("Cada fila del listado enlaza con el detalle de su cuenta")
        void cada_fila_del_listado_enlaza_con_el_detalle_de_su_cuenta() throws Exception {
            when(service.listAccounts(any(Caller.class))).thenReturn(List.of(AccountSummary.builder()
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
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

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
            when(service.deposit(any(Caller.class), any())).thenReturn(transaction("DEPOSIT", "50.00", "APPROVED"));

            mvc.perform(ingresoDe("50.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                    .andExpect(flash().attribute("message", "Ingreso de 50,00 € aprobado"));

            verify(service).deposit(ADMIN, DepositCommand.builder()
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
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(ingresoDe(amount, "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasFieldErrors("depositForm", "amount"))
                    .andExpect(model().attributeHasNoErrors("withdrawalForm"))
                    .andExpect(content().string(containsString("El importe debe ser positivo")))
                    .andExpect(content().string(containsString("ES1234567890123456789012")));

            verify(service, never()).deposit(any(Caller.class), any());
        }

        @Test
        @DisplayName("Un importe ausente vuelve al detalle con el error del campo")
        void un_importe_ausente_vuelve_al_detalle_con_el_error_del_campo() throws Exception {
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(post("/accounts/" + ACCOUNT_ID + "/deposits").param("currency", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("El importe es obligatorio")));

            verify(service, never()).deposit(any(Caller.class), any());
        }

        @Test
        @DisplayName("Otra divisa vuelve al detalle con el mensaje del dominio y conserva el importe")
        void otra_divisa_vuelve_al_detalle_con_el_mensaje_del_dominio_y_conserva_el_importe() throws Exception {
            var error = new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD"));
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
            when(service.deposit(any(Caller.class), any())).thenThrow(error);

            mvc.perform(ingresoDe("75.50", "USD"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("depositForm"))
                    .andExpect(model().attributeHasNoErrors("withdrawalForm"))
                    .andExpect(content().string(containsString(error.getMessage())))
                    .andExpect(content().string(containsString("75.50")));
        }

        @ParameterizedTest
        @CsvSource({"0.001, EUR, El importe debe ser positivo", "10.00, XYZ, XYZ no es un c"})
        @DisplayName("Un importe que redondea a cero o una divisa inexistente vuelven al detalle con el error del command")
        void un_importe_que_redondea_a_cero_o_una_divisa_inexistente_vuelven_al_detalle_con_el_error_del_command(
                String amount, String currency, String message) throws Exception {
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(ingresoDe(amount, currency))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("depositForm"))
                    .andExpect(model().attributeHasNoErrors("withdrawalForm"))
                    .andExpect(content().string(containsString(message)))
                    .andExpect(content().string(containsString(amount)));

            verify(service, never()).deposit(any(Caller.class), any());
        }

        @Test
        @DisplayName("Un ingreso en una cuenta desconocida devuelve la página 404")
        void un_ingreso_en_una_cuenta_desconocida_devuelve_la_pagina_404() throws Exception {
            when(service.deposit(any(Caller.class), any())).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

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
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(content().string(containsString("/accounts/" + ACCOUNT_ID + "/withdrawals")));
        }

        @Test
        @DisplayName("Una retirada aprobada redirige al detalle con un mensaje de éxito")
        void una_retirada_aprobada_redirige_al_detalle_con_un_mensaje_de_exito() throws Exception {
            when(service.withdraw(any(Caller.class), any())).thenReturn(transaction("WITHDRAWAL", "30.00", "APPROVED"));

            mvc.perform(retiradaDe("30.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                    .andExpect(flash().attribute("message", "Retirada de 30,00 € aprobada"))
                    .andExpect(flash().attributeCount(1));

            verify(service).withdraw(ADMIN, WithdrawCommand.builder()
                    .accountId(ACCOUNT_ID)
                    .amount(new BigDecimal("30.00"))
                    .currency("EUR")
                    .build());
        }

        @Test
        @DisplayName("Una retirada rechazada redirige al detalle con un aviso de rechazo distinto del de éxito")
        void una_retirada_rechazada_redirige_al_detalle_con_un_aviso_de_rechazo_distinto_del_de_exito()
                throws Exception {
            when(service.withdraw(any(Caller.class), any())).thenReturn(transaction("WITHDRAWAL", "500.00", "REJECTED"));

            mvc.perform(retiradaDe("500.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                    .andExpect(flash().attribute("rejection",
                            "Retirada de 500,00 € rechazada: el saldo resultante no está permitido para esta cuenta"))
                    .andExpect(flash().attributeCount(1));
        }

        @Test
        @DisplayName("El aviso de rechazo se pinta con el estilo de advertencia y no con el de éxito")
        void el_aviso_de_rechazo_se_pinta_con_el_estilo_de_advertencia_y_no_con_el_de_exito() throws Exception {
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID).flashAttr("rejection", "Retirada de 500.00 EUR rechazada"))
                    .andExpect(content().string(containsString("flash flash-warning")))
                    .andExpect(content().string(containsString("role=\"alert\"")))
                    .andExpect(content().string(containsString("Retirada de 500.00 EUR rechazada")));
        }

        @Test
        @DisplayName("Un importe no positivo vuelve al detalle con el error del campo y sin llamar al servicio")
        void un_importe_no_positivo_vuelve_al_detalle_con_el_error_del_campo_y_sin_llamar_al_servicio()
                throws Exception {
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(retiradaDe("-1", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasFieldErrors("withdrawalForm", "amount"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(content().string(containsString("El importe debe ser positivo")));

            verify(service, never()).withdraw(any(Caller.class), any());
        }

        @Test
        @DisplayName("Otra divisa vuelve al detalle con el mensaje del dominio y conserva el importe")
        void otra_divisa_vuelve_al_detalle_con_el_mensaje_del_dominio_y_conserva_el_importe() throws Exception {
            var error = new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("GBP"));
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
            when(service.withdraw(any(Caller.class), any())).thenThrow(error);

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
        void un_importe_que_redondea_a_cero_o_una_divisa_inexistente_vuelven_al_detalle_con_el_error_del_command(
                String amount, String currency, String message) throws Exception {
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(retiradaDe(amount, currency))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("withdrawalForm"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(content().string(containsString(message)))
                    .andExpect(content().string(containsString(amount)));

            verify(service, never()).withdraw(any(Caller.class), any());
        }

        @Test
        @DisplayName("Una retirada en una cuenta desconocida devuelve la página 404")
        void una_retirada_en_una_cuenta_desconocida_devuelve_la_pagina_404() throws Exception {
            when(service.withdraw(any(Caller.class), any())).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

            mvc.perform(retiradaDe("10.00", "EUR"))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"));
        }
    }

    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String TARGET_IBAN = "ES9876543210987654321098";

    @Nested
    @DisplayName("Formulario de transferencia")
    class FormularioDeTransferencia {

        @Test
        @DisplayName("El destino es un campo de texto para el IBAN y el detalle no lista cuentas de otros clientes")
        void el_destino_es_un_campo_de_texto_para_el_iban_y_no_lista_cuentas_de_otros_clientes() throws Exception {
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
            when(service.listAccounts(any(Caller.class))).thenReturn(List.of(
                    ownAccount(ACCOUNT_ID, "ES1234567890123456789012", "Ana Pérez"),
                    ownAccount(OTHER_ID, TARGET_IBAN, "Luis Gil")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeExists("transferForm"))
                    .andExpect(content().string(containsString("/accounts/" + ACCOUNT_ID + "/transfers")))
                    .andExpect(content().string(containsString("name=\"targetAccountNumber\"")))
                    .andExpect(content().string(containsString("type=\"text\"")))
                    .andExpect(content().string(not(containsString("name=\"targetAccountId\""))))
                    .andExpect(content().string(not(containsString("<select id=\"transfer-target\""))))
                    .andExpect(content().string(not(containsString(TARGET_IBAN))))
                    .andExpect(content().string(not(containsString("Luis Gil"))))
                    .andExpect(content().string(not(containsString("No hay otras cuentas a las que transferir"))));
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario con varias cuentas no ve el número de las demás en el detalle de una de ellas")
        void un_usuario_con_varias_cuentas_no_ve_el_numero_de_las_demas_en_el_detalle() throws Exception {
            when(service.findAccount(ADA, ACCOUNT_ID)).thenReturn(details());
            when(service.listAccounts(ADA)).thenReturn(List.of(
                    ownAccount(ACCOUNT_ID, "ES1234567890123456789012", "Ada Lovelace"),
                    ownAccount(OTHER_ID, TARGET_IBAN, "Ada Lovelace")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString(TARGET_IBAN))));
        }
    }

    private static TransferSummary transfer(String amount, String status) {
        return TransferSummary.builder()
                .sourceAccountId(ACCOUNT_ID)
                .sourceAccountNumber("ES1234567890123456789012")
                .targetAccountId(OTHER_ID)
                .targetAccountNumber(TARGET_IBAN)
                .amount(new BigDecimal(amount))
                .currency("EUR")
                .occurredOn(Instant.parse("2026-10-25T10:15:00Z"))
                .status(status)
                .build();
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
        void una_transferencia_a_un_iban_escrito_llama_al_servicio_con_el_comando_esperado()
                throws Exception {
            when(service.transfer(any(Caller.class), any())).thenReturn(transfer("25.00", "APPROVED"));

            mvc.perform(transferenciaDe("es98 7654 3210 9876 5432 1098", "25.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                    .andExpect(flash().attribute("message",
                            "Transferencia de 25,00 € a ES98 •••• 1098 aprobada"))
                    .andExpect(flash().attributeCount(1));

            verify(service).transfer(ADMIN, TransferCommand.builder()
                    .sourceAccountId(ACCOUNT_ID)
                    .targetAccountNumber("es98 7654 3210 9876 5432 1098")
                    .amount(new BigDecimal("25.00"))
                    .currency("EUR")
                    .build());
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario transfiere desde su cuenta: el servicio recibe a ada como usuario")
        void un_usuario_transfiere_desde_su_cuenta() throws Exception {
            when(service.transfer(eq(ADA), any())).thenReturn(transfer("25.00", "APPROVED"));

            mvc.perform(transferenciaDe(TARGET_IBAN, "25.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID));

            verify(service).transfer(eq(ADA), any());
        }

        @Test
        @DisplayName("Una transferencia rechazada redirige con un aviso de rechazo distinto del de éxito")
        void una_transferencia_rechazada_redirige_con_un_aviso_de_rechazo_distinto_del_de_exito() throws Exception {
            when(service.transfer(any(Caller.class), any())).thenReturn(transfer("900.00", "REJECTED"));

            mvc.perform(transferenciaDe(TARGET_IBAN, "900.00", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ACCOUNT_ID))
                    .andExpect(flash().attribute("rejection",
                            "Transferencia de 900,00 € a ES98 •••• 1098 rechazada: "
                                    + "el saldo resultante no está permitido para la cuenta de origen"))
                    .andExpect(flash().attributeCount(1));
        }

        @Test
        @DisplayName("Una divisa vacía se trata como el euro por defecto")
        void una_divisa_vacia_se_trata_como_el_euro_por_defecto() throws Exception {
            when(service.transfer(any(Caller.class), any())).thenReturn(transfer("25.00", "APPROVED"));

            mvc.perform(post("/accounts/" + ACCOUNT_ID + "/transfers")
                            .param("targetAccountNumber", TARGET_IBAN)
                            .param("amount", "25.00")
                            .param("currency", ""))
                    .andExpect(status().is3xxRedirection());

            verify(service).transfer(ADMIN, TransferCommand.builder()
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
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
        }

        @Test
        @DisplayName("Sin IBAN de destino vuelve al detalle con el error del campo y sin llamar al servicio")
        void sin_iban_de_destino_vuelve_al_detalle_con_el_error_del_campo_y_sin_llamar_al_servicio()
                throws Exception {
            mvc.perform(transferenciaDe("", "25.00", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasFieldErrors("transferForm", "targetAccountNumber"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(model().attributeHasNoErrors("withdrawalForm"))
                    .andExpect(content().string(containsString("Escribe el número de cuenta de destino")));

            verify(service, never()).transfer(any(Caller.class), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"ES12-345", "ES12 3456", "FR7630006000011234567890189", "ES98765432109876543210981"})
        @DisplayName("Un IBAN mal formado vuelve al detalle con el error del campo, conserva lo escrito y no llama al servicio")
        void un_iban_mal_formado_vuelve_al_detalle_con_el_error_del_campo(String iban) throws Exception {
            mvc.perform(transferenciaDe(iban, "25.00", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasFieldErrors("transferForm", "targetAccountNumber"))
                    .andExpect(content().string(containsString("ES seguido de 22 dígitos")))
                    .andExpect(content().string(containsString("value=\"" + iban + "\"")))
                    .andExpect(content().string(containsString("<details id=\"transfer-action\" open=\"open\">")));

            verify(service, never()).transfer(any(Caller.class), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-5.00"})
        @DisplayName("Un importe no positivo vuelve al detalle con el error del campo y sin llamar al servicio")
        void un_importe_no_positivo_vuelve_al_detalle_con_el_error_del_campo_y_sin_llamar_al_servicio(String amount)
                throws Exception {
            mvc.perform(transferenciaDe(TARGET_IBAN, amount, "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasFieldErrors("transferForm", "amount"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(model().attributeHasNoErrors("withdrawalForm"))
                    .andExpect(content().string(containsString("El importe debe ser positivo")))
                    .andExpect(content().string(containsString("value=\"" + TARGET_IBAN + "\"")));

            verify(service, never()).transfer(any(Caller.class), any());
        }

        @Test
        @DisplayName("Transferir a la misma cuenta vuelve al detalle con el mensaje del dominio")
        void transferir_a_la_misma_cuenta_vuelve_al_detalle_con_el_mensaje_del_dominio() throws Exception {
            var error = new SameAccountTransferException(new AccountNumber("ES1234567890123456789012"));
            when(service.transfer(any(Caller.class), any())).thenThrow(error);

            mvc.perform(transferenciaDe("ES1234567890123456789012", "25.00", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("transferForm"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(content().string(containsString(error.getMessage())));
        }

        @Test
        @DisplayName("Otra divisa vuelve al detalle con el mensaje del dominio y conserva el importe")
        void otra_divisa_vuelve_al_detalle_con_el_mensaje_del_dominio_y_conserva_el_importe() throws Exception {
            var error = new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD"));
            when(service.transfer(any(Caller.class), any())).thenThrow(error);

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
        void un_iban_de_destino_que_no_existe_se_muestra_en_el_formulario_no_como_pagina_404() throws Exception {
            var error = new TargetAccountNotFoundException(TARGET_IBAN);
            when(service.transfer(any(Caller.class), any())).thenThrow(error);

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
        void una_cuenta_de_origen_desconocida_o_ajena_devuelve_la_pagina_404() throws Exception {
            when(service.transfer(any(Caller.class), any())).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

            mvc.perform(transferenciaDe(TARGET_IBAN, "25.00", "EUR"))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"));
        }

        @Test
        @DisplayName("Un error de formato que solo detecta el servicio vuelve al formulario con su mensaje")
        void un_error_de_formato_que_solo_detecta_el_servicio_vuelve_al_formulario_con_su_mensaje()
                throws Exception {
            when(service.transfer(any(Caller.class), any())).thenThrow(new IllegalArgumentException("Dato inválido"));

            mvc.perform(transferenciaDe(TARGET_IBAN, "25.00", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("transferForm"))
                    .andExpect(content().string(containsString("Dato inválido")));
        }

        @ParameterizedTest
        @CsvSource({"0.001, EUR, El importe debe ser positivo", "10.00, XYZ, XYZ no es un c"})
        @DisplayName("Un importe que redondea a cero o una divisa inexistente vuelven al detalle con el error del command")
        void un_importe_que_redondea_a_cero_o_una_divisa_inexistente_vuelven_al_detalle_con_el_error_del_command(
                String amount, String currency, String message) throws Exception {
            mvc.perform(transferenciaDe(TARGET_IBAN, amount, currency))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeHasErrors("transferForm"))
                    .andExpect(model().attributeHasNoErrors("depositForm"))
                    .andExpect(content().string(containsString(message)))
                    .andExpect(content().string(containsString(amount)));

            verify(service, never()).transfer(any(Caller.class), any());
        }
    }

    @Nested
    @DisplayName("Acciones del detalle")
    class Acciones {

        @BeforeEach
        void cuentas() {
            when(service.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
        }

        @Test
        @DisplayName("Al abrir el detalle, ingresar, retirar y transferir son pastillas con el formulario cerrado")
        void al_abrir_el_detalle_las_acciones_estan_cerradas() throws Exception {
            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<details id=\"deposit-action\">")))
                    .andExpect(content().string(containsString("<details id=\"withdrawal-action\">")))
                    .andExpect(content().string(containsString("<details id=\"transfer-action\">")))
                    .andExpect(content().string(not(containsString("open=\"open\""))));
        }

        @Test
        @DisplayName("Un error en el ingreso vuelve al detalle con solo el formulario de ingreso abierto")
        void un_error_en_el_ingreso_abre_solo_ese_formulario() throws Exception {
            mvc.perform(post("/accounts/" + ACCOUNT_ID + "/deposits").param("amount", "0").param("currency", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<details id=\"deposit-action\" open=\"open\">")))
                    .andExpect(content().string(containsString("<details id=\"withdrawal-action\">")))
                    .andExpect(content().string(containsString("<details id=\"transfer-action\">")));
        }

        @Test
        @DisplayName("Un error en la transferencia vuelve al detalle con solo el formulario de transferencia abierto")
        void un_error_en_la_transferencia_abre_solo_ese_formulario() throws Exception {
            mvc.perform(post("/accounts/" + ACCOUNT_ID + "/transfers").param("amount", "10.00").param("currency", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<details id=\"transfer-action\" open=\"open\">")))
                    .andExpect(content().string(containsString("<details id=\"deposit-action\">")));
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
            when(service.openAccount(any(Caller.class), any())).thenReturn(AccountSummary.builder()
                    .id(UUID.randomUUID())
                    .accountNumber("ES1234567890123456789012")
                    .holderName("Ana Pérez")
                    .balance(new BigDecimal("100.00"))
                    .currency("EUR")
                    .build());

            mvc.perform(post("/accounts/new")
                            .param("accountNumber", "es12 3456 7890 1234 5678 9012")
                            .param("holderName", "Ana Pérez")
                            .param("ownerUsername", "ana")
                            .param("segment", "RETAIL")
                            .param("initialDeposit", "100.00")
                            .param("currency", "EUR"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"))
                    .andExpect(flash().attributeExists("message"));

            verify(service).openAccount(ADMIN, OpenAccountCommand.builder()
                    .accountNumber("es12 3456 7890 1234 5678 9012")
                    .holderName("Ana Pérez")
                    .segment("RETAIL")
                    .initialDeposit(new BigDecimal("100.00"))
                    .currency("EUR")
                    .ownerUsername("ana")
                    .build());
        }

        @Test
        @DisplayName("El formulario pide el usuario del titular")
        void el_formulario_pide_el_usuario_del_titular() throws Exception {
            mvc.perform(get("/accounts/new"))
                    .andExpect(content().string(containsString("Usuario del titular")))
                    .andExpect(content().string(containsString("name=\"ownerUsername\"")));
        }
    }

    @Nested
    @DisplayName("Errores del alta de cuenta")
    class ErroresDelAlta {

        private MockHttpServletRequestBuilder altaCon(String accountNumber, String holderName, String deposit) {
            return altaDe(accountNumber, holderName, deposit, "ana");
        }

        @Test
        @DisplayName("Un usuario del titular ausente vuelve al formulario con el error del campo y sin llamar al servicio")
        void un_usuario_del_titular_ausente_vuelve_al_formulario_con_el_error_del_campo() throws Exception {
            mvc.perform(altaDe("ES1234567890123456789012", "Ana Lopez", "100.00", ""))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/new"))
                    .andExpect(model().attributeHasFieldErrors("form", "ownerUsername"))
                    .andExpect(content().string(containsString("El usuario del titular es obligatorio")));

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Un usuario del titular con formato inválido vuelve al formulario y conserva lo escrito")
        void un_usuario_del_titular_con_formato_invalido_vuelve_al_formulario() throws Exception {
            mvc.perform(altaDe("ES1234567890123456789012", "Ana Lopez", "100.00", "Ana Lopez"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("form", "ownerUsername"))
                    .andExpect(content().string(containsString("minúsculas, dígitos, puntos o guiones")))
                    .andExpect(content().string(containsString("Ana Lopez")));

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Un error de formato que solo detecta el servicio vuelve al formulario con su mensaje")
        void un_error_de_formato_que_solo_detecta_el_servicio_vuelve_al_formulario() throws Exception {
            when(service.openAccount(any(Caller.class), any())).thenThrow(new IllegalArgumentException("Dato inválido"));

            mvc.perform(altaCon("ES1234567890123456789012", "Ana Lopez", "100.00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/new"))
                    .andExpect(model().attributeHasErrors("form"))
                    .andExpect(content().string(containsString("Dato inválido")));
        }

        private MockHttpServletRequestBuilder altaDe(String accountNumber, String holderName, String deposit,
                                                     String owner) {
            return post("/accounts/new")
                    .param("accountNumber", accountNumber)
                    .param("ownerUsername", owner)
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
            when(service.openAccount(any(Caller.class), any())).thenThrow(error);

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
            when(service.openAccount(any(Caller.class), any())).thenThrow(error);

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
                            .param("ownerUsername", "ana")
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
