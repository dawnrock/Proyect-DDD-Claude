package com.dawnrock.ddd.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dawnrock.ddd.adapter.in.LinkedOidcUserService;
import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;

/** Página de acceso con Auth0 y la autenticación local desactivada ({@code app.auth.local=false}, #196). */
@WebMvcTest(LoginController.class)
@Import({WebSecurityConfig.class, SecuritySupport.class, TestUsers.class, Auth0TestRegistration.class})
@TestPropertySource(properties = "app.auth.local=false")
class LoginAuth0OnlyTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LinkedOidcUserService linkedOidcUserService;

    @Test
    @DisplayName("Sin autenticación local, el formulario enseña solo el botón de Auth0")
    void withoutLocalAuthenticationTheFormShowsOnlyTheAuth0Button() throws Exception {
        mvc.perform(get("/login").param("form", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/oauth2/authorization/auth0\"")))
                .andExpect(content().string(not(containsString("name=\"username\""))))
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    @Test
    @DisplayName("Sin autenticación local, la puerta «Ya soy cliente» va directa a Auth0 sin hx-boost")
    void withoutLocalAuthenticationTheDoorGoesStraightToAuth0() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "hx-boost=\"false\" href=\"/oauth2/authorization/auth0\"")))
                .andExpect(content().string(not(containsString("href=\"/login?form\""))));
    }
}
