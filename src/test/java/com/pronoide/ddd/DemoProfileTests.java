package com.pronoide.ddd;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
class DemoProfileTests {

	/** Contraseña de los usuarios del perfil demo. */
	private static final String PASSWORD = "demo";

	@LocalServerPort
	private int port;

	private HttpResponse<String> listAccountsAs(String username) throws Exception {
		return apiGetAs(username, "/api/accounts");
	}

	private HttpResponse<String> apiGetAs(String username, String path) throws Exception {
		String credentials = Base64.getEncoder().encodeToString((username + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
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

	@Test
	@DisplayName("Con el perfil demo, la API muestra en el ingreso de Ada la cuenta de Grace como contrapartida de la transferencia")
	void con_el_perfil_demo_la_api_muestra_la_contrapartida_de_la_transferencia() throws Exception {
		JsonNode accounts = JSON.readTree(listAccountsAs("admin").body());
		JsonNode ada = accountOf(accounts, "Ada Lovelace");
		String graceIban = accountOf(accounts, "Grace Hopper").get("accountNumber").asString();

		JsonNode details = JSON.readTree(apiGetAs("ada", "/api/accounts/" + ada.get("id").asString()).body());

		JsonNode transfer = null;
		int withCounterpart = 0;
		for (JsonNode transaction : details.get("transactions")) {
			if (transaction.has("counterpartAccountNumber")) {
				withCounterpart++;
				transfer = transaction;
			}
		}
		assertThat(withCounterpart).isEqualTo(1);
		assertThat(transfer.get("type").asString()).isEqualTo("DEPOSIT");
		assertThat(transfer.get("amount").decimalValue()).isEqualByComparingTo("300.00");
		assertThat(transfer.get("counterpartAccountNumber").asString()).isEqualTo(graceIban);
	}

	@Test
	@DisplayName("Con el perfil demo, la web muestra a Grace primero la transferencia a la cuenta de Ada")
	void con_el_perfil_demo_la_web_muestra_primero_la_transferencia_a_ada() throws Exception {
		JsonNode accounts = JSON.readTree(listAccountsAs("admin").body());
		String adaIban = accountOf(accounts, "Ada Lovelace").get("accountNumber").asString();
		String graceId = accountOf(accounts, "Grace Hopper").get("id").asString();

		String page = webGetAs("grace", "/accounts/" + graceId);

		String transferLabel = "Transferencia a " + adaIban.substring(0, 4) + "\u00a0••••\u00a0"
				+ adaIban.substring(adaIban.length() - 4);
		assertThat(page).contains(transferLabel);
		assertThat(page.indexOf(transferLabel)).isLessThan(page.indexOf(">Ingreso<"));
	}

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static JsonNode accountOf(JsonNode accounts, String holderName) {
		for (JsonNode account : accounts) {
			if (holderName.equals(account.get("holderName").asString())) {
				return account;
			}
		}
		throw new AssertionError("No hay cuenta de " + holderName);
	}

	/** Entra en la web con el formulario de login (sesión por cookie y token CSRF) y pide una página. */
	private String webGetAs(String username, String path) throws Exception {
		HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager())
				.followRedirects(HttpClient.Redirect.NORMAL).build();
		String loginPage = browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login")).GET().build(),
				HttpResponse.BodyHandlers.ofString()).body();
		Matcher csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(loginPage);
		assertThat(csrf.find()).as("token CSRF en /login").isTrue();
		String form = "username=" + username + "&password=" + PASSWORD + "&_csrf="
				+ URLEncoder.encode(csrf.group(1), StandardCharsets.UTF_8);
		browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
		return browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
	}

	/** Un objeto JSON de cuenta con ese titular y ese saldo, sea cual sea el orden de sus campos. */
	private static String accountWithBalance(String holderName, String balance) {
		return "\\{(?=[^}]*\"holderName\":\"%s\")(?=[^}]*\"balance\":%s[,}])".formatted(holderName,
				balance.replace(".", "\\."));
	}
}
