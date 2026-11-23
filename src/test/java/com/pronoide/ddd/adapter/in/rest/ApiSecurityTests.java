package com.pronoide.ddd.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pronoide.ddd.adapter.in.SecuritySupport;
import com.pronoide.ddd.adapter.in.TestUsers;
import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.Caller;
import com.pronoide.ddd.application.CheckingAccountCommands;
import com.pronoide.ddd.application.CheckingAccountQueries;

/**
 * La cadena de seguridad de la API con tokens reales (#122, ADR 0015): solo acepta {@code Authorization: Bearer} con
 * un JWT válido, convierte sus roles y su usuario en el {@link Caller} y responde 401/403 como {@code ProblemDetail}.
 */
@WebMvcTest(controllers = AccountController.class, properties = {
        "app.security.users[0].username=admin",
        "app.security.users[0].password-hash=" + TokenControllerTests.DEMO_HASH,
        "app.security.users[0].role=ADMIN",
        "app.security.users[1].username=ada",
        "app.security.users[1].password-hash=" + TokenControllerTests.DEMO_HASH,
        "app.security.users[1].role=USER",
        "app.jwt.secret=" + ApiSecurityTests.SECRET})
@Import({ApiSecurityConfig.class, SecuritySupport.class, TestUsers.class, AccessTokenIssuer.class})
class ApiSecurityTests {

    static final String SECRET = "un-secreto-de-pruebas-de-al-menos-32-bytes";
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String OPEN_BODY = """
            {"holderTaxId": "12345678Z", "holderName": "Ada Lovelace", "holderEmail": "ada@example.com",
             "initialDeposit": 100.00, "ownerUsername": "ada"}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccessTokenIssuer issuer;

    @Autowired
    private JwtEncoder encoder;

    @MockitoBean
    private CheckingAccountCommands commands;

    @MockitoBean
    private CheckingAccountQueries queries;

    private String bearer(String username) {
        return "Bearer " + issuer.issue(username, "demo", null).accessToken();
    }

    private static String token(JwtEncoder encoder, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("ddd-lab").subject("admin").issuedAt(issuedAt)
                .expiresAt(expiresAt).claim("roles", List.of("ADMIN")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static final String ADMIN_CLAIMS = "{\"iss\":\"ddd-lab\",\"sub\":\"admin\",\"roles\":[\"ADMIN\"],\"exp\":%d}";

    private static long future() {
        return Instant.now().plus(15, ChronoUnit.MINUTES).getEpochSecond();
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static AccountSummary summary() {
        return AccountSummary.builder().id(ID).accountNumber("ES9121000418450200051332").holderName("Ada Lovelace")
                .segment("RETAIL").balance(new BigDecimal("100.00")).currency("EUR").build();
    }

    @Nested
    @DisplayName("Con un token válido")
    class ValidToken {

        @Test
        @DisplayName("Un usuario con su token consulta la API como él mismo, sin rol de administrador")
        void un_usuario_con_su_token_consulta_la_api_como_el_mismo() throws Exception {
            given(queries.listAccounts(any(Caller.class))).willReturn(List.of());

            mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, bearer("ada")))
                    .andExpect(status().isOk());

            ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
            verify(queries).listAccounts(caller.capture());
            assertThat(caller.getValue().username()).isEqualTo("ada");
            assertThat(caller.getValue().admin()).isFalse();
        }

        @Test
        @DisplayName("El token del administrador le deja abrir cuentas como administrador")
        void el_token_del_administrador_le_deja_abrir_cuentas() throws Exception {
            given(commands.openAccount(any(Caller.class), any())).willReturn(summary());

            mvc.perform(post("/api/accounts").header(HttpHeaders.AUTHORIZATION, bearer("admin"))
                            .contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))
                    .andExpect(status().isCreated());

            verify(commands).openAccount(eq(Caller.builder().username("admin").admin(true).build()), any());
        }

        @Test
        @DisplayName("Un token sin el claim roles no da ningún rol: consulta como usuario y no puede abrir cuentas")
        void un_token_sin_roles_no_da_ningun_rol() throws Exception {
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder().issuer("ddd-lab").subject("ada").issuedAt(now)
                    .expiresAt(now.plus(15, ChronoUnit.MINUTES)).build();
            String noRoles = "Bearer " + encoder.encode(
                    JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            given(queries.listAccounts(any(Caller.class))).willReturn(List.of());

            mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, noRoles)).andExpect(status().isOk());
            mvc.perform(post("/api/accounts").header(HttpHeaders.AUTHORIZATION, noRoles)
                            .contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))
                    .andExpect(status().isForbidden());
            verify(queries).listAccounts(Caller.builder().username("ada").admin(false).build());
            verifyNoInteractions(commands);
        }

        @Test
        @DisplayName("Un usuario sin rol de administrador que abre una cuenta recibe 403")
        void un_usuario_que_abre_una_cuenta_recibe_403() throws Exception {
            mvc.perform(post("/api/accounts").header(HttpHeaders.AUTHORIZATION, bearer("ada"))
                            .contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Acceso denegado"));
            verifyNoInteractions(commands);
        }
    }

    @Nested
    @DisplayName("Sin un token válido responde 401 ProblemDetail")
    class InvalidToken {

        @Test
        @DisplayName("Sin cabecera Authorization responde 401 y pide un token Bearer")
        void sin_cabecera_responde_401_y_pide_un_token_bearer() throws Exception {
            mvc.perform(get("/api/accounts"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.title").value("No autenticado"));
            verifyNoInteractions(queries);
        }

        @Test
        @DisplayName("Con credenciales HTTP Basic responde 401: la API ya no las acepta")
        void con_credenciales_http_basic_responde_401() throws Exception {
            String basic = Base64.getEncoder().encodeToString("admin:demo".getBytes(StandardCharsets.UTF_8));

            mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, "Basic " + basic))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("No autenticado"));
            verifyNoInteractions(queries);
        }

        @Test
        @DisplayName("Con algo que no es un JWT responde 401 con invalid_token")
        void con_algo_que_no_es_un_jwt_responde_401() throws Exception {
            mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                            startsWith("Bearer error=\"invalid_token\"")))
                    .andExpect(jsonPath("$.title").value("No autenticado"))
                    .andExpect(jsonPath("$.detail").value("El token no es válido o ha caducado"));
        }

        @Test
        @DisplayName("Con un token caducado responde 401")
        void con_un_token_caducado_responde_401() throws Exception {
            Instant past = Instant.now().minus(1, ChronoUnit.HOURS);

            mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(encoder, past, past.plus(15, ChronoUnit.MINUTES))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("El token no es válido o ha caducado"));
            verifyNoInteractions(queries);
        }

        @Test
        @DisplayName("Un token sin firma (alg none) responde 401 aunque diga ser del administrador")
        void un_token_sin_firma_responde_401() throws Exception {
            String unsigned = base64Url("{\"alg\":\"none\"}") + "." + base64Url(ADMIN_CLAIMS.formatted(future())) + ".";

            mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + unsigned))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(queries);
        }

        @Test
        @DisplayName("Un token firmado con el mismo secreto pero con HS512 responde 401: solo se acepta HS256")
        void un_token_firmado_con_otro_algoritmo_responde_401() throws Exception {
            String signingInput = base64Url("{\"alg\":\"HS512\"}") + "." + base64Url(ADMIN_CLAIMS.formatted(future()));
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));

            mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + signingInput + "." + signature))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(queries);
        }

        @Test
        @DisplayName("Con un token firmado con otra clave responde 401")
        void con_un_token_firmado_con_otra_clave_responde_401() throws Exception {
            SecretKey otherKey = JwtSupport.signingKey("otro-secreto-distinto-de-al-menos-32-bytes");
            Instant now = Instant.now();
            String forged = token(new JwtSupport().jwtEncoder(otherKey), now, now.plus(15, ChronoUnit.MINUTES));

            mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(queries);
        }
    }
}
