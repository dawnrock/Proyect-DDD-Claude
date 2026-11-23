package com.pronoide.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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

import com.pronoide.ddd.adapter.in.SecuritySupport;
import com.pronoide.ddd.adapter.in.TestUsers;

/** Página de acceso: las dos puertas y el formulario (#95). */
@WebMvcTest(LoginController.class)
@Import({WebSecurityConfig.class, SecuritySupport.class, TestUsers.class})
class LoginControllerTests {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Con ?blocked, el login muestra el formulario y el aviso de bloqueo con los minutos configurados (#130)")
    void con_blocked_el_login_muestra_el_aviso_de_bloqueo() throws Exception {
        mvc.perform(get("/login").param("blocked", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString(
                        "Demasiados intentos fallidos. Vuelve a intentarlo dentro de 30 minutos.")))
                .andExpect(content().string(not(containsString("Usuario o contraseña incorrectos"))));
    }

    @Test
    @DisplayName("El login navega con htmx y la configuración de htmx no guarda copias de las páginas (#126)")
    void el_login_navega_con_htmx_y_no_guarda_copias_de_las_paginas() throws Exception {
        mvc.perform(get("/login").param("form", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hx-boost=\"true\" hx-swap=\"innerHTML transition:true\"")))
                .andExpect(content().string(containsString(
                        "<meta name=\"htmx-config\" content='{\"historyCacheSize\":0,\"refreshOnHistoryMiss\":true}'>")));
    }

    @Test
    @DisplayName("Sin parámetros, /login muestra las dos puertas y ningún campo de formulario")
    void sin_parametros_login_muestra_las_dos_puertas_y_ningun_campo() throws Exception {
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
    @DisplayName("Con ?form, /login muestra el formulario de acceso con el enlace de vuelta a las puertas")
    void con_form_login_muestra_el_formulario_con_enlace_de_vuelta() throws Exception {
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
    void con_error_login_muestra_el_formulario_con_el_mensaje() throws Exception {
        mvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Usuario o contraseña incorrectos")))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("href=\"/login\"")));
    }

    @Test
    @DisplayName("Con ?logout, /login muestra el aviso de sesión cerrada junto a las puertas")
    void con_logout_login_muestra_el_aviso_junto_a_las_puertas() throws Exception {
        mvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Has cerrado la sesión")))
                .andExpect(content().string(containsString("Quiero ser cliente")))
                .andExpect(content().string(not(containsString("name=\"username\""))));
    }

    @Test
    @DisplayName("El usuario del login no se escribe con mayúscula inicial ni con corrector, y se autocompleta (#146)")
    void el_usuario_del_login_no_se_escribe_con_mayuscula_ni_corrector() throws Exception {
        String html = mvc.perform(get("/login").param("form", "")).andReturn().getResponse().getContentAsString();

        assertThat(FieldTags.tag(html, "username"))
                .contains("autocapitalize=\"none\"")
                .contains("spellcheck=\"false\"")
                .contains("autocomplete=\"username\"")
                .contains(" required");
    }

    @Test
    @DisplayName("La contraseña del login tiene el botón «Mostrar contraseña», oculto sin JavaScript (#146)")
    void la_contrasena_del_login_tiene_el_boton_mostrar_contrasena() throws Exception {
        String html = mvc.perform(get("/login").param("form", "")).andReturn().getResponse().getContentAsString();

        FieldTags.assertPasswordToggle(html, "password");
    }
}
