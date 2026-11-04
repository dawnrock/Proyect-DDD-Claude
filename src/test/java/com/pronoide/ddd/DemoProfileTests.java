package com.pronoide.ddd;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
class DemoProfileTests {

	/** Contraseña de los usuarios del perfil demo. */
	private static final String PASSWORD = "demo";

	@LocalServerPort
	private int port;

	private HttpResponse<String> listAccountsAs(String username) throws Exception {
		String credentials = Base64.getEncoder().encodeToString((username + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/accounts"))
						.header("Accept", "application/json")
						.header("Authorization", "Basic " + credentials)
						.GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	@Test
	@DisplayName("Con el perfil demo, la API lista al administrador las tres cuentas de demostración")
	void con_el_perfil_demo_la_api_lista_las_tres_cuentas_de_demostracion() throws Exception {
		HttpResponse<String> response = listAccountsAs("admin");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body().split("\"accountNumber\"")).hasSize(4);
		assertThat(response.body()).contains("Ada Lovelace", "Grace Hopper", "Alan Turing");
	}

	@Test
	@DisplayName("Con el perfil demo, la API lista a ada solo su cuenta")
	void con_el_perfil_demo_la_api_lista_a_ada_solo_su_cuenta() throws Exception {
		HttpResponse<String> response = listAccountsAs("ada");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("Ada Lovelace").doesNotContain("Grace Hopper", "Alan Turing");
	}

	@Test
	@DisplayName("Con el perfil demo, los saldos reflejan los ingresos, retiradas y la transferencia de la demo")
	void con_el_perfil_demo_los_saldos_reflejan_las_operaciones_de_la_demo() throws Exception {
		String body = listAccountsAs("admin").body();

		assertThat(body)
				.containsPattern(accountWithBalance("Ada Lovelace", "1500.00"))
				.containsPattern(accountWithBalance("Grace Hopper", "4700.00"))
				.containsPattern(accountWithBalance("Alan Turing", "200.00"));
	}

	/** Un objeto JSON de cuenta con ese titular y ese saldo, sea cual sea el orden de sus campos. */
	private static String accountWithBalance(String holderName, String balance) {
		return "\\{(?=[^}]*\"holderName\":\"%s\")(?=[^}]*\"balance\":%s[,}])".formatted(holderName,
				balance.replace(".", "\\."));
	}
}
