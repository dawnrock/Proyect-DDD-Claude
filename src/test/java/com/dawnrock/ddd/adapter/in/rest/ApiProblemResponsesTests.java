package com.dawnrock.ddd.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las respuestas de error que escribe la propia cadena de seguridad de la API (401, 403 y 503, fuera de Spring MVC) se
 * serializan como JSON, no se montan con texto (#205): cualquier detalle sigue siendo un JSON válido.
 */
class ApiProblemResponsesTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("Un detalle con comillas y barras invertidas sigue dando un ProblemDetail JSON válido con el mismo texto")
    void aDetailWithQuotesAndBackslashesStaysValidJson() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String detail = "El token \"abc\\def\" no vale";

        ApiSecurityConfig.writeProblem(response, HttpStatus.UNAUTHORIZED, "No autenticado", detail);

        JsonNode body = JSON.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body.get("type").asString()).isEqualTo("about:blank");
        assertThat(body.get("title").asString()).isEqualTo("No autenticado");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("detail").asString()).isEqualTo(detail);
    }
}
