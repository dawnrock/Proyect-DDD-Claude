package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dawnrock.ddd.adapter.in.ExternalIdentityLinking;
import com.dawnrock.ddd.adapter.in.IdentityAlreadyLinkedException;
import com.dawnrock.ddd.adapter.in.PendingIdentity;
import com.dawnrock.ddd.adapter.in.PendingIdentityExpiredException;
import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;
import com.dawnrock.ddd.adapter.in.TooManyLoginAttemptsException;

/**
 * Página {@code /login/link} (#196, ADR 0024): vincula la identidad de Auth0 pendiente con el usuario local. El
 * servicio {@link ExternalIdentityLinking} y la identidad pendiente son dobles: el contrato fija su API, no su cuerpo.
 */
@WebMvcTest(IdentityLinkController.class)
@Import({WebSecurityConfig.class, SecuritySupport.class, TestUsers.class, IdentityLinkControllerTests.FixedClock.class})
class IdentityLinkControllerTests {

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Clock clock;

    @MockitoBean
    private ExternalIdentityLinking linking;

    private PendingIdentity pending;

    private MockHttpSession session;

    @BeforeEach
    void aPendingIdentityInTheSession() {
        pending = mock(PendingIdentity.class);
        when(pending.provider()).thenReturn("auth0");
        when(pending.email()).thenReturn("ada@example.com");
        when(pending.isExpired(clock)).thenReturn(false);
        session = new MockHttpSession();
        session.setAttribute(PendingIdentity.SESSION_ATTRIBUTE, pending);
    }

    private MockHttpServletRequestBuilder submit(String username, String password) {
        return post("/login/link").with(csrf()).session(session).param("username", username)
                .param("password", password);
    }

    @Test
    @DisplayName("Sin identidad pendiente, GET /login/link lleva a /login?link-expired")
    void withoutPendingIdentityTheGetRedirectsToLinkExpired() throws Exception {
        mvc.perform(get("/login/link"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?link-expired"));
    }

    @Test
    @DisplayName("Con la identidad pendiente caducada, GET /login/link la quita de la sesión y lleva a /login?link-expired")
    void withAnExpiredPendingIdentityTheGetClearsItAndRedirects() throws Exception {
        when(pending.isExpired(clock)).thenReturn(true);

        mvc.perform(get("/login/link").session(session))
                .andExpect(redirectedUrl("/login?link-expired"));

        assertThat(session.getAttribute(PendingIdentity.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("Con identidad pendiente, la página saluda con el correo de Auth0 y pide usuario y contraseña")
    void withAPendingIdentityThePageGreetsWithTheEmailAndAsksForCredentials() throws Exception {
        String html = mvc.perform(get("/login/link").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Has entrado en Auth0 como ada@example.com. Para terminar, escribe una sola vez tu usuario "
                                + "y tu contraseña de monA Bank.")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Vincular")))
                .andExpect(content().string(matchesPattern("(?s).*<a [^>]*href=\"/login\"[^>]*>.*")))
                .andReturn().getResponse().getContentAsString();

        assertThat(FieldTags.tag(html, "username"))
                .contains("autocomplete=\"username\"")
                .contains("autocapitalize=\"none\"")
                .contains("spellcheck=\"false\"")
                .contains(" required");
        assertThat(FieldTags.tag(html, "password")).contains("autocomplete=\"current-password\"");
        FieldTags.assertPasswordToggle(html, "password");
        FieldTags.assertValid(html, "username");
        assertThat(html).containsPattern("<form[^>]*hx-boost=\"false\"[^>]*action=\"/login/link\"|"
                + "<form[^>]*action=\"/login/link\"[^>]*hx-boost=\"false\"");
    }

    @Test
    @DisplayName("Sin correo en la identidad pendiente, el saludo no lo menciona")
    void withoutEmailTheGreetingOmitsIt() throws Exception {
        when(pending.email()).thenReturn(null);

        mvc.perform(get("/login/link").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Has entrado en Auth0. Para terminar, escribe una sola vez")))
                .andExpect(content().string(not(containsString("null"))));
    }

    @Test
    @DisplayName("Vincular con éxito quita la identidad de la sesión, normaliza el usuario, pasa la IP y vuelve a Auth0")
    void aSuccessfulLinkClearsThePendingIdentityAndGoesBackToAuth0() throws Exception {
        when(linking.link(any(), anyString(), anyString(), anyString())).thenReturn("ada");

        mvc.perform(submit("  Ada ", "secreto-123").with(request -> {
                    request.setRemoteAddr("203.0.113.9");
                    return request;
                }))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/auth0"));

        verify(linking).link(pending, "ada", "secreto-123", "203.0.113.9");
        assertThat(session.getAttribute(PendingIdentity.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("POST sin identidad pendiente lleva a /login?link-expired y no vincula nada")
    void postWithoutPendingIdentityRedirectsAndLinksNothing() throws Exception {
        mvc.perform(post("/login/link").with(csrf()).param("username", "ada").param("password", "x"))
                .andExpect(redirectedUrl("/login?link-expired"));

        verify(linking, never()).link(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Si la identidad pendiente caduca al vincular, lleva a /login?link-expired")
    void anIdentityThatExpiresWhileLinkingRedirectsToLinkExpired() throws Exception {
        when(linking.link(any(), anyString(), anyString(), anyString()))
                .thenThrow(new PendingIdentityExpiredException("auth0"));

        mvc.perform(submit("ada", "secreto-123"))
                .andExpect(redirectedUrl("/login?link-expired"));

        assertThat(session.getAttribute(PendingIdentity.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("Usuario o contraseña incorrectos: la página otra vez (200) con el aviso, sin devolver la contraseña")
    void badCredentialsShowThePageAgainWithTheNotice() throws Exception {
        when(linking.link(any(), anyString(), anyString(), anyString())).thenThrow(new BadCredentialsException("x"));

        mvc.perform(submit("ada", "mala-clave"))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern(
                        "(?s).*role=\"alert\"><svg[^>]*>.*</svg><span>Usuario o contraseña incorrectos</span></div>.*")))
                .andExpect(content().string(containsString("value=\"ada\"")))
                .andExpect(content().string(not(containsString("mala-clave"))));

        assertThat(session.getAttribute(PendingIdentity.SESSION_ATTRIBUTE)).isSameAs(pending);
    }

    @Test
    @DisplayName("Con el bloqueo por intentos, la página dice los minutos del bloqueo del login")
    void aLockoutShowsTheLoginBlockingMessage() throws Exception {
        when(linking.link(any(), anyString(), anyString(), anyString()))
                .thenThrow(new TooManyLoginAttemptsException(Duration.ofMinutes(30)));

        mvc.perform(submit("ada", "x"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Demasiados intentos fallidos. Vuelve a intentarlo dentro de 30 minutos.")));
    }

    @Test
    @DisplayName("Una cuenta local deshabilitada recibe el mismo aviso que una contraseña errónea, sin revelar su estado")
    void aDisabledLocalAccountGetsTheSameNoticeAsAWrongPassword() throws Exception {
        when(linking.link(any(), anyString(), anyString(), anyString()))
                .thenThrow(new DisabledException("deshabilitada"));

        mvc.perform(submit("ada", "x"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Usuario o contraseña incorrectos")))
                .andExpect(content().string(not(containsString("deshabilitada"))));
    }

    @Test
    @DisplayName("Si el usuario ya tiene otra cuenta de Auth0, la página lo dice")
    void anAlreadyLinkedUserShowsItsNotice() throws Exception {
        when(linking.link(any(), anyString(), anyString(), anyString()))
                .thenThrow(new IdentityAlreadyLinkedException("auth0", "ada"));

        mvc.perform(submit("ada", "secreto-123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ese usuario ya está vinculado a otra cuenta de Auth0")));
    }

    @Test
    @DisplayName("Con los campos vacíos no se vincula nada y cada campo apunta a su error (#145)")
    void emptyFieldsLinkNothingAndPointToTheirErrors() throws Exception {
        String html = mvc.perform(submit("  ", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        verify(linking, never()).link(any(), any(), any(), any());
        FieldTags.assertInvalid(html, "username");
        FieldTags.assertInvalid(html, "password");
        assertThat(html).contains("El usuario es obligatorio").contains("La contraseña es obligatoria");
    }

    @Test
    @DisplayName("POST sin token CSRF es un 403")
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/login/link").session(session).param("username", "ada").param("password", "x"))
                .andExpect(status().isForbidden());
    }
}
