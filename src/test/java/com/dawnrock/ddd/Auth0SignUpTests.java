package com.dawnrock.ddd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.CheckingAccountQueries;

/**
 * Alta de un cliente con su identidad de Auth0, sin contraseña local, de punta a punta (#198, ADR 0024): todos los
 * beans reales y la H2 de los tests, con un tenant de Auth0 imaginario que nunca se llama. Tras los tres pasos existen
 * el cliente, su cuenta, el usuario y el vínculo; la vuelta de Auth0 lo resuelve a su usuario, que llega a su cuenta, y
 * ese usuario no puede entrar con ninguna contraseña.
 */
@SpringBootTest(properties = {
        "app.auth.auth0.domain=tenant.example.auth0.com",
        "app.auth.auth0.client-id=test-client",
        "app.auth.auth0.client-secret=test-secret"})
class Auth0SignUpTests {

    private static final String SUBJECT = "auth0|nueva-clienta";

    private static final String USERNAME = "katherine";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ExternalIdentities identities;

    @Autowired
    private UserDetailsService users;

    @Autowired
    private ClientRegistrationRepository registrations;

    @Autowired
    private CheckingAccountQueries queries;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("Una identidad de Auth0 nueva se da de alta sin contraseña, vuelve a Auth0 y llega a su cuenta")
    void aNewAuth0IdentitySignsUpWithoutPasswordAndReachesItsAccount() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingIdentity.SESSION_ATTRIBUTE,
                new PendingIdentity(ExternalIdentities.AUTH0, SUBJECT, "katherine@example.com", Instant.now()));

        mvc.perform(get("/signup").param("auth0", "").session(session)).andExpect(status().isOk());
        mvc.perform(post("/signup").session(session).with(csrf())
                        .param("fullName", "Katherine Johnson").param("taxId", "33333333P")
                        .param("email", "katherine@example.com"))
                .andExpect(redirectedUrl("/signup/access"));
        mvc.perform(post("/signup/access").session(session).with(csrf()).param("username", USERNAME))
                .andExpect(redirectedUrl("/signup/account"));
        mvc.perform(post("/signup/account").session(session).with(csrf())
                        .param("initialDeposit", "150.00").param("currency", "EUR")
                        .param("conditionsAccepted", "true"))
                .andExpect(redirectedUrl("/oauth2/authorization/auth0"));

        // El cliente, su cuenta, el usuario y el vínculo existen; la identidad pendiente se ha ido
        assertThat(session.getAttribute(PendingIdentity.SESSION_ATTRIBUTE)).isNull();
        assertThat(identities.usernameOf(ExternalIdentities.AUTH0, SUBJECT)).contains(USERNAME);
        List<AccountSummary> accounts = queries.listAccounts(Caller.customer(USERNAME));
        assertThat(accounts).singleElement().satisfies(account ->
                assertThat(account.holderName()).isEqualTo("Katherine Johnson"));

        // De vuelta de Auth0 (silenciosa), el sub se resuelve a la nueva clienta y su sesión llega a su cuenta
        OidcUser user = linkedUserFor(SUBJECT);
        assertThat(user.getName()).isEqualTo(USERNAME);
        assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
        ClientRegistration auth0 = registrations.findByRegistrationId(ExternalIdentities.AUTH0);
        mvc.perform(get("/").with(oidcLogin().clientRegistration(auth0).oidcUser(user)))
                .andExpect(redirectedUrl("/accounts/" + accounts.getFirst().id()));

        // Sin contraseña local: la API no da token con ninguna (la vacía ni siquiera pasa la validación del cuerpo)
        for (String password : List.of("katherine", "una-contraseña-cualquiera", "demo")) {
            mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(USERNAME, password))
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.7");
                                return request;
                            }))
                    .andExpect(status().isUnauthorized());
        }
    }

    /**
     * Lo que haría Spring al volver de Auth0: el servicio real de usuarios vinculados, con un delegado que devuelve el
     * usuario OIDC sin llamar a {@code /userinfo}.
     */
    private OidcUser linkedUserFor(String subject) {
        Instant now = Instant.now();
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token").subject(subject)
                .issuer("https://tenant.example.auth0.com/").issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        LinkedOidcUserService service = new LinkedOidcUserService(request -> new DefaultOidcUser(List.of(), idToken),
                identities, users, context.getBeanProvider(Clock.class));
        return service.loadUser(new OidcUserRequest(registrations.findByRegistrationId(ExternalIdentities.AUTH0),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(60)),
                idToken));
    }
}
