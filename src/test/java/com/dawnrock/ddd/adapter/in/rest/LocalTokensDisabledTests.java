package com.dawnrock.ddd.adapter.in.rest;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.CheckingAccountQueries;
import com.dawnrock.ddd.application.MoneyMovementCommands;

/**
 * Sin autenticación local ({@code app.auth.local=false}, #197; la issue #199 lo hará en {@code prod}) la API no emite
 * ni acepta sus propios tokens: {@code POST /api/auth/token} no existe y un token propio, aunque esté bien firmado,
 * recibe 401.
 */
@WebMvcTest(controllers = {AccountController.class, TokenController.class}, properties = {
        "app.auth.local=false",
        "app.security.users[0].username=ada",
        "app.security.users[0].password-hash=" + TokenControllerTests.DEMO_HASH,
        "app.security.users[0].role=USER",
        "app.jwt.secret=" + ApiSecurityTests.SECRET})
@Import({ApiSecurityConfig.class, SecuritySupport.class, TestUsers.class, AccessTokenIssuer.class})
class LocalTokensDisabledTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder encoder;

    @MockitoBean
    private AccountOpeningCommands commands;

    @MockitoBean
    private MoneyMovementCommands movements;

    @MockitoBean
    private CheckingAccountQueries queries;

    @Test
    @DisplayName("Pedir un token propio responde 404: el endpoint no existe")
    void askingForAnOwnTokenResponds404() throws Exception {
        mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"ada\", \"password\": \"demo\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un token propio bien firmado responde 401: su emisor ya no es de confianza")
    void aWellSignedOwnTokenResponds401() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("ddd-lab").subject("ada").issuedAt(now)
                .expiresAt(now.plusSeconds(900)).claim("roles", List.of("USER")).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(queries);
    }
}
