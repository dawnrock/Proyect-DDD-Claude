package com.dawnrock.ddd.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dawnrock.ddd.adapter.in.ExternalIdentities;
import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.CheckingAccountQueries;
import com.dawnrock.ddd.application.MoneyMovementCommands;
import com.dawnrock.ddd.support.TestAuth0Tenant;

/**
 * La API con dos emisores (#197, ADR 0024): sus propios tokens y los de acceso de Auth0, cuyo {@code sub} se traduce
 * al usuario local con sus roles. Tenant imaginario con clave RSA local: sin red.
 */
@WebMvcTest(controllers = AccountController.class, properties = {
        "app.security.users[0].username=admin",
        "app.security.users[0].password-hash=" + TokenControllerTests.DEMO_HASH,
        "app.security.users[0].role=ADMIN",
        "app.security.users[1].username=ada",
        "app.security.users[1].password-hash=" + TokenControllerTests.DEMO_HASH,
        "app.security.users[1].role=USER",
        "app.jwt.secret=" + ApiSecurityTests.SECRET,
        "app.auth.auth0.domain=" + TestAuth0Tenant.DOMAIN,
        "app.auth.auth0.client-id=test-client",
        "app.auth.auth0.client-secret=test-secret",
        "app.auth.auth0.audience=" + TestAuth0Tenant.AUDIENCE})
@Import({ApiSecurityConfig.class, SecuritySupport.class, TestUsers.class, AccessTokenIssuer.class,
        ApiAuth0TokensTests.Tenant.class})
class ApiAuth0TokensTests {

    private static final String OPEN_BODY = """
            {"holderTaxId": "12345678Z", "holderName": "Ada Lovelace", "holderEmail": "ada@example.com",
             "initialDeposit": 100.00, "ownerUsername": "ada"}""";

    /** El tenant de prueba en lugar del de verdad: mismo nombre de bean, validación idéntica, clave local. */
    @TestConfiguration
    static class Tenant {

        @Bean(Auth0ApiTokensConfig.DECODER)
        JwtDecoder auth0JwtDecoder() {
            return TestAuth0Tenant.decoder();
        }

        @Bean
        Auth0AccessTokens auth0AccessTokens(ExternalIdentities identities, UserDetailsService users) {
            return new Auth0AccessTokens(identities, users);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccessTokenIssuer issuer;

    @MockitoBean
    private ExternalIdentities identities;

    @MockitoBean
    private AccountOpeningCommands commands;

    @MockitoBean
    private MoneyMovementCommands movements;

    @MockitoBean
    private CheckingAccountQueries queries;

    @BeforeEach
    void links() {
        given(identities.usernameOf(ExternalIdentities.AUTH0, "auth0|ada")).willReturn(Optional.of("ada"));
        given(identities.usernameOf(ExternalIdentities.AUTH0, "auth0|admin")).willReturn(Optional.of("admin"));
        given(identities.usernameOf(ExternalIdentities.AUTH0, "auth0|nadie")).willReturn(Optional.empty());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    @DisplayName("Con el token de Auth0 de una identidad vinculada, la API responde como su usuario local")
    void anAuth0TokenOfALinkedIdentityActsAsTheLocalUser() throws Exception {
        given(queries.listAccounts(any(Caller.class))).willReturn(List.of());

        mvc.perform(get("/api/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TestAuth0Tenant.accessToken("auth0|ada"))))
                .andExpect(status().isOk());

        ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
        verify(queries).listAccounts(caller.capture());
        assertThat(caller.getValue().username()).isEqualTo("ada");
        assertThat(caller.getValue().admin()).isFalse();
    }

    @Test
    @DisplayName("Los roles salen de la base de datos: el administrador abre cuentas y un cliente recibe 403")
    void rolesComeFromTheDatabase() throws Exception {
        given(commands.openAccount(any(Caller.class), any())).willReturn(AccountSummary.builder()
                .id(UUID.randomUUID()).accountNumber("ES9121000418450200051332").holderName("Ada Lovelace")
                .segment("RETAIL").balance(new BigDecimal("100.00")).currency("EUR").build());

        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY)
                        .header(HttpHeaders.AUTHORIZATION, bearer(TestAuth0Tenant.accessToken("auth0|admin"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY)
                        .header(HttpHeaders.AUTHORIZATION, bearer(TestAuth0Tenant.accessToken("auth0|ada"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Una identidad de Auth0 sin vincular recibe 401 y el detalle dice cómo vincularla")
    void anUnlinkedIdentityGets401SayingHowToLinkIt() throws Exception {
        mvc.perform(get("/api/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TestAuth0Tenant.accessToken("auth0|nadie"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer error=\"invalid_token\"")))
                .andExpect(jsonPath("$.detail").value(Auth0AccessTokens.UNLINKED_DETAIL));
        verifyNoInteractions(queries);
    }

    @Test
    @DisplayName("Un token de Auth0 para otra API, de otro emisor, caducado o firmado con otra clave recibe 401")
    void otherAudienceIssuerExpiredOrForeignKeyGet401() throws Exception {
        Instant later = Instant.now().plusSeconds(600);
        List<String> rejected = List.of(
                TestAuth0Tenant.accessToken("auth0|ada", TestAuth0Tenant.ISSUER, "https://otra-api.example", later),
                TestAuth0Tenant.accessToken("auth0|ada", "https://otro.auth0.com/", TestAuth0Tenant.AUDIENCE, later),
                TestAuth0Tenant.accessToken("auth0|ada", TestAuth0Tenant.ISSUER, TestAuth0Tenant.AUDIENCE,
                        Instant.now().minusSeconds(3600)),
                TestAuth0Tenant.signedWithAnotherKey("auth0|ada"));

        for (String token : rejected) {
            mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                            startsWith("Bearer error=\"invalid_token\"")))
                    .andExpect(jsonPath("$.detail").value(ApiSecurityConfig.INVALID_TOKEN_DETAIL));
        }
        verifyNoInteractions(queries);
    }

    @Test
    @DisplayName("Los tokens propios de la API siguen valiendo junto a los de Auth0")
    void ownTokensStillWorkNextToAuth0Ones() throws Exception {
        given(queries.listAccounts(any(Caller.class))).willReturn(List.of());

        mvc.perform(get("/api/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(issuer.issue("ada", "demo", null).accessToken())))
                .andExpect(status().isOk());
    }
}
