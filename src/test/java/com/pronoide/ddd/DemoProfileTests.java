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
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
class DemoProfileTests {

	@LocalServerPort
	private int port;

	@Test
	@DisplayName("Con el perfil demo, la API lista las tres cuentas de demostración")
	void con_el_perfil_demo_la_api_lista_las_tres_cuentas_de_demostracion() throws Exception {
		HttpResponse<String> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/accounts"))
						.header("Accept", "application/json").GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body().split("\"accountNumber\"")).hasSize(4);
		assertThat(response.body()).contains("Ada Lovelace", "Grace Hopper", "Alan Turing");
	}
}
