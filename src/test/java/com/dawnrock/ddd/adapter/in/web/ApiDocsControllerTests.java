package com.dawnrock.ddd.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import com.dawnrock.ddd.adapter.in.ContentSecurityPolicies;
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
    @DisplayName("La página de Swagger UI carga init.js, sin script en línea, que pide su configuración de Auth0 y solo inicia sesión con PKCE y la audiencia (#206)")
    void theSwaggerUiPageAsksForItsAuth0ConfigAndSignsInWithPkce() throws Exception {
        mvc.perform(get("/api-docs/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<script src=\"init.js\"></script>")))
                .andExpect(content().string(not(matchesPattern("(?s).*<script>.*"))));
        mvc.perform(get("/api-docs/init.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"))
                .andExpect(content().string(containsString("fetch('/api-docs/oauth-config')")))
                .andExpect(content().string(containsString(
                        "oauth2RedirectUrl: window.location.origin + '/api-docs/oauth2-redirect.html'")))
                .andExpect(content().string(containsString("usePkceWithAuthorizationCodeGrant: true")))
                .andExpect(content().string(containsString("additionalQueryStringParams: {audience: config.audience}")));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("La página de vuelta de OAuth de Swagger UI se sirve desde nuestro origen")
    void theOAuthRedirectPageIsServedFromOurOrigin() throws Exception {
        mvc.perform(get("/api-docs/oauth2-redirect.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<script src=\"oauth2-redirect.js\"></script>")));
        mvc.perform(get("/api-docs/oauth2-redirect.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("swaggerUIRedirectOauth2")));
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

    @Test
    @WithAnonymousUser
    @DisplayName("Swagger UI tiene su propia CSP, que permite jsDelivr; sin Auth0, no se conecta con nadie más (#206)")
    void swaggerUiHasItsOwnPolicy() throws Exception {
        mvc.perform(get("/api-docs/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", ContentSecurityPolicies.apiDocs(Optional.empty())));
    }
}
