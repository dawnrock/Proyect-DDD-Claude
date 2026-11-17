package com.pronoide.ddd.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import com.pronoide.ddd.adapter.in.SecuritySupport;

/** {@code /api-docs} (#111): la documentación interactiva de la API, pública. */
@WebMvcTest(ApiDocsController.class)
@Import({WebSecurityConfig.class, SecuritySupport.class})
class ApiDocsControllerTests {

    @Autowired
    private MockMvc mvc;

    @Test
    @WithAnonymousUser
    @DisplayName("/api-docs lleva sin credenciales a la página de Swagger UI")
    void api_docs_lleva_sin_credenciales_a_la_pagina_de_swagger_ui() throws Exception {
        mvc.perform(get("/api-docs"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/api-docs/index.html"));
    }
}
