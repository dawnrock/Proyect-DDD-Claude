package com.dawnrock.ddd.adapter.in.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;
import com.dawnrock.ddd.adapter.in.rest.support.FailingTestController;

@WebMvcTest(FailingTestController.class)
@Import({ApiSecurityConfig.class, SecuritySupport.class, TestUsers.class})
class RestExceptionHandlerTests {

    @Autowired
    private MockMvc mvc;

    @Nested
    @DisplayName("Reglas de negocio")
    class BusinessRules {

        @Test
        @DisplayName("Una regla de negocio incumplida se traduce en un 422 con el mensaje del dominio")
        void aBrokenBusinessRuleBecomesA422WithTheDomainMessage() throws Exception {
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
        void aMissingResourceBecomesA404() throws Exception {
            mvc.perform(get("/test/errors/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Recurso inexistente"))
                    .andExpect(jsonPath("$.detail").value("No existe el recurso de prueba 42"));
        }
    }

    @Nested
    @DisplayName("Operaciones no permitidas")
    class Forbidden {

        @Test
        @DisplayName("Una operación reservada a administradores se traduce en un 403 Acceso denegado")
        void anAdministratorOnlyOperationBecomesA403() throws Exception {
            mvc.perform(get("/test/errors/forbidden"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Acceso denegado"))
                    .andExpect(jsonPath("$.detail").value("Solo un administrador puede abrir cuentas"));
        }
    }

    @Nested
    @DisplayName("Peticiones no válidas")
    class BadRequest {

        @Test
        @DisplayName("Un argumento que el dominio rechaza se traduce en un 400 y no en un 500")
        void anArgumentTheDomainRejectsBecomesA400NotA500() throws Exception {
            mvc.perform(get("/test/errors/invalid-argument"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Petición no válida"))
                    .andExpect(jsonPath("$.detail").value("El argumento de prueba no es válido"));
        }

        @Test
        @DisplayName("Un cuerpo que no pasa la validación se traduce en un 400 con los campos que fallan")
        void aBodyFailingValidationBecomesA400WithTheFailingFields() throws Exception {
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
        void aMalformedJsonBodyBecomesA400WithTheContractTitle() throws Exception {
            mvc.perform(post("/test/errors/validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"holderName\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Petición no válida"));
        }
    }

    @Nested
    @DisplayName("Tipo de contenido no admitido")
    class UnsupportedMediaType {

        @Test
        @DisplayName("Un cuerpo que no es JSON se traduce en un 415 con el título y el detalle en español")
        void aNonJsonBodyBecomesA415InSpanish() throws Exception {
            mvc.perform(post("/test/errors/validation")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("hola"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(header().exists("Accept"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Tipo de contenido no admitido"))
                    .andExpect(jsonPath("$.detail").value(
                            "El tipo de contenido text/plain no está admitido: envía application/json"));
        }
    }
}
