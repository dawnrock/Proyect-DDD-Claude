package com.dawnrock.ddd.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

/**
 * Swagger UI con Auth0 (#197, ADR 0024): la página pide su configuración y el contrato apunta a un documento de
 * descubrimiento propio, construido con el dominio del tenant, sin llamar a Auth0 y sin ningún secreto.
 */
@WebMvcTest(controllers = ApiDocsController.class, properties = {
        "app.auth.auth0.domain=https://tenant.example.auth0.com/",
        "app.auth.auth0.client-id=web-client",
        "app.auth.auth0.client-secret=secreto-web",
        "app.auth.auth0.audience=https://api.monabank.example",
        "app.auth.auth0.api-docs-client-id=spa-client"})
@Import({WebSecurityConfig.class, SecuritySupport.class, TestUsers.class})
class ApiDocsAuth0Tests {

    private static final String TENANT = "https://tenant.example.auth0.com";

    @Autowired
    private MockMvc mvc;

    @Test
    @WithAnonymousUser
    @DisplayName("La configuración de Swagger UI da el cliente público y la audiencia, nunca el secreto de la web")
    void theSwaggerUiConfigGivesThePublicClientAndAudience() throws Exception {
        mvc.perform(get("/api-docs/oauth-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.clientId").value("spa-client"))
                .andExpect(jsonPath("$.audience").value("https://api.monabank.example"))
                .andExpect(content().string(not(containsString("secreto-web"))))
                .andExpect(content().string(not(containsString("web-client"))));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("El documento de descubrimiento lleva los endpoints del tenant y solo admite PKCE con S256")
    void theDiscoveryDocumentHasTheTenantEndpointsAndPkce() throws Exception {
        mvc.perform(get("/api-docs/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value(TENANT + "/"))
                .andExpect(jsonPath("$.authorization_endpoint").value(TENANT + "/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value(TENANT + "/oauth/token"))
                .andExpect(jsonPath("$.jwks_uri").value(TENANT + "/.well-known/jwks.json"))
                .andExpect(jsonPath("$.response_types_supported[0]").value("code"))
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"))
                .andExpect(content().string(not(containsString("secreto-web"))));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("Con Auth0, Swagger UI puede pedir el token al tenant y la web deja que los formularios acaben en él (#206)")
    void withAuth0ThePoliciesAllowTheTenant() throws Exception {
        mvc.perform(get("/api-docs/index.html"))
                .andExpect(header().string("Content-Security-Policy",
                        ContentSecurityPolicies.apiDocs(Optional.of(TENANT))));
        mvc.perform(get("/login"))
                .andExpect(header().string("Content-Security-Policy", containsString("form-action 'self' " + TENANT)));
    }
}
