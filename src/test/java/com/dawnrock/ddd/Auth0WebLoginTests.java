package com.dawnrock.ddd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.dawnrock.ddd.adapter.in.ExternalIdentities;
import com.dawnrock.ddd.adapter.in.LinkedOidcUserService;
import com.dawnrock.ddd.adapter.in.PendingIdentity;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.OpenAccountCommand;

/**
 * Inicio de sesión web con Auth0 de punta a punta (#196, ADR 0024), con todos los beans reales, la H2 de los tests y un
 * tenant de Auth0 imaginario: no hace falta red, porque Auth0 nunca llega a llamarse. Un cliente vincula su identidad
 * con su contraseña, la identidad se resuelve a su usuario local y, con esa sesión, ve solo sus cuentas y cierra la
 * sesión también en Auth0.
 */
@SpringBootTest(properties = {
        "app.auth.auth0.domain=tenant.example.auth0.com",
        "app.auth.auth0.client-id=test-client",
        "app.auth.auth0.client-secret=test-secret",
        "app.security.users[0].username=admin",
        "app.security.users[0].password-hash=" + DddLabApplicationTests.DEMO_HASH,
        "app.security.users[0].role=ADMIN",
        "app.security.users[1].username=ada",
        "app.security.users[1].password-hash=" + DddLabApplicationTests.DEMO_HASH,
        "app.security.users[1].role=USER",
        "app.security.users[2].username=grace",
        "app.security.users[2].password-hash=" + DddLabApplicationTests.DEMO_HASH,
        "app.security.users[2].role=USER"})
class Auth0WebLoginTests {

    private static final String SUBJECT = "auth0|ada-123";

    private static final String AUTHORIZE = "https://tenant.example.auth0.com/authorize";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ExternalIdentities identities;

    @Autowired
    private UserDetailsService users;

    @Autowired
    private ClientRegistrationRepository registrations;

    @Autowired
    private AccountOpeningCommands commands;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("Con Auth0 configurado, el formulario de acceso ofrece entrar con Auth0 sin que htmx siga la redirección")
    void theLoginFormOffersAuth0WithoutHtmxBoost() throws Exception {
        String page = mvc.perform(get("/login").param("form", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Entrar con Auth0").contains("href=\"/oauth2/authorization/auth0\"")
                .contains("hx-boost=\"false\"");
    }

    @Test
    @DisplayName("Entrar con Auth0 lleva al tenant con PKCE (S256), state y nonce")
    void signingInWithAuth0GoesToTheTenantWithPkce() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/auth0"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(location).startsWith(AUTHORIZE).contains("code_challenge=").contains("code_challenge_method=S256")
                .contains("state=").contains("nonce=").contains("client_id=test-client");
    }

    @Test
    @DisplayName("Un cliente vincula su identidad de Auth0 con su contraseña, entra con su usuario y su rol, ve solo sus "
            + "cuentas y cierra la sesión también en Auth0")
    void aCustomerLinksTheIdentitySignsInSeesOnlyTheirAccountsAndLogsOutOfAuth0() throws Exception {
        Caller admin = Caller.administrator("admin");
        UUID adasAccount = commands.openAccount(admin, account("Ada Lovelace", "12345678Z", "ada")).id();
        UUID gracesAccount = commands.openAccount(admin, account("Grace Hopper", "87654321X", "grace")).id();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingIdentity.SESSION_ATTRIBUTE,
                new PendingIdentity(ExternalIdentities.AUTH0, SUBJECT, "ada@example.com", Instant.now()));

        // Vincula: usuario con mayúsculas (se normaliza) y la contraseña local; vuelve a Auth0 para entrar
        mvc.perform(post("/login/link").session(session).with(csrf())
                        .param("username", "Ada").param("password", DddLabApplicationTests.PASSWORD))
                .andExpect(redirectedUrl("/oauth2/authorization/auth0"));

        assertThat(identities.usernameOf(ExternalIdentities.AUTH0, SUBJECT)).contains("ada");
        assertThat(session.getAttribute(PendingIdentity.SESSION_ATTRIBUTE)).isNull();

        // De vuelta de Auth0, el sub se resuelve al usuario local con su rol de la base de datos
        OidcUser user = linkedUserFor(SUBJECT);
        assertThat(user.getName()).isEqualTo("ada");
        assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");

        // Con esa sesión llega a su única cuenta y la de otro cliente no existe para ella (404, ADR 0008)
        ClientRegistration auth0 = registrations.findByRegistrationId(ExternalIdentities.AUTH0);
        mvc.perform(get("/").with(oidcLogin().clientRegistration(auth0).oidcUser(user)))
                .andExpect(redirectedUrl("/accounts/" + adasAccount));
        String detail = mvc.perform(get("/accounts/" + adasAccount)
                        .with(oidcLogin().clientRegistration(auth0).oidcUser(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("Ada Lovelace");
        mvc.perform(get("/accounts/" + gracesAccount).with(oidcLogin().clientRegistration(auth0).oidcUser(user)))
                .andExpect(status().isNotFound());

        // Y al salir, Auth0 también cierra su sesión y devuelve al aviso de sesión cerrada
        String logout = mvc.perform(post("/logout").with(oidcLogin().clientRegistration(auth0).oidcUser(user))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(logout).startsWith("https://tenant.example.auth0.com/oidc/logout").contains("id_token_hint=")
                .contains("post_logout_redirect_uri=").contains("login?logout");
    }

    /**
     * Lo que haría Spring al volver de Auth0: el servicio real de usuarios vinculados, con un delegado que devuelve el
     * usuario OIDC sin llamar a {@code /userinfo} (no hay red ni tenant de verdad).
     */
    private OidcUser linkedUserFor(String subject) {
        Instant now = Instant.now();
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token").subject(subject)
                .issuer("https://tenant.example.auth0.com/").claim("email", "ada@example.com")
                .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        LinkedOidcUserService service = new LinkedOidcUserService(request -> new DefaultOidcUser(List.of(), idToken),
                identities, users, context.getBeanProvider(Clock.class));
        OidcUserRequest request = new OidcUserRequest(registrations.findByRegistrationId(ExternalIdentities.AUTH0),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(60)),
                idToken);
        return service.loadUser(request);
    }

    private static OpenAccountCommand account(String holderName, String taxId, String owner) {
        return OpenAccountCommand.of(holderName, taxId, owner + "@example.com", new BigDecimal("100"), owner);
    }
}
