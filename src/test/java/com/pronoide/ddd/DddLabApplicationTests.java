package com.pronoide.ddd;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DddLabApplicationTests {

	@LocalServerPort
	private int port;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void contextLoads() {
	}

	@Test
	@DisplayName("Una página inexistente muestra la página 404 propia de la web")
	void una_pagina_inexistente_muestra_la_pagina_404_propia_de_la_web() throws Exception {
		HttpResponse<String> response = get("/no-existe", "text/html");

		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("text/html"));
		assertThat(response.body()).contains("Página no encontrada").contains("Cuentas corrientes");
	}

	@Test
	@DisplayName("Una ruta inexistente de la API sigue respondiendo en JSON")
	void una_ruta_inexistente_de_la_api_sigue_respondiendo_en_json() throws Exception {
		HttpResponse<String> response = get("/api/no-existe", "application/json");

		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).contains("json"));
	}

	private HttpResponse<String> get(String path, String accept) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Accept", accept)
				.GET()
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
