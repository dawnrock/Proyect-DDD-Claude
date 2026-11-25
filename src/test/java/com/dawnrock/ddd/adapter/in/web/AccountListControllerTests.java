package com.dawnrock.ddd.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import com.dawnrock.ddd.application.AccountNotFoundException;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;

/** Inicio de la web (#177): listado de cuentas, inicio según el rol y cabecera con el usuario. */
@WebMvcTest({AccountListController.class, AccountDetailController.class, OpenAccountWebController.class})
@WithMockUser(username = "admin", roles = "ADMIN")
class AccountListControllerTests extends AccountWebTestSupport {

    @Nested
    @DisplayName("Listado de cuentas")
    class Listado {

        @Test
        @DisplayName("La página usa el tema oscuro de Bootstrap y carga htmx, Alpine.js, Motion y app.js desde la propia aplicación (#124)")
        void thePageUsesBootstrapDarkThemeAndLoadsTheFrontendLibraries() throws Exception {
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of());

            // Spring reescribe las rutas con la versión del WebJar (/webjars/bootstrap/5.3.8/...) o con el hash del contenido
            // (/js/app-<md5>.js): el navegador puede guardarlas en caché sin servir nunca una versión antigua
            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-bs-theme=\"dark\"")))
                    .andExpect(content().string(webjar("href", "bootstrap", "css/bootstrap.min.css")))
                    .andExpect(content().string(webjar("src", "bootstrap", "js/bootstrap.bundle.min.js")))
                    .andExpect(content().string(webjar("src", "htmx.org", "dist/htmx.min.js")))
                    .andExpect(content().string(webjar("src", "alpinejs", "dist/cdn.min.js")))
                    .andExpect(content().string(hashed("src", "/vendor/motion/motion", ".js")))
                    .andExpect(content().string(hashed("src", "/js/app", ".js")))
                    .andExpect(content().string(hashed("href", "/css/app", ".css")))
                    .andExpect(content().string(not(containsString("https://"))));
        }

        @Test
        @DisplayName("Con cuentas, el listado muestra el saldo total y cada cuenta con iniciales, IBAN abreviado y saldo")
        void withAccountsTheListShowsTheTotalBalanceAndEachAccount() throws Exception {
            UUID id = UUID.randomUUID();
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of(
                    summary(id, "ES1234567890123456789012", "Ana Pérez", "150.00"),
                    summary(UUID.randomUUID(), "ES7921000813610123456789", "Grace Hopper", "5000.00")));

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Saldo total")))
                    .andExpect(content().string(containsString("5.150,00 €")))
                    .andExpect(content().string(containsString("ES12\u00A0••••\u00A09012")))
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
                    .holderName(holder).segment("RETAIL")
                    .balance(new BigDecimal(balance))
                    .currency("EUR")
                    .build();
        }

        @Test
        @DisplayName("El listado muestra el segmento de cada cuenta junto al IBAN abreviado")
        void theListShowsEachAccountsSegment() throws Exception {
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of(
                    AccountSummary.builder().id(UUID.randomUUID()).accountNumber("ES1234567890123456789012")
                            .holderName("Ana Pérez").segment("HIGH_NET_WORTH")
                            .balance(new BigDecimal("150.00")).currency("EUR").build(),
                    summary(UUID.randomUUID(), "ES7921000813610123456789", "Grace Hopper", "5000.00")));

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Alto patrimonio")))
                    .andExpect(content().string(containsString("Minorista")));
        }

        @Test
        @DisplayName("Sin cuentas, el listado muestra el mensaje de estado vacío")
        void withoutAccountsTheListShowsTheEmptyStateMessage() throws Exception {
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of());

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Todavía no hay cuentas")))
                    .andExpect(content().string(containsString("Abrir cuenta")));
        }

        @Test
        @DisplayName("La cabecera lleva el logo de monA Bank, la pestaña su favicon y el título acaba en el nombre del banco (#178)")
        void theHeaderShowsTheBrandLogoWithFaviconAndTitle() throws Exception {
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of());

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(matchesPattern(
                            "(?s).*<a class=\"navbar-brand[^\"]*\" href=\"/\">\\s*<img src=\"/brand/monabank-horizontal[^\"]*\\.svg\""
                                    + "[^>]*alt=\"monA Bank\".*")))
                    .andExpect(content().string(matchesPattern(
                            "(?s).*<link rel=\"icon\" type=\"image/svg\\+xml\" href=\"/brand/favicon[^\"]*\\.svg\">.*")))
                    .andExpect(content().string(matchesPattern("(?s).*<title>[^<]* · monA Bank</title>.*")))
                    .andExpect(content().string(not(containsString("Cuentas corrientes"))));
        }

        @Test
        @DisplayName("Todas las páginas precargan las tipografías de la marca, Tektur y Sora, con su URL versionada (#185)")
        void everyPagePreloadsTheBrandTypefaces() throws Exception {
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of());

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(matchesPattern(
                            "(?s).*<link rel=\"preload\" href=\"/vendor/tektur/tektur-latin-wght-normal-[0-9a-f]{32}\\.woff2\""
                                    + " as=\"font\" type=\"font/woff2\" crossorigin>.*")))
                    .andExpect(content().string(matchesPattern(
                            "(?s).*<link rel=\"preload\" href=\"/vendor/sora/sora-latin-wght-normal-[0-9a-f]{32}\\.woff2\""
                                    + " as=\"font\" type=\"font/woff2\" crossorigin>.*")));
        }

        @Test
        @DisplayName("La web no tiene pie de página: ni la fase ni la arquitectura del proyecto (#178)")
        void thePageHasNoFooterWithThePhaseOrTheArchitecture() throws Exception {
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of());

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("<footer"))))
                    .andExpect(content().string(not(containsStringIgnoringCase("arquitectura hexagonal"))))
                    .andExpect(content().string(not(containsString("Proyect-DDD-Claude"))));
        }
    }

    @Nested
    @DisplayName("Cabecera con el usuario")
    class Cabecera {

        @Test
        @DisplayName("Toda página con sesión muestra el usuario y un botón Cerrar sesión que envía un POST con CSRF")
        void everyPageWithASessionShowsTheUserAndALogoutButtonWithCsrf() throws Exception {
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of());

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
        void the404PageAlsoHasTheHeaderWithTheUser() throws Exception {
            when(queries.findAccount(any(Caller.class), any(UUID.class)))
                    .thenThrow(new AccountNotFoundException(UUID.randomUUID()));

            mvc.perform(get("/accounts/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(content().string(containsString("Cerrar sesión")));
        }

        @Test
        @DisplayName("La página de login, anónima, no muestra el botón Cerrar sesión")
        @WithAnonymousUser
        void theAnonymousLoginPageShowsNoLogoutButton() throws Exception {
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
        void theAdministratorSeesAccountsWithTheOpenAccountButton() throws Exception {
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of(
                    ownAccount(ADA_ONE, "ES1234567890123456789012", "Ada Lovelace")));

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(">Cuentas</h1>")))
                    .andExpect(content().string(containsString("#plus\"></use></svg>Abrir cuenta")))
                    .andExpect(content().string(not(containsString("Mis cuentas"))));

            verify(queries).listAccounts(Caller.builder().username("admin").admin(true).build());
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario con varias cuentas ve «Mis cuentas» sin abrir cuenta")
        void aUserWithSeveralAccountsSeesMyAccountsWithoutOpenAccount() throws Exception {
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of(
                    ownAccount(ADA_ONE, "ES1234567890123456789012", "Ada Lovelace"),
                    ownAccount(ADA_TWO, "ES9876543210987654321098", "Ada Lovelace")));

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(">Mis cuentas</h1>")))
                    .andExpect(content().string(containsString("Saldo total")))
                    .andExpect(content().string(containsString("300,00 €")))
                    .andExpect(content().string(not(containsString("Abrir cuenta"))))
                    .andExpect(content().string(not(containsString("/accounts/new"))));

            verify(queries).listAccounts(Caller.builder().username("ada").admin(false).build());
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario con una sola cuenta es redirigido a su detalle")
        void aUserWithASingleAccountIsRedirectedToItsDetails() throws Exception {
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of(
                    ownAccount(ADA_ONE, "ES1234567890123456789012", "Ada Lovelace")));

            mvc.perform(get("/"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + ADA_ONE));
        }

        @Test
        @WithMockUser(username = "ada", roles = "USER")
        @DisplayName("Un usuario sin cuentas ve «Mis cuentas» con el mensaje de estado vacío y sin abrir cuenta")
        void aUserWithoutAccountsSeesMyAccountsWithTheEmptyStateMessage() throws Exception {
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of());

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(">Mis cuentas</h1>")))
                    .andExpect(content().string(containsString("Todavía no tienes cuentas")))
                    .andExpect(content().string(not(containsString("Abrir cuenta"))));
        }

        @Test
        @DisplayName("Un administrador con una sola cuenta sigue viendo el listado")
        void anAdministratorWithASingleAccountStillSeesTheList() throws Exception {
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of(
                    ownAccount(ADA_ONE, "ES1234567890123456789012", "Ada Lovelace")));

            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/list"));
        }
    }
}
