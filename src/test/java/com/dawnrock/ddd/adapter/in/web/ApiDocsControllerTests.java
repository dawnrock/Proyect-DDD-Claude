package com.dawnrock.ddd.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;

/** {@code /api-docs} (#111): la documentación interactiva de la API, pública. */
@WebMvcTest(ApiDocsController.class)
@Import({WebSecurityConfig.class, SecuritySupport.class, TestUsers.class})
class ApiDocsControllerTests {

    @Autowired
    private MockMvc mvc;

    @Test
    @WithAnonymousUser
    @DisplayName("/api-docs lleva sin credenciales a la página de Swagger UI")
    void apiDocsLeadsToTheSwaggerUiPageWithoutCredentials() throws Exception {
        mvc.perform(get("/api-docs"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/api-docs/index.html"));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("Sin Auth0 configurado, Swagger UI no ofrece iniciar sesión con Auth0 y no hay documento de descubrimiento")
    void withoutAuth0SwaggerUiOffersNoAuth0SignIn() throws Exception {
        mvc.perform(get("/api-docs/oauth-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.clientId").doesNotExist());
        mvc.perform(get("/api-docs/openid-configuration"))
                .andExpect(status().isNotFound());
    }
}
