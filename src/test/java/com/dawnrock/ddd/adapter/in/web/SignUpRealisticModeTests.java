package com.dawnrock.ddd.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dawnrock.ddd.adapter.in.CustomerRegistration;
import com.dawnrock.ddd.adapter.in.LinkedOidcUserService;
import com.dawnrock.ddd.adapter.in.PendingIdentity;
import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.SignUpLimit;
import com.dawnrock.ddd.adapter.in.TestUsers;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.SignUpCommand;

/**
 * El asistente de alta sin autenticación local ({@code app.auth.local=false}, #199): el alta clásica, con contraseña,
 * no se ofrece y cualquiera de sus pasos lleva a Auth0; el alta por Auth0 (#198) sigue igual.
 */
@WebMvcTest(SignUpController.class)
@Import({WebFormat.class, WebSecurityConfig.class, SecuritySupport.class, TestUsers.class, Auth0TestRegistration.class})
@TestPropertySource(properties = "app.auth.local=false")
class SignUpRealisticModeTests {

    private static final String AUTH0 = "/oauth2/authorization/auth0";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerRegistration registration;

    @MockitoBean
    private SignUpLimit signUpLimit;

    @MockitoBean
    private LinkedOidcUserService linkedOidcUserService;

    @Test
    @DisplayName("GET /signup sin ?auth0 lleva a Auth0: no hay alta con contraseña")
    void theClassicWizardGoesToAuth0() throws Exception {
        mvc.perform(get("/signup")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(AUTH0));
    }

    @Test
    @DisplayName("Ningún paso del alta clásica avanza ni da de alta: todos llevan a Auth0")
    void noStepOfTheClassicWizardMovesOn() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/signup").with(csrf()).session(session).param("fullName", "Margaret Hamilton")
                .param("taxId", "00000000T").param("email", "margaret@example.com")).andExpect(redirectedUrl(AUTH0));
        mvc.perform(get("/signup/access").session(session)).andExpect(redirectedUrl(AUTH0));
        mvc.perform(post("/signup/access").with(csrf()).session(session).param("username", "margaret")
                .param("password", "demo-1234").param("passwordRepetition", "demo-1234"))
                .andExpect(redirectedUrl(AUTH0));
        mvc.perform(get("/signup/account").session(session)).andExpect(redirectedUrl(AUTH0));
        mvc.perform(post("/signup/account").with(csrf()).session(session).param("initialDeposit", "100.00")
                .param("currency", "EUR").param("conditionsAccepted", "true")).andExpect(redirectedUrl(AUTH0));

        verifyNoInteractions(registration);
    }

    @Test
    @DisplayName("El alta por Auth0 sigue funcionando de principio a fin y vuelve a Auth0")
    void theAuth0SignUpStillWorks() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingIdentity.SESSION_ATTRIBUTE,
                new PendingIdentity("auth0", "auth0|abc123", "ada@example.com", Instant.now()));
        when(registration.registerWithExternalIdentity(any(SignUpCommand.class), any(PendingIdentity.class),
                anyString())).thenReturn(new AccountSummary(UUID.randomUUID(), "ES2199990001220000000001",
                        "Ada Lovelace", "RETAIL", new BigDecimal("100.00"), "EUR"));

        mvc.perform(get("/signup").param("auth0", "").session(session)).andExpect(status().isOk());
        mvc.perform(post("/signup").with(csrf()).session(session).param("fullName", "Ada Lovelace")
                .param("taxId", "12345678Z").param("email", "ada@example.com"))
                .andExpect(redirectedUrl("/signup/access"));
        mvc.perform(post("/signup/access").with(csrf()).session(session).param("username", "ada"))
                .andExpect(redirectedUrl("/signup/account"));
        mvc.perform(post("/signup/account").with(csrf()).session(session).param("initialDeposit", "100.00")
                .param("currency", "EUR").param("conditionsAccepted", "true"))
                .andExpect(redirectedUrl(AUTH0));

        verify(registration).registerWithExternalIdentity(any(SignUpCommand.class), any(PendingIdentity.class),
                anyString());
    }
}
