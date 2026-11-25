package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;

/** Página de acceso: las dos puertas y el formulario (#95). */
@WebMvcTest(LoginController.class)
@Import({WebSecurityConfig.class, SecuritySupport.class, TestUsers.class})
class LoginControllerTests {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Con ?blocked, el login muestra el formulario y el aviso de bloqueo con los minutos configurados (#130)")
    void withBlockedTheLoginShowsTheLockoutNotice() throws Exception {
        mvc.perform(get("/login").param("blocked", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString(
                        "Demasiados intentos fallidos. Vuelve a intentarlo dentro de 30 minutos.")))
                .andExpect(content().string(not(containsString("Usuario o contraseña incorrectos"))));
    }

    @Test
    @DisplayName("El login navega con htmx y la configuración de htmx no guarda copias de las páginas (#126)")
    void theLoginNavigatesWithHtmxAndDoesNotCachePages() throws Exception {
        mvc.perform(get("/login").param("form", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hx-boost=\"true\" hx-swap=\"innerHTML transition:true\"")))
                .andExpect(content().string(containsString(
                        "<meta name=\"htmx-config\" content='{\"historyCacheSize\":0,\"refreshOnHistoryMiss\":true}'>")));
    }

    @Test
    @DisplayName("Sin parámetros, /login muestra las dos puertas y ningún campo de formulario")
    void withoutParametersLoginShowsBothDoorsAndNoField() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("Ya soy cliente")))
                .andExpect(content().string(containsString("Quiero ser cliente")))
                .andExpect(content().string(containsString("href=\"/login?form\"")))
                .andExpect(content().string(containsString("href=\"/signup\"")))
                .andExpect(content().string(not(containsString("name=\"username\""))))
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    @Test
    @DisplayName("La bienvenida del login muestra el logo apilado de monA Bank sobre su título; el formulario, no (#178)")
    void theLoginWelcomeShowsTheStackedLogoAboveItsTitle() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern(
                        "(?s).*<img [^>]*src=\"/brand/monabank-stacked[^\"]*\\.svg\"[^>]*alt=\"\".*<h1[^>]*>Bienvenido a monA Bank</h1>.*")));
        mvc.perform(get("/login").param("form", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("monabank-stacked"))));
    }

    @Test
    @DisplayName("Las puertas del login traen la intro de monA Bank oculta, con su lienzo y su script; el formulario, no (#178)")
    void theLoginDoorsBringTheHiddenIntroWithItsCanvasAndScript() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern(
                        "(?s).*<div class=\"splash\" id=\"splash\" role=\"img\" aria-label=\"monA Bank\" hidden>\\s*"
                                + "<canvas aria-hidden=\"true\"></canvas>\\s*</div>.*")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*<script type=\"importmap\">\\{\"imports\":\\{\"brand-geometry\":\"\\\\?/js\\\\?/brand-geometry-[0-9a-f]{32}\\.js\"\\}\\}</script>.*")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*<script type=\"module\" src=\"/js/intro-[0-9a-f]{32}\\.js\"></script>.*")));
        mvc.perform(get("/login").param("form", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"splash\""))))
                .andExpect(content().string(not(containsString("intro"))));
    }

    @Test
    @DisplayName("El script de la intro se sirve como JavaScript, que es lo que exige un módulo (#178)")
    void theIntroScriptIsServedAsJavaScript() throws Exception {
        mvc.perform(get("/js/intro.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));
        mvc.perform(get("/js/brand-geometry.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));
    }

    @Test
    @DisplayName("Con ?form, /login muestra el formulario de acceso con el enlace de vuelta a las puertas")
    void withFormLoginShowsTheFormWithABackLink() throws Exception {
        mvc.perform(get("/login").param("form", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Entrar")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(not(containsString("Quiero ser cliente"))))
                .andExpect(content().string(not(containsString("Usuario o contraseña incorrectos"))));
    }

    @Test
    @DisplayName("Con ?error, /login muestra el formulario con el mensaje de credenciales incorrectas")
    void withErrorLoginShowsTheFormWithTheMessage() throws Exception {
        mvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Usuario o contraseña incorrectos")))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("href=\"/login\"")));
    }

    @Test
    @DisplayName("Con ?logout, /login muestra el aviso de sesión cerrada junto a las puertas")
    void withLogoutLoginShowsTheNoticeNextToTheDoors() throws Exception {
        mvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Has cerrado la sesión")))
                .andExpect(content().string(containsString("Quiero ser cliente")))
                .andExpect(content().string(not(containsString("name=\"username\""))));
    }

    @Test
    @DisplayName("El usuario del login no se escribe con mayúscula inicial ni con corrector, y se autocompleta (#146)")
    void theLoginUsernameHasNoAutoCapitalisationOrSpellCheck() throws Exception {
        String html = mvc.perform(get("/login").param("form", "")).andReturn().getResponse().getContentAsString();

        assertThat(FieldTags.tag(html, "username"))
                .contains("autocapitalize=\"none\"")
                .contains("spellcheck=\"false\"")
                .contains("autocomplete=\"username\"")
                .contains(" required");
    }

    @Test
    @DisplayName("La contraseña del login tiene el botón «Mostrar contraseña», oculto sin JavaScript (#146)")
    void theLoginPasswordHasTheShowPasswordButton() throws Exception {
        String html = mvc.perform(get("/login").param("form", "")).andReturn().getResponse().getContentAsString();

        FieldTags.assertPasswordToggle(html, "password");
    }
}
