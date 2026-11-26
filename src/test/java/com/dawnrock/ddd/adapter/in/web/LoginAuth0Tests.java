package com.dawnrock.ddd.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dawnrock.ddd.adapter.in.LinkedOidcUserService;
import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;

/** Página de acceso con Auth0 configurado y la autenticación local activa (#196, ADR 0024). */
@WebMvcTest(LoginController.class)
@Import({WebSecurityConfig.class, SecuritySupport.class, TestUsers.class, Auth0TestRegistration.class})
class LoginAuth0Tests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LinkedOidcUserService linkedOidcUserService;

    @Test
    @DisplayName("Con Auth0 configurado, el formulario trae debajo el botón «Entrar con Auth0» sin hx-boost")
    void withAuth0TheFormShowsTheAuth0ButtonWithoutBoost() throws Exception {
        mvc.perform(get("/login").param("form", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*<a class=\"btn-slant btn-slant-surface\" hx-boost=\"false\" "
                                + "href=\"/oauth2/authorization/auth0\">Entrar con Auth0</a>.*")));
    }

    @Test
    @DisplayName("Con Auth0 y autenticación local, la puerta «Ya soy cliente» sigue yendo al formulario")
    void withAuth0AndLocalTheDoorStillGoesToTheForm() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/login?form\"")))
                .andExpect(content().string(not(containsString("oauth2/authorization/auth0"))));
    }

    @Test
    @DisplayName("Con ?oauth-error, /login muestra el formulario con el aviso de Auth0 como alerta")
    void withOauthErrorLoginShowsTheFormWithTheAuth0Notice() throws Exception {
        mvc.perform(get("/login").param("oauth-error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*<div class=\"alert alert-danger[^\"]*\" role=\"alert\"><svg[^>]*>.*</svg>"
                                + "No se pudo iniciar sesión con Auth0\\. Inténtalo de nuevo\\.</div>.*")))
                .andExpect(content().string(not(containsString("Usuario o contraseña incorrectos"))));
    }

    @Test
    @DisplayName("Con ?link-expired, /login muestra el formulario con el aviso de volver a entrar con Auth0")
    void withLinkExpiredLoginShowsTheFormWithTheNotice() throws Exception {
        mvc.perform(get("/login").param("link-expired", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*role=\"alert\"><svg[^>]*>.*</svg>Vuelve a entrar con Auth0 para vincular tu usuario\\.</div>.*")));
    }
}
