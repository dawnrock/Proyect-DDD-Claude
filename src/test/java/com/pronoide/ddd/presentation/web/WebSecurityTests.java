package com.pronoide.ddd.presentation.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.presentation.SecuritySupport;

/** Reglas de la cadena de seguridad web (ADR 0008, #50): acceso, CSRF y rol de administrador. */
@WebMvcTest({AccountWebController.class, LoginController.class, AccessDeniedController.class})
@Import({WebFormat.class, WebSecurityConfig.class, SecuritySupport.class})
class WebSecurityTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CheckingAccountService service;

    @Test
    @DisplayName("Un anónimo que pide el listado es redirigido al login")
    void un_anonimo_que_pide_el_listado_es_redirigido_al_login() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("La página de login es pública, lleva el token CSRF y el botón Entrar")
    void la_pagina_de_login_es_publica_lleva_el_token_csrf_y_el_boton_entrar() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("Entrar")))
                .andExpect(content().string(not(containsString("Usuario o contraseña incorrectos"))));
    }

    @Test
    @DisplayName("Con ?error el login muestra el mensaje de credenciales incorrectas")
    void con_error_el_login_muestra_el_mensaje_de_credenciales_incorrectas() throws Exception {
        mvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Usuario o contraseña incorrectos")));
    }

    @Test
    @DisplayName("Con ?logout el login muestra que se ha cerrado la sesión")
    void con_logout_el_login_muestra_que_se_ha_cerrado_la_sesion() throws Exception {
        mvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Has cerrado la sesión")));
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Cerrar la sesión (POST /logout con CSRF) redirige a /login?logout")
    void cerrar_la_sesion_redirige_a_login_logout() throws Exception {
        mvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Un usuario que abre el formulario de alta recibe 403 y se le reenvía a la página 403")
    void un_usuario_que_abre_el_formulario_de_alta_recibe_403_y_se_le_reenvia_a_la_pagina_403() throws Exception {
        mvc.perform(get("/accounts/new"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/error/403"));
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("La página 403 se pinta con su estado y su plantilla")
    void la_pagina_403_se_pinta_con_su_estado_y_su_plantilla() throws Exception {
        mvc.perform(get("/error/403"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"))
                .andExpect(content().string(containsString("Acceso denegado")));
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Un usuario que envía el alta recibe 403")
    void un_usuario_que_envia_el_alta_recibe_403() throws Exception {
        mvc.perform(post("/accounts/new").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Un administrador abre el formulario de alta")
    void un_administrador_abre_el_formulario_de_alta() throws Exception {
        mvc.perform(get("/accounts/new"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Un POST sin token CSRF se rechaza con 403")
    void un_post_sin_token_csrf_se_rechaza_con_403() throws Exception {
        mvc.perform(post("/accounts/new"))
                .andExpect(status().isForbidden());
    }
}
