package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.dawnrock.ddd.adapter.in.CustomerRegistration;
import com.dawnrock.ddd.adapter.in.PendingIdentity;
import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.SignUpLimit;
import com.dawnrock.ddd.adapter.in.TestUsers;

/**
 * Alta de cliente con una identidad de Auth0, sin contraseña local (#198, ADR 0024): el asistente de tres pasos en modo
 * Auth0. {@link CustomerRegistration} es un doble: el contrato fija su API, no su cuerpo. Contrato:
 * {@code docs/contracts/auth0-sign-up.md}.
 */
@WebMvcTest(SignUpController.class)
@Import({WebFormat.class, WebSecurityConfig.class, SecuritySupport.class, TestUsers.class,
        SignUpAuth0Tests.FixedClock.class})
class SignUpAuth0Tests {

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
    private CustomerRegistration registration;

    /** Sin preparar, nunca bloquea (Mockito devuelve un {@code Optional} vacío). */
    @MockitoBean
    private SignUpLimit signUpLimit;

    private MockHttpSession session;

    @BeforeEach
    void aSessionWithAPendingIdentity() {
        session = new MockHttpSession();
        session.setAttribute(PendingIdentity.SESSION_ATTRIBUTE, pendingCreatedMinutesAgo(5, "ada@example.com"));
    }

    private PendingIdentity pendingCreatedMinutesAgo(long minutes, String email) {
        return new PendingIdentity("auth0", "auth0|abc123", email, clock.instant().minusSeconds(minutes * 60));
    }

    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url).with(csrf());
    }

    private PendingIdentity pendingInSession() {
        return (PendingIdentity) session.getAttribute(PendingIdentity.SESSION_ATTRIBUTE);
    }

    private SignUpForm formInSession() {
        return (SignUpForm) session.getAttribute(SignUpController.FORM);
    }

    private void startAuth0Wizard() throws Exception {
        mvc.perform(get("/signup").param("auth0", "").session(session)).andExpect(status().isOk());
    }

    private void completeStep1() throws Exception {
        mvc.perform(post("/signup").session(session).param("fullName", "Ada Lovelace").param("taxId", "12345678Z")
                .param("email", "ada@example.com")).andExpect(redirectedUrl("/signup/access"));
    }

    private void completeStep2() throws Exception {
        completeStep1();
        mvc.perform(post("/signup/access").session(session).param("username", "ada"))
                .andExpect(redirectedUrl("/signup/account"));
    }

    @Nested
    @DisplayName("GET /signup?auth0")
    class Arranque {

        @Test
        @DisplayName("Sin identidad pendiente lleva a /login?link-expired")
        void withoutPendingIdentityItRedirectsToLinkExpired() throws Exception {
            mvc.perform(get("/signup").param("auth0", ""))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?link-expired"));
        }

        @Test
        @DisplayName("Con la identidad pendiente caducada la quita de la sesión y lleva a /login?link-expired")
        void withAnExpiredPendingIdentityItIsRemovedAndRedirected() throws Exception {
            session.setAttribute(PendingIdentity.SESSION_ATTRIBUTE, pendingCreatedMinutesAgo(11, "ada@example.com"));

            mvc.perform(get("/signup?auth0").session(session))
                    .andExpect(redirectedUrl("/login?link-expired"));

            assertThat(pendingInSession()).isNull();
        }

        @Test
        @DisplayName("Con identidad pendiente enseña el paso 1 con el correo de Auth0 y vuelve a la página de vinculación")
        void withAPendingIdentityItShowsStep1WithTheAuth0Email() throws Exception {
            String html = mvc.perform(get("/signup?auth0").session(session))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/data"))
                    .andExpect(content().string(containsString("Paso 1 de 3")))
                    .andReturn().getResponse().getContentAsString();

            assertThat(FieldTags.tag(html, "email")).contains("value=\"ada@example.com\"");
            assertThat(html).containsPattern("<a [^>]*href=\"/login/link\"[^>]*>");
            assertThat(formInSession().isExternalIdentity()).isTrue();
        }

        @Test
        @DisplayName("El correo de Auth0 es editable y solo informativo: el campo explica que se puede cambiar")
        void theEmailIsEditableAndOnlyInformative() throws Exception {
            String html = mvc.perform(get("/signup?auth0").session(session))
                    .andReturn().getResponse().getContentAsString();

            assertThat(FieldTags.tag(html, "email")).doesNotContain("readonly").doesNotContain("disabled")
                    .containsPattern("aria-describedby=\"email-help\"");
            assertThat(html).containsPattern("<p [^>]*id=\"email-help\"[^>]*>[^<]*(cambiar|cambiarlo)[^<]*</p>");
        }

        @Test
        @DisplayName("Sin correo en la identidad pendiente el campo queda vacío")
        void withoutEmailTheFieldIsEmpty() throws Exception {
            session.setAttribute(PendingIdentity.SESSION_ATTRIBUTE, pendingCreatedMinutesAgo(1, null));

            String html = mvc.perform(get("/signup?auth0").session(session)).andReturn().getResponse()
                    .getContentAsString();

            assertThat(FieldTags.tag(html, "email")).contains("value=\"\"").doesNotContain("null");
        }

        @Test
        @DisplayName("Sustituye cualquier asistente que hubiera en la sesión por un formulario nuevo en modo Auth0")
        void itReplacesAnyWizardInTheSession() throws Exception {
            mvc.perform(get("/signup").session(session)).andExpect(status().isOk());
            completeStep1();
            assertThat(formInSession().isExternalIdentity()).isFalse();
            assertThat(formInSession().isPersonalDataDone()).isTrue();

            mvc.perform(get("/signup?auth0").session(session)).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("Ada Lovelace"))));

            assertThat(formInSession().isExternalIdentity()).isTrue();
            assertThat(formInSession().isPersonalDataDone()).isFalse();
            assertThat(formInSession().getFullName()).isNull();
        }

        @Test
        @DisplayName("El navegador no puede cambiar de modo enviando externalIdentity")
        void theBrowserCannotSwitchModes() throws Exception {
            mvc.perform(post("/signup").session(session).param("externalIdentity", "true")
                    .param("fullName", "Ada Lovelace").param("taxId", "12345678Z").param("email", "ada@example.com"))
                    .andExpect(redirectedUrl("/signup/access"));

            assertThat(formInSession().isExternalIdentity()).isFalse();
            mvc.perform(get("/signup/access").session(session))
                    .andExpect(content().string(containsString("name=\"password\"")));
        }
    }

    @Nested
    @DisplayName("GET /signup sin auth0")
    class AltaClasica {

        @Test
        @DisplayName("Un formulario Auth0 abandonado, con la identidad pendiente caducada o ausente, se descarta y sale un paso 1 limpio")
        void aLeftoverAuth0FormWithoutPendingIdentityIsDiscarded() throws Exception {
            startAuth0Wizard();
            session.removeAttribute(PendingIdentity.SESSION_ATTRIBUTE);

            String html = mvc.perform(get("/signup").session(session)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(FieldTags.tag(html, "email")).contains("value=\"\"");
            assertThat(html).containsPattern("<a [^>]*href=\"/login\"[^>]*>");
            assertThat(formInSession().isExternalIdentity()).isFalse();
        }

        @Test
        @DisplayName("Un formulario Auth0 con identidad pendiente vigente se conserva")
        void aLeftoverAuth0FormWithAValidPendingIdentityIsKept() throws Exception {
            startAuth0Wizard();

            mvc.perform(get("/signup").session(session)).andExpect(status().isOk());

            assertThat(formInSession().isExternalIdentity()).isTrue();
        }
    }

    @Nested
    @DisplayName("Paso 1 en modo Auth0")
    class Paso1 {

        @Test
        @DisplayName("Sin identidad pendiente, el envío lleva a /login?link-expired")
        void withoutPendingIdentityThePostRedirects() throws Exception {
            startAuth0Wizard();
            session.removeAttribute(PendingIdentity.SESSION_ATTRIBUTE);

            mvc.perform(post("/signup").session(session).param("fullName", "Ada Lovelace")
                            .param("taxId", "12345678Z").param("email", "ada@example.com"))
                    .andExpect(redirectedUrl("/login?link-expired"));
        }

        @Test
        @DisplayName("Completar el paso 1 renueva la identidad pendiente: la misma identidad con createdAt = ahora")
        void completingTheStepRenewsThePendingIdentity() throws Exception {
            PendingIdentity before = pendingInSession();
            startAuth0Wizard();

            completeStep1();

            assertThat(pendingInSession()).isEqualTo(new PendingIdentity(before.provider(), before.subject(),
                    before.email(), clock.instant()));
        }

        @Test
        @DisplayName("Un paso 1 con errores no renueva la identidad pendiente")
        void aStepWithErrorsDoesNotRenewThePendingIdentity() throws Exception {
            PendingIdentity before = pendingInSession();
            startAuth0Wizard();

            mvc.perform(post("/signup").session(session).param("fullName", "").param("taxId", "x")
                            .param("email", "ada@example.com"))
                    .andExpect(status().isOk());

            assertThat(pendingInSession()).isSameAs(before);
        }
    }

    @Nested
    @DisplayName("Paso 2 en modo Auth0")
    class Paso2 {

        @BeforeEach
        void startedAndStep1Done() throws Exception {
            startAuth0Wizard();
            completeStep1();
        }

        @Test
        @DisplayName("Solo pide el usuario: no pinta campos de contraseña y explica que se entra con Auth0")
        void itAsksOnlyForTheUsername() throws Exception {
            String html = mvc.perform(get("/signup/access").session(session))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/access"))
                    .andExpect(content().string(not(containsString("name=\"password\""))))
                    .andExpect(content().string(not(containsString("name=\"passwordRepetition\""))))
                    .andExpect(content().string(containsString(
                            "Entrarás con tu cuenta de Auth0: no necesitas contraseña de monA Bank")))
                    .andReturn().getResponse().getContentAsString();

            assertThat(FieldTags.tag(html, "username")).contains("name=\"username\"")
                    .containsPattern("aria-describedby=\"[^\"]*auth0-access-note[^\"]*\"");
            assertThat(html).containsPattern("<p [^>]*id=\"auth0-access-note\"[^>]*>");
        }

        @Test
        @DisplayName("Sin identidad pendiente, entrar o enviar el paso 2 lleva a /login?link-expired")
        void withoutPendingIdentityBothGetAndPostRedirect() throws Exception {
            session.removeAttribute(PendingIdentity.SESSION_ATTRIBUTE);

            mvc.perform(get("/signup/access").session(session)).andExpect(redirectedUrl("/login?link-expired"));
            mvc.perform(post("/signup/access").session(session).param("username", "ada"))
                    .andExpect(redirectedUrl("/login?link-expired"));
        }

        @Test
        @DisplayName("Un usuario válido basta, sin contraseña: pasa al paso 3 y renueva la identidad pendiente")
        void aValidUsernameAloneMovesOnAndRenewsThePendingIdentity() throws Exception {
            PendingIdentity before = pendingInSession();

            mvc.perform(post("/signup/access").session(session).param("username", "Ada"))
                    .andExpect(redirectedUrl("/signup/account"));

            assertThat(formInSession().getUsername()).isEqualTo("ada");
            assertThat(pendingInSession()).isEqualTo(new PendingIdentity(before.provider(), before.subject(),
                    before.email(), clock.instant()));
        }

        @Test
        @DisplayName("Un usuario mal formado muestra su error como hoy, accesible, y no avanza")
        void aBadUsernameShowsItsErrorAndStays() throws Exception {
            PendingIdentity before = pendingInSession();

            String html = mvc.perform(post("/signup/access").session(session).param("username", "Ab"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/access"))
                    .andExpect(content().string(containsString("El usuario debe tener de 3 a 30")))
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).contains("id=\"username-error\"");
            assertThat(FieldTags.tag(html, "username")).contains("aria-invalid=\"true\"")
                    .containsPattern("aria-describedby=\"[^\"]*username-error[^\"]*\"")
                    .containsPattern("aria-describedby=\"[^\"]*auth0-access-note[^\"]*\"");
            assertThat(formInSession().isAccessDone()).isFalse();
            assertThat(pendingInSession()).isSameAs(before);
        }

        @Test
        @DisplayName("Un usuario en blanco es obligatorio también en modo Auth0")
        void aBlankUsernameIsRequired() throws Exception {
            mvc.perform(post("/signup/access").session(session).param("username", ""))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("El usuario es obligatorio")));
        }
    }

    @Nested
    @DisplayName("Paso 3 en modo Auth0")
    class Paso3 {

        @Test
        @DisplayName("Sin identidad pendiente, entrar en el paso 3 lleva a /login?link-expired")
        void withoutPendingIdentityTheGetRedirects() throws Exception {
            startAuth0Wizard();
            completeStep2();
            session.removeAttribute(PendingIdentity.SESSION_ATTRIBUTE);

            mvc.perform(get("/signup/account").session(session)).andExpect(redirectedUrl("/login?link-expired"));
        }

        @Test
        @DisplayName("Con identidad pendiente enseña el paso 3 y su formulario sale de htmx (hx-boost=\"false\")")
        void theStepFormLeavesHtmx() throws Exception {
            startAuth0Wizard();
            completeStep2();

            String html = mvc.perform(get("/signup/account").session(session)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).containsPattern("<form[^>]*hx-boost=\"false\"[^>]*action=\"/signup/account\"|"
                    + "<form[^>]*action=\"/signup/account\"[^>]*hx-boost=\"false\"");
        }
    }
}
