package com.pronoide.ddd.presentation.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.pronoide.ddd.presentation.rest.support.FailingTestController;

@WebMvcTest(FailingTestController.class)
class RestExceptionHandlerTests {

    @Autowired
    private MockMvc mvc;

    @Nested
    @DisplayName("Reglas de negocio")
    class BusinessRules {

        @Test
        @DisplayName("Una regla de negocio incumplida se traduce en un 422 con el mensaje del dominio")
        void una_regla_de_negocio_incumplida_se_traduce_en_un_422_con_el_mensaje_del_dominio() throws Exception {
            mvc.perform(get("/test/errors/business-rule"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"))
                    .andExpect(jsonPath("$.detail").value(
                            "Regla de prueba incumplida"));
        }
    }

    @Nested
    @DisplayName("Recursos inexistentes")
    class NotFound {

        @Test
        @DisplayName("Un recurso inexistente se traduce en un 404")
        void un_recurso_inexistente_se_traduce_en_un_404() throws Exception {
            mvc.perform(get("/test/errors/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Recurso inexistente"))
                    .andExpect(jsonPath("$.detail").value("No existe el recurso de prueba 42"));
        }
    }

    @Nested
    @DisplayName("Peticiones no válidas")
    class BadRequest {

        @Test
        @DisplayName("Un argumento que el dominio rechaza se traduce en un 400 y no en un 500")
        void un_argumento_que_el_dominio_rechaza_se_traduce_en_un_400_y_no_en_un_500() throws Exception {
            mvc.perform(get("/test/errors/invalid-argument"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Petición no válida"))
                    .andExpect(jsonPath("$.detail").value("El argumento de prueba no es válido"));
        }

        @Test
        @DisplayName("Un cuerpo que no pasa la validación se traduce en un 400 con los campos que fallan")
        void un_cuerpo_que_no_pasa_la_validacion_se_traduce_en_un_400_con_los_campos_que_fallan() throws Exception {
            mvc.perform(post("/test/errors/validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"holderName\": \"\", \"amount\": 0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Petición no válida"))
                    .andExpect(jsonPath("$.errors.holderName").exists())
                    .andExpect(jsonPath("$.errors.amount").exists());
        }

        @Test
        @DisplayName("Un cuerpo JSON mal formado se traduce en un 400 con el título del contrato")
        void un_cuerpo_json_mal_formado_se_traduce_en_un_400_con_el_titulo_del_contrato() throws Exception {
            mvc.perform(post("/test/errors/validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"holderName\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Petición no válida"));
        }
    }
}
