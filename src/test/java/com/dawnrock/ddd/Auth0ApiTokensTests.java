package com.dawnrock.ddd;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.dawnrock.ddd.adapter.in.ExternalIdentities;
import com.dawnrock.ddd.adapter.in.rest.Auth0AccessTokens;
import com.dawnrock.ddd.adapter.in.rest.Auth0ApiTokensConfig;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.OpenAccountCommand;
import com.dawnrock.ddd.support.TestAuth0Tenant;

/**
 * La API con tokens de acceso de Auth0 de punta a punta (#197, ADR 0024): todos los beans reales y la H2 de los tests,
 * salvo el decodificador de Auth0, que se cambia por uno del tenant imaginario con clave local (sin red). La identidad
 * vinculada en {@code external_identity} actúa como su usuario local, con sus roles y solo sobre sus cuentas.
 */
@SpringBootTest(properties = {
        "app.auth.auth0.domain=" + TestAuth0Tenant.DOMAIN,
        "app.auth.auth0.client-id=test-client",
        "app.auth.auth0.client-secret=test-secret",
        "app.auth.auth0.audience=" + TestAuth0Tenant.AUDIENCE,
        "app.security.users[0].username=admin",
        "app.security.users[0].password-hash=" + DddLabApplicationTests.DEMO_HASH,
        "app.security.users[0].role=ADMIN",
        "app.security.users[1].username=ada",
        "app.security.users[1].password-hash=" + DddLabApplicationTests.DEMO_HASH,
        "app.security.users[1].role=USER",
        "app.security.users[2].username=grace",
        "app.security.users[2].password-hash=" + DddLabApplicationTests.DEMO_HASH,
        "app.security.users[2].role=USER"})
class Auth0ApiTokensTests {

    private static final String ADA = "auth0|ada-api";

    private static final String ADMIN = "auth0|admin-api";

    @TestBean(name = Auth0ApiTokensConfig.DECODER, methodName = "tenantDecoder")
    private JwtDecoder auth0Decoder;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ExternalIdentities identities;

    @Autowired
    private AccountOpeningCommands commands;

    private MockMvc mvc;

    private UUID adasAccount;

    private UUID gracesAccount;

    static JwtDecoder tenantDecoder() {
        return TestAuth0Tenant.decoder();
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        identities.link(ExternalIdentities.AUTH0, ADA, "ada");
        identities.link(ExternalIdentities.AUTH0, ADMIN, "admin");
        Caller admin = Caller.administrator("admin");
        adasAccount = commands.openAccount(admin, account("Ada Lovelace", "12345678Z", "ada")).id();
        gracesAccount = commands.openAccount(admin, account("Grace Hopper", "87654321X", "grace")).id();
    }

    @Test
    @DisplayName("Con un token de Auth0 la clienta vinculada ve sus cuentas y la de otro cliente no existe para ella")
    void aLinkedCustomerSeesOnlyTheirAccounts() throws Exception {
        String bearer = "Bearer " + TestAuth0Tenant.accessToken(ADA);

        mvc.perform(get("/api/accounts/" + adasAccount).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderName").value("Ada Lovelace"));
        mvc.perform(get("/api/accounts/" + gracesAccount).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(openBody("11111111H", "ada")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Con un token de Auth0 el administrador vinculado abre cuentas")
    void aLinkedAdministratorOpensAccounts() throws Exception {
        mvc.perform(post("/api/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestAuth0Tenant.accessToken(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(openBody("22222222J", "nuevo.cliente")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Una identidad sin vincular recibe 401 con cómo vincularla, y un token para otra API, 401")
    void anUnlinkedIdentityOrAnotherAudienceGets401() throws Exception {
        mvc.perform(get("/api/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestAuth0Tenant.accessToken("auth0|nadie")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(Auth0AccessTokens.UNLINKED_DETAIL));
        String otherApi = TestAuth0Tenant.accessToken(ADA, TestAuth0Tenant.ISSUER, "https://otra-api.example",
                Instant.now().plusSeconds(600));
        mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherApi))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Los tokens propios siguen valiendo con Auth0 configurado")
    void ownTokensStillWork() throws Exception {
        String body = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"ada\", \"password\": \"" + DddLabApplicationTests.PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = body.replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mvc.perform(get("/api/accounts/" + adasAccount).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private static OpenAccountCommand account(String holderName, String taxId, String owner) {
        return OpenAccountCommand.of(holderName, taxId, owner + "@example.com", new BigDecimal("100"), owner);
    }

    private static String openBody(String taxId, String owner) {
        return """
                {"holderTaxId": "%s", "holderName": "Nueva Cuenta", "holderEmail": "%s@example.com",
                 "initialDeposit": 100.00, "ownerUsername": "%s"}""".formatted(taxId, owner, owner);
    }
}
