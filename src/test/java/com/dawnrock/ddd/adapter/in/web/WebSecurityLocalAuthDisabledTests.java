package com.dawnrock.ddd.adapter.in.web;

import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;

/**
 * Con {@code app.auth.local=false} la cadena web no tiene {@code formLogin()} (#196, ADR 0024): un usuario con
 * contraseña válida no inicia sesión. Con el valor por defecto sí lo hace ({@code WebSecurityTests}).
 */
@WebMvcTest(LoginController.class)
@Import({WebSecurityConfig.class, SecuritySupport.class, TestUsers.class})
@TestPropertySource(properties = {
        "app.auth.local=false",
        "app.security.users[0].username=ada",
        "app.security.users[0].password-hash=$2a$10$XbpS91y/CFr.Zvx6Xxi7RuLFFUMRM9adecz2G7FO4YClrsKKCdKWW",
        "app.security.users[0].role=USER"})
class WebSecurityLocalAuthDisabledTests {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Sin autenticación local, POST /login con credenciales válidas no autentica")
    void withoutLocalAuthenticationAFormLoginDoesNotAuthenticate() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username", "ada").param("password", "demo"))
                .andExpect(unauthenticated());
    }
}
