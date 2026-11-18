package com.pronoide.ddd.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.pronoide.ddd.adapter.in.SecuritySupport;

/** {@code POST /api/auth/token} (#122, ADR 0015): credenciales a cambio de un token JWT, sin autenticarse antes. */
@WithAnonymousUser
@WebMvcTest(controllers = TokenController.class, properties = {
        "app.security.users[0].username=admin",
        "app.security.users[0].password-hash=" + TokenControllerTests.DEMO_HASH,
        "app.security.users[0].role=ADMIN",
        "app.security.users[1].username=ada",
        "app.security.users[1].password-hash=" + TokenControllerTests.DEMO_HASH,
        "app.security.users[1].role=USER",
        "app.jwt.secret=un-secreto-de-pruebas-de-al-menos-32-bytes"})
@Import({ApiSecurityConfig.class, SecuritySupport.class, JwtSupport.class, AccessTokenIssuer.class})
class TokenControllerTests {

    /** Hash BCrypt de "demo". */
    static final String DEMO_HASH = "$2a$10$XbpS91y/CFr.Zvx6Xxi7RuLFFUMRM9adecz2G7FO4YClrsKKCdKWW";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtDecoder decoder;

    private String tokenFor(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    @Test
    @DisplayName("Con credenciales válidas responde 200 con un token Bearer que dura 15 minutos")
    void con_credenciales_validas_responde_200_con_un_token_bearer() throws Exception {
        mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"ada\", \"password\": \"demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("El token lleva el usuario como sub, sus roles, el emisor y la caducidad")
    void el_token_lleva_el_usuario_sus_roles_el_emisor_y_la_caducidad() throws Exception {
        Jwt admin = decoder.decode(tokenFor("admin", "demo"));
        Jwt ada = decoder.decode(tokenFor("ada", "demo"));

        assertThat(admin.getSubject()).isEqualTo("admin");
        assertThat(admin.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(ada.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(admin.getClaimAsString("iss")).isEqualTo("ddd-lab");
        assertThat(Duration.between(admin.getIssuedAt(), admin.getExpiresAt())).hasMinutes(15);
    }

    @ParameterizedTest
    @CsvSource({"ada, incorrecta", "nadie, demo"})
    @DisplayName("Con una contraseña errónea o un usuario inexistente responde 401 con el mismo mensaje")
    void con_credenciales_erroneas_responde_401_con_el_mismo_mensaje(String username, String password)
            throws Exception {
        mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    @DisplayName("Sin usuario o sin contraseña responde 400 con el error de cada campo")
    void sin_usuario_o_sin_contrasena_responde_400() throws Exception {
        mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON).content("{\"username\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"))
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }
}
