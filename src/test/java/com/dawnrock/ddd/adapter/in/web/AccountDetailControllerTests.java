package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;

import com.dawnrock.ddd.application.AccountNotFoundException;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.ForbiddenOperationException;

/** Detalle de una cuenta (#177): movimientos, formularios de las operaciones y acciones según el rol. */
@WebMvcTest({AccountDetailController.class, AccountListController.class, AccountWebController.class, DepositWebController.class})
@WithMockUser(username = "admin", roles = "ADMIN")
class AccountDetailControllerTests extends AccountWebTestSupport {

    @Nested
    @DisplayName("Detalle y operaciones con el usuario")
    class DetalleConUsuario {

        private static final UUID FOREIGN_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario consulta el detalle de su cuenta: el servicio recibe a ada como usuario")
        void aUserViewsTheirAccountDetails() throws Exception {
            when(queries.findAccount(ADA, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"));
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario que pide una cuenta ajena recibe la página 404")
        void aUserAskingForAnotherUsersAccountGetsThe404Page() throws Exception {
            when(queries.findAccount(ADA, FOREIGN_ID)).thenThrow(new AccountNotFoundException(FOREIGN_ID));

            mvc.perform(get("/accounts/" + FOREIGN_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"))
                    .andExpect(content().string(containsString("Página no encontrada")))
                    .andExpect(content().string(containsString("ada")));
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario que ingresa en una cuenta ajena recibe la página 404")
        void aUserDepositingIntoAnotherUsersAccountGetsThe404Page() throws Exception {
            when(movements.deposit(eq(ADA), any())).thenThrow(new AccountNotFoundException(FOREIGN_ID));

            mvc.perform(post("/accounts/" + FOREIGN_ID + "/deposits").param("amount", "10.00").param("currency", "EUR"))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"));
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario con varias cuentas ve el enlace «← Mis cuentas»")
        void aUserWithSeveralAccountsSeesTheMyAccountsLink() throws Exception {
            when(queries.findAccount(ADA, ACCOUNT_ID)).thenReturn(details());
            when(queries.listAccounts(ADA)).thenReturn(List.of(
                    ownAccount(ACCOUNT_ID, "ES1234567890123456789012", "Ada Lovelace"),
                    ownAccount(UUID.randomUUID(), "ES9876543210987654321098", "Ada Lovelace")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(content().string(matchesPattern("(?s).*<a [^>]*href=\"/\"><svg [^>]*><use href=\"[^\"]*#arrow-left\"></use></svg>Mis cuentas</a>.*")));
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario con una sola cuenta no ve enlace de vuelta")
        void aUserWithASingleAccountSeesNoBackLink() throws Exception {
            when(queries.findAccount(ADA, ACCOUNT_ID)).thenReturn(details());
            when(queries.listAccounts(ADA)).thenReturn(List.of(
                    ownAccount(ACCOUNT_ID, "ES1234567890123456789012", "Ada Lovelace")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("#arrow-left\""))));
        }

        @Test
        @DisplayName("Una operación que el servicio prohíbe por el rol (403) se muestra con la página 403")
        void anOperationForbiddenByTheRoleIsShownWithThe403Page() throws Exception {
            when(movements.deposit(any(Caller.class), any())).thenThrow(new ForbiddenOperationException("abrir cuentas"));

            mvc.perform(post("/accounts/" + ACCOUNT_ID + "/deposits").param("amount", "10.00").param("currency", "EUR"))
                    .andExpect(status().isForbidden())
                    .andExpect(view().name("error/403"))
                    .andExpect(content().string(containsString("Acceso denegado")));
        }
    }

    @Nested
    @DisplayName("Detalle de cuenta")
    class Detalle {

        @Test
        @DisplayName("El detalle muestra los datos de la cuenta, el segmento en español y sus movimientos")
        void theDetailsShowTheAccountDataTheSpanishSegmentAndItsTransactions() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details(
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
        @DisplayName("Una retirada con contrapartida se muestra como transferencia a la cuenta de destino")
        void aWithdrawalWithCounterpartIsShownAsTransferTo() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details(
                    transfer("WITHDRAWAL", "30.00", "APPROVED", "ES7921000813610123456789")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(content().string(containsString("Transferencia a ES79\u00A0••••\u00A06789")))
                    .andExpect(content().string(not(containsString(">Retirada<"))));
        }

        @Test
        @DisplayName("Un ingreso con contrapartida se muestra como transferencia desde la cuenta de origen")
        void aDepositWithCounterpartIsShownAsTransferFrom() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details(
                    transfer("DEPOSIT", "30.00", "APPROVED", "ES7921000813610123456789")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(content().string(containsString("Transferencia de ES79\u00A0••••\u00A06789")))
                    .andExpect(content().string(not(containsString(">Ingreso<"))));
        }

        @Test
        @DisplayName("Una transferencia rechazada conserva el estilo de rechazada y su etiqueta de transferencia")
        void aRejectedTransferKeepsTheRejectedStyle() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details(
                    transfer("WITHDRAWAL", "500.00", "REJECTED", "ES7921000813610123456789")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(content().string(containsString("row-rejected")))
                    .andExpect(content().string(containsString("Transferencia a ES79\u00A0••••\u00A06789")))
                    .andExpect(content().string(containsString("Rechazado")));
        }

        @Test
        @DisplayName("Los movimientos se muestran del más reciente al más antiguo, aunque compartan instante")
        void transactionsAreShownFromNewestToOldest() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details(
                    transaction("DEPOSIT", "111.00", "APPROVED"),
                    transaction("DEPOSIT", "222.00", "APPROVED"),
                    transaction("DEPOSIT", "333.00", "APPROVED")));

            String html = mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andReturn().getResponse().getContentAsString();

            assertThat(html.indexOf("+333,00 €")).isPositive()
                    .isLessThan(html.indexOf("+222,00 €"));
            assertThat(html.indexOf("+222,00 €")).isLessThan(html.indexOf("+111,00 €"));
        }

        @Test
        @DisplayName("El panel de la cuenta es el destino de htmx y cada formulario lo envía con hx-post sin perder action ni CSRF (#125)")
        void theAccountPanelIsTheHtmxTarget() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            String base = "/accounts/" + ACCOUNT_ID;
            mvc.perform(get(base))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "<div id=\"account-panel\" hx-target=\"#account-panel\" hx-swap=\"outerHTML transition:true\">")))
                    .andExpect(content().string(containsString("action=\"" + base + "/deposits\" hx-post=\"" + base + "/deposits\"")))
                    .andExpect(content().string(containsString("action=\"" + base + "/withdrawals\" hx-post=\"" + base + "/withdrawals\"")))
                    .andExpect(content().string(containsString("action=\"" + base + "/transfers\" hx-post=\"" + base + "/transfers\"")))
                    .andExpect(content().string(containsString("action=\"" + base + "/account-number\" hx-post=\"" + base + "/account-number\"")))
                    .andExpect(content().string(matchesPattern("(?s)(.*name=\"_csrf\"){4}.*")));
        }

        @Test
        @DisplayName("El detalle enlaza de vuelta al listado")
        void theDetailsLinkBackToTheList() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(matchesPattern("(?s).*<a [^>]*href=\"/\"><svg [^>]*><use href=\"[^\"]*#arrow-left\"></use></svg>Cuentas</a>.*")));
        }

        @Test
        @DisplayName("Una retirada rechazada se muestra marcada como rechazada")
        void aRejectedWithdrawalIsShownMarkedAsRejected() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details(
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
        void anAccountWithoutTransactionsShowsTheEmptyStateMessage() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Todavía no hay movimientos")));
        }

        @Test
        @DisplayName("Una cuenta desconocida devuelve la página 404 con estado 404")
        void anUnknownAccountReturnsThe404PageWithStatus404() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"))
                    .andExpect(content().string(containsString("Página no encontrada")));
        }

        @Test
        @DisplayName("Cada fila del listado enlaza con el detalle de su cuenta")
        void eachListRowLinksToItsAccountDetails() throws Exception {
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of(AccountSummary.builder()
                    .id(ACCOUNT_ID)
                    .accountNumber("ES1234567890123456789012")
                    .holderName("Ana Pérez").segment("RETAIL")
                    .balance(new BigDecimal("150.00"))
                    .currency("EUR")
                    .build()));

            mvc.perform(get("/"))
                    .andExpect(content().string(containsString("href=\"/accounts/" + ACCOUNT_ID + "\"")));
        }
    }

    @Nested
    @DisplayName("Formulario de transferencia")
    class FormularioDeTransferencia {

        @Test
        @DisplayName("El destino es un campo de texto para el IBAN y el detalle no lista cuentas de otros clientes")
        void theTargetIsAnIbanTextFieldAndOtherCustomersAccountsAreNotListed() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of(
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
        void aUserWithSeveralAccountsDoesNotSeeTheOtherNumbersInTheDetails() throws Exception {
            when(queries.findAccount(ADA, ACCOUNT_ID)).thenReturn(details());
            when(queries.listAccounts(ADA)).thenReturn(List.of(
                    ownAccount(ACCOUNT_ID, "ES1234567890123456789012", "Ada Lovelace"),
                    ownAccount(OTHER_ID, TARGET_IBAN, "Ada Lovelace")));

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString(TARGET_IBAN))));
        }
    }

    @Nested
    @DisplayName("Acciones del detalle")
    class Acciones {

        @BeforeEach
        void cuentas() {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());
        }

        @Test
        @DisplayName("Al abrir el detalle, ingresar, retirar y transferir son pastillas con el formulario cerrado")
        void whenTheDetailsOpenTheActionsAreClosed() throws Exception {
            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<details id=\"deposit-action\">")))
                    .andExpect(content().string(containsString("<details id=\"withdrawal-action\">")))
                    .andExpect(content().string(containsString("<details id=\"transfer-action\">")))
                    .andExpect(content().string(not(containsString("open=\"open\""))));
        }

        @Test
        @DisplayName("Las acciones son un acordeón de Alpine.js con iconos de Lucide: abrir una cierra las demás (#124)")
        void theActionsAreAnAlpineAccordionWithLucideIcons() throws Exception {
            when(queries.findAccount(ADMIN, ACCOUNT_ID)).thenReturn(details());

            mvc.perform(get("/accounts/" + ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("x-data=\"actionPanels\"")))
                    .andExpect(content().string(containsString("x-on:toggle.capture=\"closeOthers($event)\"")))
                    .andExpect(content().string(containsString("#arrow-down-to-line\"></use></svg>Ingresar</summary>")))
                    .andExpect(content().string(containsString("#arrow-up-from-line\"></use></svg>Retirar</summary>")))
                    .andExpect(content().string(containsString("#arrow-left-right\"></use></svg>Transferir</summary>")))
                    .andExpect(content().string(containsString("#refresh-cw\"></use></svg>Generar nuevo IBAN</summary>")));
        }

        @Test
        @DisplayName("Un error en el ingreso vuelve al detalle con solo el formulario de ingreso abierto")
        void aDepositErrorOpensOnlyThatForm() throws Exception {
            mvc.perform(post("/accounts/" + ACCOUNT_ID + "/deposits").param("amount", "0").param("currency", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<details id=\"deposit-action\" open=\"open\">")))
                    .andExpect(content().string(containsString("<details id=\"withdrawal-action\">")))
                    .andExpect(content().string(containsString("<details id=\"transfer-action\">")));
        }

        @Test
        @DisplayName("Un error en la transferencia vuelve al detalle con solo el formulario de transferencia abierto")
        void aTransferErrorOpensOnlyThatForm() throws Exception {
            mvc.perform(post("/accounts/" + ACCOUNT_ID + "/transfers").param("amount", "10.00").param("currency", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<details id=\"transfer-action\" open=\"open\">")))
                    .andExpect(content().string(containsString("<details id=\"deposit-action\">")));
        }
    }
}
