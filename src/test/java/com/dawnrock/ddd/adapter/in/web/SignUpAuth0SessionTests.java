package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.SignUpCommand;

/**
 * El asistente en modo Auth0 queda ligado a su identidad y a su tiempo (#198, revisión): otra identidad del mismo
 * navegador no lo continúa, no vive más de {@link SignUpController#AUTH0_WIZARD_LIMIT} aunque la identidad se renueve,
 * y nunca lleva ni usa una contraseña local.
 */
@WebMvcTest(SignUpController.class)
@Import({WebFormat.class, WebSecurityConfig.class, SecuritySupport.class, TestUsers.class,
        SignUpAuth0SessionTests.FixedClock.class})
class SignUpAuth0SessionTests {

    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerRegistration registration;

    @MockitoBean
    private SignUpLimit signUpLimit;

    private MockHttpSession session;

    @BeforeEach
    void aSessionWithAPendingIdentity() {
        session = new MockHttpSession();
        session.setAttribute(PendingIdentity.SESSION_ATTRIBUTE, pending("auth0|ada"));
    }

    private static PendingIdentity pending(String subject) {
        return new PendingIdentity("auth0", subject, "ada@example.com", NOW);
    }

    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url).with(csrf());
    }

    private SignUpForm formInSession() {
        return (SignUpForm) session.getAttribute(SignUpController.FORM);
    }

    private void step1() throws Exception {
        mvc.perform(post("/signup").session(session).param("fullName", "Ada Lovelace").param("taxId", "12345678Z")
                .param("email", "ada@example.com")).andExpect(redirectedUrl("/signup/access"));
    }

    private MockHttpServletRequestBuilder step3() {
        return post("/signup/account").session(session).param("initialDeposit", "100.00").param("currency", "EUR")
                .param("conditionsAccepted", "true");
    }

    private static AccountSummary opened() {
        return new AccountSummary(UUID.randomUUID(), "ES2199990001220000000001", "Ada Lovelace", "RETAIL",
                new BigDecimal("100.00"), "EUR");
    }

    @Test
    @DisplayName("Otra identidad de Auth0 del mismo navegador no puede continuar el alta a medias de la primera")
    void anotherIdentityCannotContinueTheWizardOfTheFirst() throws Exception {
        mvc.perform(get("/signup").param("auth0", "").session(session)).andExpect(status().isOk());
        step1();
        session.setAttribute(PendingIdentity.SESSION_ATTRIBUTE, pending("auth0|otra"));

        mvc.perform(post("/signup/access").session(session).param("username", "otra"))
                .andExpect(redirectedUrl("/login?link-expired"));
        mvc.perform(get("/signup").session(session)).andExpect(status().isOk());
        assertThat(formInSession().getFullName()).isNull();
        assertThat(formInSession().isExternalIdentity()).isFalse();
    }

    @Test
    @DisplayName("El alta con Auth0 no dura más del límite aunque la identidad pendiente se renueve en cada paso")
    void theAuth0WizardCannotOutliveItsLimit() throws Exception {
        SignUpForm started = new SignUpForm();
        started.startExternalIdentity("auth0|ada", "ada@example.com",
                NOW.minus(SignUpController.AUTH0_WIZARD_LIMIT).minusSeconds(1));
        session.setAttribute(SignUpController.FORM, started);

        mvc.perform(post("/signup").session(session).param("fullName", "Ada Lovelace").param("taxId", "12345678Z")
                        .param("email", "ada@example.com"))
                .andExpect(redirectedUrl("/login?link-expired"));
    }

    @Test
    @DisplayName("Con una identidad pendiente, el alta clásica sigue usando la contraseña y nunca la identidad")
    void withAPendingIdentityTheClassicWizardStillUsesThePassword() throws Exception {
        when(registration.register(any(SignUpCommand.class), anyString(), anyString())).thenReturn(opened());
        mvc.perform(get("/signup").session(session)).andExpect(status().isOk());
        step1();
        mvc.perform(post("/signup/access").session(session).param("username", "ada")
                        .param("password", "clave-secreta").param("passwordRepetition", "clave-secreta"))
                .andExpect(redirectedUrl("/signup/account"));

        mvc.perform(step3()).andExpect(status().is3xxRedirection());

        verify(registration).register(any(SignUpCommand.class), any(), anyString());
        verify(registration, never()).registerWithExternalIdentity(any(), any(), any());
    }

    @Test
    @DisplayName("Una contraseña enviada en el paso 2 del modo Auth0 no se guarda ni se usa")
    void aPasswordSentInAuth0ModeIsNeitherKeptNorUsed() throws Exception {
        when(registration.registerWithExternalIdentity(any(), any(), any())).thenReturn(opened());
        mvc.perform(get("/signup").param("auth0", "").session(session)).andExpect(status().isOk());
        step1();
        mvc.perform(post("/signup/access").session(session).param("username", "ada")
                        .param("password", "colada").param("passwordRepetition", "colada"))
                .andExpect(redirectedUrl("/signup/account"));

        assertThat(formInSession().getPassword()).isNull();
        assertThat(formInSession().getPasswordRepetition()).isNull();
        mvc.perform(step3()).andExpect(redirectedUrl("/oauth2/authorization/auth0"));
        verify(registration, never()).register(any(), any(), any());
    }

    @Test
    @DisplayName("Empezar el alta con Auth0 descarta la contraseña de un alta clásica a medias")
    void startingWithAuth0DropsTheClassicPassword() throws Exception {
        mvc.perform(get("/signup").session(session)).andExpect(status().isOk());
        step1();
        mvc.perform(post("/signup/access").session(session).param("username", "ada")
                        .param("password", "clave-secreta").param("passwordRepetition", "clave-secreta"))
                .andExpect(redirectedUrl("/signup/account"));

        mvc.perform(get("/signup").param("auth0", "").session(session)).andExpect(status().isOk());

        assertThat(formInSession().isExternalIdentity()).isTrue();
        assertThat(formInSession().getPassword()).isNull();
        assertThat(formInSession().isAccessDone()).isFalse();
    }
}
