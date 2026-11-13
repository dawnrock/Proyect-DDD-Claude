package com.pronoide.ddd.presentation.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.presentation.CustomerRegistration;
import com.pronoide.ddd.presentation.SecuritySupport;

/** Alta de cliente en tres pasos (#95): datos personales, acceso y cuenta. */
@WebMvcTest(SignUpController.class)
@Import({WebFormat.class, WebSecurityConfig.class, SecuritySupport.class})
class SignUpControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerRegistration registration;

    @MockitoBean
    private CheckingAccountService service;

    /** POST con token CSRF: la cadena web lo exige para las rutas públicas del alta. */
    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url).with(csrf());
    }

    private static MockHttpServletRequestBuilder validStep1(MockHttpServletRequestBuilder request) {
        return request.param("fullName", "Margaret Hamilton").param("taxId", "12345678Z")
                .param("email", "margaret@example.com");
    }

    @Nested
    @DisplayName("Paso 1: datos personales")
    class Paso1 {

        @Test
        @DisplayName("La página es pública, lleva el token CSRF y los tres campos")
        void la_pagina_es_publica_lleva_el_token_csrf_y_los_tres_campos() throws Exception {
            mvc.perform(get("/signup"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/data"))
                    .andExpect(content().string(containsString("name=\"_csrf\"")))
                    .andExpect(content().string(containsString("name=\"fullName\"")))
                    .andExpect(content().string(containsString("name=\"taxId\"")))
                    .andExpect(content().string(containsString("name=\"email\"")))
                    .andExpect(content().string(containsString("Paso 1 de 3")));
        }

        @Test
        @DisplayName("Sin enviar nada, los errores de campo se quedan en el paso 1")
        void sin_enviar_nada_los_errores_de_campo_se_quedan_en_el_paso_1() throws Exception {
            mvc.perform(post("/signup"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/data"))
                    .andExpect(content().string(containsString("El nombre y apellidos son obligatorios")))
                    .andExpect(content().string(containsString("field-error")));
        }

        @Test
        @DisplayName("Un documento o un correo mal formados se quedan en el paso 1 con su mensaje")
        void un_documento_o_un_correo_mal_formados_se_quedan_en_el_paso_1() throws Exception {
            mvc.perform(post("/signup").param("fullName", "Margaret").param("taxId", "abc")
                            .param("email", "sin-arroba"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/data"))
                    .andExpect(content().string(containsString(
                            com.pronoide.ddd.application.OpenAccountCommandDef.TAX_ID_MESSAGE)))
                    .andExpect(content().string(containsString("El correo electrónico no es válido")));
        }

        @Test
        @DisplayName("Con datos válidos redirige al paso 2")
        void con_datos_validos_redirige_al_paso_2() throws Exception {
            mvc.perform(validStep1(post("/signup")))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/signup/access"));
        }

        @Test
        @DisplayName("Un POST sin token CSRF se rechaza con 403")
        void un_post_sin_token_csrf_se_rechaza_con_403() throws Exception {
            mvc.perform(MockMvcRequestBuilders.post("/signup").param("fullName", "Margaret"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Volver al paso 1 conserva los datos ya escritos")
        void volver_al_paso_1_conserva_los_datos_ya_escritos() throws Exception {
            MockHttpSession session = new MockHttpSession();
            mvc.perform(validStep1(post("/signup")).session(session))
                    .andExpect(redirectedUrl("/signup/access"));

            mvc.perform(get("/signup").session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("value=\"Margaret Hamilton\"")))
                    .andExpect(content().string(containsString("value=\"12345678Z\"")))
                    .andExpect(content().string(containsString("value=\"margaret@example.com\"")))
                    .andExpect(content().string(not(containsString("field-error"))));
        }
    }
}
