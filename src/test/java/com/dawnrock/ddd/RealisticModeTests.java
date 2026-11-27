package com.dawnrock.ddd;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.dawnrock.ddd.adapter.in.ExternalIdentities;
import com.dawnrock.ddd.adapter.in.rest.Auth0ApiTokensConfig;
import com.dawnrock.ddd.support.TestAuth0Tenant;

/**
 * El modo realista de punta a punta ({@code app.auth.local=false}, #199, ADR 0024): todos los beans reales y la H2 de
 * los tests, con el tenant imaginario de Auth0 y su clave local (sin red). Solo se entra con Auth0: no hay tokens
 * propios, ni inicio de sesión con contraseña, ni altas con contraseña.
 */
@SpringBootTest(properties = {
        "app.auth.local=false",
        "app.auth.auth0.domain=" + TestAuth0Tenant.DOMAIN,
        "app.auth.auth0.client-id=test-client",
        "app.auth.auth0.client-secret=test-secret",
        "app.auth.auth0.audience=" + TestAuth0Tenant.AUDIENCE,
        "app.auth.auth0.api-docs-client-id=test-spa",
        "app.security.users[0].username=ada",
        "app.security.users[0].password-hash=" + DddLabApplicationTests.DEMO_HASH,
        "app.security.users[0].role=USER"})
class RealisticModeTests {

    private static final String ADA = "auth0|ada-realistic";

    @TestBean(name = Auth0ApiTokensConfig.DECODER, methodName = "tenantDecoder")
    private JwtDecoder auth0Decoder;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ExternalIdentities identities;

    @Autowired
    private JwtEncoder ownEncoder;

    private MockMvc mvc;

    static JwtDecoder tenantDecoder() {
        return TestAuth0Tenant.decoder();
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // La H2 de los tests es la misma para todos los contextos: el vínculo puede venir de otra prueba de esta clase
        if (identities.usernameOf(ExternalIdentities.AUTH0, ADA).isEmpty()) {
            identities.link(ExternalIdentities.AUTH0, ADA, "ada");
        }
    }

    @Test
    @DisplayName("No se pueden pedir tokens propios ni darse de alta con contraseña por la API: las dos rutas responden 404")
    void ownTokensAndPasswordSignUpDoNotExist() throws Exception {
        mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"ada\", \"password\": \"" + DddLabApplicationTests.PASSWORD + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Margaret Hamilton\", \"taxId\": \"00000000T\", "
                                + "\"email\": \"margaret@example.com\", \"username\": \"margaret\", "
                                + "\"password\": \"demo-1234\", \"initialDeposit\": 100}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("La web no tiene formulario de contraseña y enviar usuario y contraseña a /login no inicia sesión")
    void theWebHasNoPasswordLogin() throws Exception {
        mvc.perform(get("/login").param("form", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/oauth2/authorization/auth0\"")))
                .andExpect(content().string(not(containsString("name=\"password\""))));
        mvc.perform(post("/login").with(csrf()).param("username", "ada")
                        .param("password", DddLabApplicationTests.PASSWORD))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("El alta clásica lleva a Auth0")
    void theClassicSignUpGoesToAuth0() throws Exception {
        mvc.perform(get("/signup")).andExpect(redirectedUrl("/oauth2/authorization/auth0"));
    }

    @Test
    @DisplayName("Un token de Auth0 de una identidad vinculada se acepta y uno propio bien firmado se rechaza")
    void anAuth0TokenIsAcceptedAndAnOwnTokenIsRejected() throws Exception {
        mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestAuth0Tenant.accessToken(ADA)))
                .andExpect(status().isOk());

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("ddd-lab").subject("ada").issuedAt(now)
                .expiresAt(now.plusSeconds(900)).claim("roles", List.of("USER")).build();
        String own = ownEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + own))
                .andExpect(status().isUnauthorized());
    }
}
