package com.pronoide.ddd.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.pronoide.ddd.application.CheckingAccountCommands;
import com.pronoide.ddd.application.CheckingAccountQueries;
import com.pronoide.ddd.adapter.in.SecuritySupport;

/** Reglas de la cadena de seguridad web (ADR 0008, #50): acceso, CSRF y rol de administrador. */
@WebMvcTest({AccountWebController.class, LoginController.class, AccessDeniedController.class})
@Import({WebFormat.class, WebSecurityConfig.class, SecuritySupport.class})
// Una usuaria real (contraseña «demo», el hash del perfil demo) para los inicios de sesión con el formulario (#130)
@TestPropertySource(properties = {
        "app.security.users[0].username=ada",
        "app.security.users[0].password-hash=$2a$10$XbpS91y/CFr.Zvx6Xxi7RuLFFUMRM9adecz2G7FO4YClrsKKCdKWW",
        "app.security.users[0].role=USER"})
class WebSecurityTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CheckingAccountCommands commands;

    @MockitoBean
    private CheckingAccountQueries queries;

    @Test
    @DisplayName("Una petición de htmx sin sesión recibe 401 con HX-Redirect al login, no la página de login (#125)")
    void una_peticion_de_htmx_sin_sesion_recibe_hx_redirect_al_login() throws Exception {
        mvc.perform(post("/accounts/" + UUID.randomUUID() + "/deposits").with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("HX-Redirect", "/login"));
    }

    @Test
    @DisplayName("Un cierre de sesión con htmx sin token CSRF, como tras caducar la sesión, lleva al login con HX-Redirect (#126)")
    void un_cierre_de_sesion_con_htmx_sin_token_csrf_lleva_al_login() throws Exception {
        mvc.perform(post("/logout").header("HX-Request", "true").header("HX-Boosted", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("HX-Redirect", "/login"));
    }

    @Test
    @DisplayName("La misma petición sin htmx sigue redirigiendo al login (#125)")
    void la_misma_peticion_sin_htmx_sigue_redirigiendo_al_login() throws Exception {
        mvc.perform(post("/accounts/" + UUID.randomUUID() + "/deposits").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(header().doesNotExist("HX-Redirect"));
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Una petición de htmx sin token CSRF, como tras caducar la sesión, recibe HX-Redirect al login (#125)")
    void una_peticion_de_htmx_sin_token_csrf_recibe_hx_redirect_al_login() throws Exception {
        mvc.perform(post("/accounts/" + UUID.randomUUID() + "/deposits").header("HX-Request", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("HX-Redirect", "/login"));

        verifyNoInteractions(commands, queries);
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Una petición de htmx sin el rol necesario recibe la página 403, sin HX-Redirect (#125)")
    void una_peticion_de_htmx_sin_el_rol_necesario_recibe_la_pagina_403() throws Exception {
        mvc.perform(get("/accounts/new").header("HX-Request", "true"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/error/403"))
                .andExpect(header().doesNotExist("HX-Redirect"));
    }

    @Test
    @WithMockUser(username = "ada", roles = "USER")
    @DisplayName("Una petición de htmx con un token CSRF inválido sigue recibiendo 403 (#125)")
    void una_peticion_de_htmx_con_un_token_csrf_invalido_recibe_403() throws Exception {
        mvc.perform(post("/accounts/" + UUID.randomUUID() + "/deposits").with(csrf().useInvalidToken())
                        .header("HX-Request", "true"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("HX-Redirect"));

        verifyNoInteractions(commands, queries);
    }

    @Test
    @DisplayName("Un anónimo que pide el listado es redirigido al login")
    void un_anonimo_que_pide_el_listado_es_redirigido_al_login() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/webjars/bootstrap/css/bootstrap.min.css",
            "/webjars/bootstrap/js/bootstrap.bundle.min.js",
            "/webjars/htmx.org/dist/htmx.min.js",
            "/webjars/alpinejs/dist/cdn.min.js",
            "/webjars/lucide-static/sprite.svg",
            "/vendor/motion/motion.js"})
    @DisplayName("Las librerías del frontend se sirven sin sesión y sin versión en la ruta (#124)")
    void las_librerias_del_frontend_se_sirven_sin_sesion_y_sin_version_en_la_ruta(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Las librerías del frontend se guardan en caché un año y las páginas no se guardan (#124)")
    void las_librerias_del_frontend_se_guardan_en_cache_y_las_paginas_no() throws Exception {
        mvc.perform(get("/webjars/bootstrap/css/bootstrap.min.css"))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public"));
        mvc.perform(get("/login"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        String css = mvc.perform(get("/login")).andReturn().getResponse().getContentAsString()
                .replaceAll("(?s).*href=\"(/css/app-[0-9a-f]{32}\\.css)\".*", "$1");
        mvc.perform(get(css))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public"));
    }

    @Test
    @DisplayName("Tras 3 inicios de sesión fallidos, ni la contraseña correcta entra: lleva a /login?blocked (#130)")
    void tras_3_inicios_de_sesion_fallidos_ni_la_contrasena_correcta_entra() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(formLogin("ada", "mala", "198.51.100.7"))
                    .andExpect(redirectedUrl("/login?error"));
        }

        mvc.perform(formLogin("ada", "demo", "198.51.100.7"))
                .andExpect(redirectedUrl("/login?blocked"));
        // Desde otra IP, la misma usuaria sigue pudiendo entrar
        mvc.perform(formLogin("ada", "demo", "198.51.100.8"))
                .andExpect(redirectedUrl("/"));
    }

    private static MockHttpServletRequestBuilder formLogin(String username, String password, String ip) {
        return post("/login").with(csrf()).param("username", username).param("password", password)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    @Test
    @DisplayName("La página de login es pública, lleva el token CSRF y el botón Entrar")
    void la_pagina_de_login_es_publica_lleva_el_token_csrf_y_el_boton_entrar() throws Exception {
        mvc.perform(get("/login").param("form", ""))
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
