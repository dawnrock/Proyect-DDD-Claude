package com.pronoide.ddd;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

import com.pronoide.ddd.application.DemoDataLoader;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DddLabApplicationTests {

	@LocalServerPort
	private int port;

	private final HttpClient http = HttpClient.newHttpClient();

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
	}

	@Test
	@DisplayName("Sin el perfil demo no se cargan los datos de demostración")
	void sin_el_perfil_demo_no_se_cargan_los_datos_de_demostracion() {
		assertThat(context.getBeansOfType(DemoDataLoader.class)).isEmpty();
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

	@Nested
	@DisplayName("Abrir, consultar y listar cuentas con el servicio real (#5)")
	class Accounts {

		@Test
		@DisplayName("Una cuenta abierta por la API se consulta por su id y aparece en el listado web")
		void una_cuenta_abierta_por_la_api_se_consulta_por_su_id_y_aparece_en_el_listado_web() throws Exception {
			HttpResponse<String> created = post("/api/accounts", "application/json", """
					{"accountNumber": "es91 2100 0418 4502 0005 1332", "holderName": "Ada Lovelace",
					 "segment": "RETAIL", "initialDeposit": 100.00}""");

			assertThat(created.statusCode()).isEqualTo(201);
			assertThat(created.body()).contains("\"accountNumber\":\"ES9121000418450200051332\"");
			String location = created.headers().firstValue("Location").orElseThrow();

			HttpResponse<String> details = get(location, "application/json");
			assertThat(details.statusCode()).isEqualTo(200);
			assertThat(details.body()).contains("\"type\":\"DEPOSIT\"").contains("\"status\":\"APPROVED\"");

			assertThat(get("/", "text/html").body()).contains("ES9121000418450200051332").contains("Ada Lovelace");
		}

		@Test
		@DisplayName("Una cuenta abierta desde el formulario web redirige al listado y aparece en él")
		void una_cuenta_abierta_desde_el_formulario_web_redirige_al_listado_y_aparece_en_el() throws Exception {
			HttpResponse<String> response = submitForm("ES7620770024003102575766", "EUR");

			assertThat(response.statusCode()).isEqualTo(302);
			assertThat(response.headers().firstValue("Location"))
					.as("sin ;jsessionid en la URL, que Spring no reconoce y acaba en 404")
					.hasValueSatisfying(location -> assertThat(location).doesNotContain(";jsessionid"));
			assertThat(get("/", "text/html").body()).contains("ES7620770024003102575766");
		}

		@Test
		@DisplayName("Un número de cuenta ya usado vuelve al formulario con el mensaje del dominio")
		void un_numero_de_cuenta_ya_usado_vuelve_al_formulario_con_el_mensaje_del_dominio() throws Exception {
			submitForm("ES6000491500051234567892", "EUR");

			HttpResponse<String> duplicate = submitForm("es60 0049 1500 0512 3456 7892", "EUR");

			assertThat(duplicate.statusCode()).isEqualTo(200);
			assertThat(duplicate.body()).contains("Ya existe una cuenta con el").contains("ES6000491500051234567892");
		}

		@Test
		@DisplayName("Un depósito en otra divisa vuelve al formulario con el mensaje del dominio")
		void un_deposito_en_otra_divisa_vuelve_al_formulario_con_el_mensaje_del_dominio() throws Exception {
			HttpResponse<String> response = submitForm("ES1000492352082414205416", "USD");

			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).contains("EUR").contains("USD").contains("ha recibido");
			assertThat(get("/", "text/html").body()).doesNotContain("ES1000492352082414205416");
		}

		private HttpResponse<String> submitForm(String accountNumber, String currency) throws Exception {
			String form = "accountNumber=%s&holderName=Grace+Hopper&segment=RETAIL&initialDeposit=75.00&currency=%s"
					.formatted(URLEncoder.encode(accountNumber, StandardCharsets.UTF_8), currency);
			return post("/accounts/new", "application/x-www-form-urlencoded", form);
		}
	}

	@Nested
	@DisplayName("Ingresos y retiradas con el servicio real (#6)")
	class Movements {

		@Test
		@DisplayName("Por la API, un ingreso se aprueba y una retirada sin saldo se registra como rechazada y se ve en el detalle web")
		void por_la_api_un_ingreso_se_aprueba_y_una_retirada_sin_saldo_se_registra_como_rechazada() throws Exception {
			String account = openAccount("ES0000000000000000000601");

			HttpResponse<String> deposit = post(account + "/deposits", "application/json", "{\"amount\": 50.00}");
			HttpResponse<String> withdrawal = post(account + "/withdrawals", "application/json", "{\"amount\": 500.00}");

			assertThat(deposit.statusCode()).isEqualTo(201);
			assertThat(deposit.body()).contains("\"status\":\"APPROVED\"");
			assertThat(withdrawal.statusCode()).isEqualTo(201);
			assertThat(withdrawal.body()).contains("\"status\":\"REJECTED\"");
			assertThat(get(account, "application/json").body()).contains("\"balance\":150.00");
			assertThat(get(webPath(account), "text/html").body()).contains("Rechazado").contains("150,00 €");
		}

		@Test
		@DisplayName("Desde la web, un ingreso y una retirada rechazada redirigen al detalle con su mensaje")
		void desde_la_web_un_ingreso_y_una_retirada_rechazada_redirigen_al_detalle_con_su_mensaje() throws Exception {
			String detail = webPath(openAccount("ES0000000000000000000602"));

			HttpResponse<String> deposit = submit(detail + "/deposits", "amount=25.00&currency=EUR");
			assertThat(deposit.statusCode()).isEqualTo(302);
			assertThat(deposit.headers().firstValue("Location")).hasValueSatisfying(
					location -> assertThat(location).endsWith(detail).doesNotContain(";jsessionid"));
			assertThat(browse(detail)).contains("Ingreso de 25,00 € aprobado");

			assertThat(submit(detail + "/withdrawals", "amount=1000.00&currency=EUR").statusCode()).isEqualTo(302);
			assertThat(browse(detail)).contains("rechazada").contains("125,00 €");
		}

		@Test
		@DisplayName("Una cuenta inexistente es la página 404 en la web y un ProblemDetail 404 en la API")
		void una_cuenta_inexistente_es_la_pagina_404_en_la_web_y_un_problem_detail_404_en_la_api() throws Exception {
			String unknown = "/api/accounts/" + java.util.UUID.randomUUID();

			HttpResponse<String> web = get(webPath(unknown), "text/html");
			HttpResponse<String> api = post(unknown + "/deposits", "application/json", "{\"amount\": 10.00}");

			assertThat(web.statusCode()).isEqualTo(404);
			assertThat(web.body()).contains("Página no encontrada");
			assertThat(api.statusCode()).isEqualTo(404);
			assertThat(api.headers().firstValue("Content-Type")).hasValueSatisfying(
					type -> assertThat(type).contains("problem+json"));
			assertThat(api.body()).contains("Recurso inexistente");
		}
	}

	@Nested
	@DisplayName("Transferencias con el servicio real (#7)")
	class Transfers {

		@Test
		@DisplayName("Por la API, una transferencia aprobada mueve el saldo y una rechazada no llega al destino")
		void por_la_api_una_transferencia_aprobada_mueve_el_saldo_y_una_rechazada_no_llega_al_destino() throws Exception {
			String source = openAccount("ES0000000000000000000701");
			String target = openAccount("ES0000000000000000000702");

			HttpResponse<String> approved = transfer(source, target, "40.00");
			HttpResponse<String> rejected = transfer(source, target, "500.00");

			assertThat(approved.statusCode()).isEqualTo(201);
			assertThat(approved.body()).contains("\"status\":\"APPROVED\"")
					.contains("\"targetAccountNumber\":\"ES0000000000000000000702\"");
			assertThat(rejected.statusCode()).isEqualTo(201);
			assertThat(rejected.body()).contains("\"status\":\"REJECTED\"");
			assertThat(get(source, "application/json").body()).contains("\"balance\":60.00");
			assertThat(get(target, "application/json").body()).contains("\"balance\":140.00");
		}

		@Test
		@DisplayName("Por la API, una transferencia a la misma cuenta es una regla de negocio incumplida")
		void por_la_api_una_transferencia_a_la_misma_cuenta_es_una_regla_de_negocio_incumplida() throws Exception {
			String account = openAccount("ES0000000000000000000703");

			HttpResponse<String> response = transfer(account, account, "10.00");

			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.body()).contains("Regla de negocio incumplida").contains("misma cuenta");
			assertThat(get(account, "application/json").body()).contains("\"balance\":100.00");
		}

		@Test
		@DisplayName("Desde la web, una transferencia redirige al detalle con su mensaje y el destino recibe el ingreso")
		void desde_la_web_una_transferencia_redirige_al_detalle_con_su_mensaje_y_el_destino_recibe_el_ingreso()
				throws Exception {
			String source = webPath(openAccount("ES0000000000000000000704"));
			String target = webPath(openAccount("ES0000000000000000000705"));

			HttpResponse<String> response = submit(source + "/transfers",
					"targetAccountId=%s&amount=25.00&currency=EUR".formatted(idOf(target)));

			assertThat(response.statusCode()).isEqualTo(302);
			assertThat(response.headers().firstValue("Location")).hasValueSatisfying(
					location -> assertThat(location).endsWith(source).doesNotContain(";jsessionid"));
			assertThat(browse(source)).contains("Transferencia de 25,00 € a ES00 •••• 0705 aprobada")
					.contains("75,00 €");
			assertThat(browse(target)).contains("125,00 €");
		}

		private HttpResponse<String> transfer(String source, String target, String amount) throws Exception {
			return post("/api/transfers", "application/json", """
					{"sourceAccountId": "%s", "targetAccountId": "%s", "amount": %s}"""
					.formatted(idOf(source), idOf(target), amount));
		}

		private String idOf(String path) {
			return path.substring(path.lastIndexOf('/') + 1);
		}
	}

	/** Con cookies: el mensaje flash tras la redirección viaja en la sesión. */
	private final HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

	/** Abre una cuenta minorista con 100,00 € y devuelve su ruta de la API ({@code /api/accounts/{id}}). */
	private String openAccount(String accountNumber) throws Exception {
		HttpResponse<String> created = post("/api/accounts", "application/json", """
				{"accountNumber": "%s", "holderName": "Grace Hopper", "segment": "RETAIL", "initialDeposit": 100.00}"""
				.formatted(accountNumber));
		assertThat(created.statusCode()).isEqualTo(201);
		return created.headers().firstValue("Location").orElseThrow();
	}

	private String webPath(String apiPath) {
		return apiPath.replaceFirst("^/api", "");
	}

	private HttpResponse<String> submit(String path, String form) throws Exception {
		return browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form))
				.build(), HttpResponse.BodyHandlers.ofString());
	}

	private String browse(String path) throws Exception {
		return browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
				HttpResponse.BodyHandlers.ofString()).body();
	}

	private HttpResponse<String> get(String path, String accept) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Accept", accept)
				.GET()
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> post(String path, String contentType, String body) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Content-Type", contentType)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
