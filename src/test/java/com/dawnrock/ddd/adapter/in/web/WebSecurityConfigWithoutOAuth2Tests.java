package com.dawnrock.ddd.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;

/** Sin registro de Auth0 la cadena web no tiene {@code oauth2Login()}: las rutas de Auth0 no existen (#196). */
@WebMvcTest(LoginController.class)
@Import({WebSecurityConfig.class, SecuritySupport.class, TestUsers.class})
class WebSecurityConfigWithoutOAuth2Tests {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Sin Auth0 configurado, /oauth2/authorization/auth0 no existe: pide iniciar sesión")
    void withoutAuth0TheAuthorizationRouteDoesNotExist() throws Exception {
        mvc.perform(get("/oauth2/authorization/auth0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
