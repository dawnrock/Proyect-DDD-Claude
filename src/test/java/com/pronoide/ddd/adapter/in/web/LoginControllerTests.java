package com.pronoide.ddd.adapter.in.web;

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

/** Página de acceso: las dos puertas y el formulario (#95). */
@WebMvcTest(LoginController.class)
@Import({WebSecurityConfig.class, SecuritySupport.class})
class LoginControllerTests {

    @Autowired
    private MockMvc mvc;

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
}
