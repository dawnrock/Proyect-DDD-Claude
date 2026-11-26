package com.dawnrock.ddd.adapter.in.rest;

import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dawnrock.ddd.adapter.in.ExternalIdentities;
import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.CheckingAccountQueries;
import com.dawnrock.ddd.application.MoneyMovementCommands;
import com.dawnrock.ddd.support.TestAuth0Tenant;

/**
 * Si no se pueden leer las claves públicas de Auth0 ({@code jwks.json} inalcanzable, #197 revisión), la API no puede
 * decir si el token es bueno: responde 503 con {@code ProblemDetail}, no un 401 que pediría un token que sí llegó.
 */
@WebMvcTest(controllers = AccountController.class, properties = {
        "app.jwt.secret=" + ApiSecurityTests.SECRET,
        "app.auth.auth0.domain=" + TestAuth0Tenant.DOMAIN,
        "app.auth.auth0.client-id=test-client",
        "app.auth.auth0.client-secret=test-secret",
        "app.auth.auth0.audience=" + TestAuth0Tenant.AUDIENCE})
@Import({ApiSecurityConfig.class, SecuritySupport.class, TestUsers.class, AccessTokenIssuer.class,
        ApiAuth0UnavailableTests.UnreachableTenant.class})
class ApiAuth0UnavailableTests {

    /** Un tenant cuyas claves no se pueden descargar: Nimbus lanza {@link JwtException} al decodificar. */
    @TestConfiguration
    static class UnreachableTenant {

        @Bean(Auth0ApiTokensConfig.DECODER)
        JwtDecoder auth0JwtDecoder() {
            return token -> {
                throw new JwtException("Couldn't retrieve remote JWK set: connection refused");
            };
        }

        @Bean
        Auth0AccessTokens auth0AccessTokens(ExternalIdentities identities, UserDetailsService users) {
            return new Auth0AccessTokens(identities, users);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ExternalIdentities identities;

    @MockitoBean
    private AccountOpeningCommands commands;

    @MockitoBean
    private MoneyMovementCommands movements;

    @MockitoBean
    private CheckingAccountQueries queries;

    @Test
    @DisplayName("Con Auth0 inalcanzable, un token de Auth0 recibe 503 y no se le pide otro token")
    void withAuth0UnreachableAnAuth0TokenGets503() throws Exception {
        mvc.perform(get("/api/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestAuth0Tenant.accessToken("auth0|ada")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value(ApiSecurityConfig.UNAVAILABLE_TITLE))
                .andExpect(jsonPath("$.detail").value(not(ApiSecurityConfig.MISSING_TOKEN_DETAIL)));
        verifyNoInteractions(queries);
    }
}
