package com.pronoide.ddd.presentation.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                            "El depósito inicial de 10.00 EUR no alcanza el mínimo exigido de 50.00 EUR"));
        }
    }
}
