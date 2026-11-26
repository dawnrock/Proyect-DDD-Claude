package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import com.dawnrock.ddd.adapter.in.LinkedOidcUserService;
import com.dawnrock.ddd.adapter.in.PendingIdentity;
import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;
import com.dawnrock.ddd.adapter.in.UnlinkedIdentityException;

/**
 * Cadena web con {@code oauth2Login()} (#196, ADR 0024) sobre un registro {@code auth0} de un dominio falso, con el
 * servicio de usuarios simulado.
 */
@WebMvcTest(LoginController.class)
@Import({WebSecurityConfig.class, SecuritySupport.class, TestUsers.class, Auth0TestRegistration.class})
class WebSecurityConfigOAuth2Tests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClientRegistrationRepository registrations;

    @MockitoBean
    private LinkedOidcUserService linkedOidcUserService;

    @Test
    @DisplayName("GET /oauth2/authorization/auth0 lleva al /authorize del dominio con state, nonce y PKCE S256")
    void theAuthorizationRouteRedirectsToAuth0WithStateNonceAndPkce() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/auth0"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        URI uri = URI.create(location);
        assertThat(uri.getScheme() + "://" + uri.getHost() + uri.getPath())
                .isEqualTo("https://tenant.example.auth0.com/authorize");
        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        assertThat(params.getFirst("response_type")).isEqualTo("code");
        assertThat(params.getFirst("client_id")).isEqualTo("test-client");
        assertThat(params.getFirst("scope")).contains("openid");
        assertThat(params.getFirst("state")).isNotBlank();
        assertThat(params.getFirst("nonce")).isNotBlank();
        assertThat(params.getFirst("code_challenge")).isNotBlank();
        assertThat(params.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(params.getFirst("redirect_uri")).isEqualTo("http://localhost/login/oauth2/code/auth0");
    }

    @Test
    @DisplayName("Un retorno de Auth0 sin petición de autorización previa acaba en /login?oauth-error")
    void aCallbackWithoutAuthorizationRequestEndsInOauthError() throws Exception {
        mvc.perform(get("/login/oauth2/code/auth0").param("code", "x").param("state", "y"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?oauth-error"));
    }

    @Test
    @DisplayName("El manejador de fallos guarda la identidad pendiente en la sesión y lleva a /login/link si la causa es UnlinkedIdentityException")
    void theFailureHandlerStoresThePendingIdentityAndGoesToLink() throws Exception {
        PendingIdentity pending = new PendingIdentity("auth0", "auth0|123", "ada@example.com", Instant.now());
        // Spring envuelve el fallo del servicio de usuarios: el manejador busca en la cadena de causas
        OAuth2AuthenticationException wrapped = new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info_response"),
                new UnlinkedIdentityException(pending));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OAuth2LoginFailureHandler().onAuthenticationFailure(request, response, wrapped);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login/link");
        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(PendingIdentity.SESSION_ATTRIBUTE)).isEqualTo(pending);
    }

    @Test
    @DisplayName("El manejador de fallos lleva cualquier otro fallo a /login?oauth-error sin tocar la sesión")
    void theFailureHandlerSendsAnyOtherFailureToOauthError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OAuth2LoginFailureHandler().onAuthenticationFailure(request, response, new BadCredentialsException("x"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauth-error");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("Cerrar sesión con una sesión de Auth0 lleva al /oidc/logout del dominio con id_token_hint y el retorno a /login?logout")
    void logoutOfAnAuth0SessionGoesToAuth0WithTheIdTokenHint() throws Exception {
        OidcIdToken idToken = OidcIdToken.withTokenValue("token-de-prueba").subject("auth0|123")
                .issuer("https://tenant.example.auth0.com/").build();

        String location = mvc.perform(post("/logout").with(csrf())
                        .with(oidcLogin().clientRegistration(registrations.findByRegistrationId("auth0")).idToken(
                                token -> token.tokenValue(idToken.getTokenValue()))))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(location).startsWith("https://tenant.example.auth0.com/oidc/logout?")
                .contains("id_token_hint=token-de-prueba")
                .contains("post_logout_redirect_uri=http://localhost/login?logout");
    }

    @Test
    @DisplayName("Cerrar sesión con una sesión de formulario lleva a /login?logout, sin pasar por Auth0")
    void logoutOfAFormSessionGoesToTheLoginNotice() throws Exception {
        mvc.perform(post("/logout").with(csrf()).with(user("ada")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }
}
