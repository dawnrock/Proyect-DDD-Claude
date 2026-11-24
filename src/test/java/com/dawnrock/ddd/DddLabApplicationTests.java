package com.dawnrock.ddd;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

import com.dawnrock.ddd.adapter.in.cli.DomainModelRunner;
import com.dawnrock.ddd.adapter.out.event.OutboxRelay;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.CheckingAccountCommands;
import com.dawnrock.ddd.application.CheckingAccountQueries;
import com.dawnrock.ddd.application.DepositCommand;
import com.dawnrock.ddd.application.OpenAccountCommand;
import com.dawnrock.ddd.application.listener.AuditLogListener;
import com.dawnrock.ddd.application.listener.CustomerNotificationListener;
import com.dawnrock.ddd.support.ApiTokens;

/**
 * Usuarios de test (#50): admin, ada, grace y alan, todos con la contraseña "demo" (mismo hash que el perfil demo).
 * alan solo lo usa "Login y roles", para tener un usuario con una sola cuenta.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		// Todas las altas de estos tests llegan desde 127.0.0.1: el límite de altas (#131) se prueba en sus tests
		"app.security.sign-ups.max-per-ip=1000",
		"app.security.sign-ups.max-total=1000",
		"app.security.sign-ups.max-failures-per-ip=1000",
		"app.security.users[0].username=admin",
		"app.security.users[0].password-hash=" + DddLabApplicationTests.DEMO_HASH,
		"app.security.users[0].role=ADMIN",
		"app.security.users[1].username=ada",
		"app.security.users[1].password-hash=" + DddLabApplicationTests.DEMO_HASH,
		"app.security.users[1].role=USER",
		"app.security.users[2].username=grace",
		"app.security.users[2].password-hash=" + DddLabApplicationTests.DEMO_HASH,
		"app.security.users[2].role=USER",
		"app.security.users[3].username=alan",
		"app.security.users[3].password-hash=" + DddLabApplicationTests.DEMO_HASH,
		"app.security.users[3].role=USER"})
class DddLabApplicationTests {

	/** Hash BCrypt de "demo". */
	static final String DEMO_HASH = "$2a$10$XbpS91y/CFr.Zvx6Xxi7RuLFFUMRM9adecz2G7FO4YClrsKKCdKWW";

	/** Contraseña de los usuarios de test. */
	static final String PASSWORD = "demo";

	@LocalServerPort
	private int port;

	private final HttpClient http = HttpClient.newHttpClient();

	@Autowired
	private ApplicationContext context;

	@Autowired
	private CheckingAccountCommands commands;

	@Test
	void contextLoads() {
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	@DisplayName("Un ingreso llega al consumidor de auditoría a través del publicador de eventos")
	void aDepositReachesTheAuditConsumer(CapturedOutput output) {
		Caller admin = Caller.builder().username("admin").admin(true).build();
		UUID accountId = commands.openAccount(admin, OpenAccountCommand.builder()
				.holderName("Ada Lovelace")
				.holderTaxId("12345678Z")
				.holderEmail("ada@example.com")
				.initialDeposit(new BigDecimal("100"))
				.ownerUsername("ada")
				.build()).id();

		commands.deposit(admin, DepositCommand.builder().accountId(accountId).amount(new BigDecimal("25")).build());
		// Desde #165 los eventos se guardan en el outbox y los entrega el relé, no la propia transacción
		context.getBean(OutboxRelay.class).relayPending();

		assertThat(output).contains("Auditoría · MoneyDeposited · cuenta " + accountId);
	}

	@Test
	@DisplayName("El contexto tiene un único servicio detrás de los dos puertos de entrada y los dos consumidores de eventos")
	void theContextWiresTheCoreFromConfig() {
		assertThat(context.getBeansOfType(CheckingAccountCommands.class)).hasSize(1);
		assertThat(context.getBean(CheckingAccountCommands.class)).isSameAs(context.getBean(CheckingAccountQueries.class));
		assertThat(context.getBeansOfType(AuditLogListener.class)).hasSize(1);
		assertThat(context.getBeansOfType(CustomerNotificationListener.class)).hasSize(1);
	}

	@Test
	@DisplayName("Sin el perfil demo no se cargan los datos de demostración")
	void withoutTheDemoProfileNoDemoDataIsLoaded() {
		assertThat(context.getBeansOfType(DomainModelRunner.class)).isEmpty();
	}

	@Test
	@DisplayName("Una página inexistente muestra la página 404 propia de la web")
	void aMissingPageShowsTheWebsOwn404Page() throws Exception {
		HttpResponse<String> response = page("/no-existe");

		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("text/html"));
		assertThat(response.body()).contains("Página no encontrada").contains("Cuentas corrientes");
	}

	@Test
	@DisplayName("Una ruta inexistente de la API sigue respondiendo en JSON")
	void aMissingApiRouteStillRespondsInJson() throws Exception {
		HttpResponse<String> response = get("/api/no-existe", "application/json");

		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).contains("json"));
	}

	@Nested
	@DisplayName("Abrir, consultar y listar cuentas con el servicio real (#5)")
	class Accounts {

		@Test
		@DisplayName("Una cuenta abierta por la API se consulta por su id y aparece en el listado web")
		void anAccountOpenedThroughTheApiIsFoundByIdAndAppearsInTheWebList() throws Exception {
			HttpResponse<String> created = post("/api/accounts", "application/json", """
					{"holderName": "Ada Lovelace", "holderTaxId": "12345678Z", "holderEmail": "ada@example.com",
					 "initialDeposit": 100.00, "ownerUsername": "ada"}""");

			assertThat(created.statusCode()).isEqualTo(201);
			assertThat(created.body()).containsPattern("\"accountNumber\":\"ES\\d{22}\"").contains("\"segment\":\"RETAIL\"");
			String location = created.headers().firstValue("Location").orElseThrow();
			String iban = ibanOf(location);

			HttpResponse<String> details = get(location, "application/json");
			assertThat(details.statusCode()).isEqualTo(200);
			assertThat(details.body()).contains("\"type\":\"DEPOSIT\"").contains("\"status\":\"APPROVED\"");

			assertThat(browse("/")).contains(iban).contains("Ada Lovelace");
		}

		@Test
		@DisplayName("Una cuenta abierta desde el formulario web redirige al listado y aparece en él")
		void anAccountOpenedFromTheWebFormRedirectsToTheListAndAppearsInIt() throws Exception {
			HttpResponse<String> response = submitForm("EUR");

			assertThat(response.statusCode()).isEqualTo(302);
			assertThat(response.headers().firstValue("Location"))
					.as("sin ;jsessionid en la URL, que Spring no reconoce y acaba en 404")
					.hasValueSatisfying(location -> assertThat(location).doesNotContain(";jsessionid"));
			assertThat(browse("/")).contains("Grace Hopper");
		}

		@Test
		@DisplayName("Un depósito en otra divisa vuelve al formulario con el mensaje del dominio")
		void aDepositInAnotherCurrencyReturnsToTheFormWithTheDomainMessage() throws Exception {
			HttpResponse<String> response = submitForm("USD");

			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).contains("EUR").contains("USD").contains("ha recibido");
		}

		@Test
		@DisplayName("Por la API, la factoría decide el segmento con los ingresos que verifica la agencia tributaria")
		void throughTheApiTheFactoryDecidesTheSegmentFromTheVerifiedIncome() throws Exception {
			HttpResponse<String> highIncome = post("/api/accounts", "application/json", """
					{"holderName": "Grace Hopper", "holderTaxId": "87654321X", "holderEmail": "grace@example.com",
					 "initialDeposit": 100.00, "ownerUsername": "grace"}""");
			HttpResponse<String> unknown = post("/api/accounts", "application/json", """
					{"holderName": "Sin Verificar", "holderTaxId": "99999999R", "holderEmail": "sin@example.com",
					 "initialDeposit": 100.00, "ownerUsername": "sinverificar"}""");

			assertThat(highIncome.statusCode()).isEqualTo(201);
			assertThat(highIncome.body()).contains("\"segment\":\"HIGH_NET_WORTH\"");
			assertThat(unknown.statusCode()).isEqualTo(201);
			assertThat(unknown.body()).contains("\"segment\":\"RETAIL\"");
			assertThat(ibanOf(highIncome.headers().firstValue("Location").orElseThrow()))
					.isNotEqualTo(ibanOf(unknown.headers().firstValue("Location").orElseThrow()));
		}

		@Test
		@DisplayName("Desde la web, el aviso del alta muestra el IBAN generado y el segmento calculado")
		void fromTheWebTheOpeningNoticeShowsTheGeneratedIbanAndTheSegment() throws Exception {
			String form = "holderName=Grace+Hopper&holderTaxId=87654321X&holderEmail=grace%40example.com&ownerUsername=grace"
					+ "&initialDeposit=75.00&currency=EUR";
			assertThat(submit("/accounts/new", form).statusCode()).isEqualTo(302);

			assertThat(browse("/")).containsPattern("Cuenta ES\\d{22} abierta correctamente como Alto patrimonio");
		}

		private HttpResponse<String> submitForm(String currency) throws Exception {
			String form = "holderName=Grace+Hopper&holderTaxId=87654321X&holderEmail=grace%40example.com&ownerUsername=grace"
					+ "&initialDeposit=75.00&currency=%s"
					.formatted(currency);
			return submit("/accounts/new", form);
		}
	}

	@Nested
	@DisplayName("Ingresos y retiradas con el servicio real (#6)")
	class Movements {

		@Test
		@DisplayName("Por la API, un ingreso se aprueba y una retirada sin saldo se registra como rechazada y se ve en el detalle web")
		void throughTheApiADepositIsApprovedAndAWithdrawalWithoutBalanceIsRecordedAsRejected() throws Exception {
			String account = openAccount();

			HttpResponse<String> deposit = post(account + "/deposits", "application/json", "{\"amount\": 50.00}");
			HttpResponse<String> withdrawal = post(account + "/withdrawals", "application/json", "{\"amount\": 500.00}");

			assertThat(deposit.statusCode()).isEqualTo(201);
			assertThat(deposit.body()).contains("\"status\":\"APPROVED\"");
			assertThat(withdrawal.statusCode()).isEqualTo(201);
			assertThat(withdrawal.body()).contains("\"status\":\"REJECTED\"");
			assertThat(get(account, "application/json").body()).contains("\"balance\":150.00");
			assertThat(browse(webPath(account))).contains("Rechazado").contains("150,00 €");
		}

		@Test
		@DisplayName("Desde la web, un ingreso y una retirada rechazada redirigen al detalle con su mensaje")
		void fromTheWebADepositAndARejectedWithdrawalRedirectToTheDetailsWithTheirMessage() throws Exception {
			String detail = webPath(openAccount());

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
		void aMissingAccountIsThe404PageOnTheWebAndA404ProblemDetailOnTheApi() throws Exception {
			String unknown = "/api/accounts/" + java.util.UUID.randomUUID();

			HttpResponse<String> web = page(webPath(unknown));
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
		void throughTheApiAnApprovedTransferMovesTheBalanceAndARejectedOneDoesNotReachTheTarget() throws Exception {
			String source = openAccount();
			String target = openAccount();

			String targetIban = ibanOf(target);
			HttpResponse<String> approved = transfer(source, targetIban, "40.00");
			HttpResponse<String> rejected = transfer(source, targetIban, "500.00");

			assertThat(approved.statusCode()).isEqualTo(201);
			assertThat(approved.body()).contains("\"status\":\"APPROVED\"")
					.contains("\"targetAccountNumber\":\"" + targetIban + "\"");
			assertThat(rejected.statusCode()).isEqualTo(201);
			assertThat(rejected.body()).contains("\"status\":\"REJECTED\"");
			assertThat(get(source, "application/json").body()).contains("\"balance\":60.00");
			assertThat(get(target, "application/json").body()).contains("\"balance\":140.00");
		}

		@Test
		@DisplayName("Por la API, una transferencia a la misma cuenta es una regla de negocio incumplida")
		void throughTheApiATransferToTheSameAccountIsABrokenBusinessRule() throws Exception {
			String account = openAccount();

			HttpResponse<String> response = transfer(account, ibanOf(account), "10.00");

			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.body()).contains("Regla de negocio incumplida").contains("misma cuenta");
			assertThat(get(account, "application/json").body()).contains("\"balance\":100.00");
		}

		@Test
		@DisplayName("Desde la web, una transferencia redirige al detalle con su mensaje y el destino recibe el ingreso")
		void fromTheWebATransferRedirectsToTheDetailsAndTheTargetGetsTheDeposit()
				throws Exception {
			String sourceApi = openAccount();
			String targetApi = openAccount();
			String source = webPath(sourceApi);
			String target = webPath(targetApi);
			String targetIban = ibanOf(targetApi);

			HttpResponse<String> response = submit(source + "/transfers",
					"targetAccountNumber=" + targetIban + "&amount=25.00&currency=EUR");

			assertThat(response.statusCode()).isEqualTo(302);
			assertThat(response.headers().firstValue("Location")).hasValueSatisfying(
					location -> assertThat(location).endsWith(source).doesNotContain(";jsessionid"));
			assertThat(browse(source)).contains("Transferencia de 25,00 € a "
					+ targetIban.substring(0, 4) + "\u00A0••••\u00A0" + targetIban.substring(20) + " aprobada")
					.contains("75,00 €");
			assertThat(browse(target)).contains("125,00 €");
		}

		private HttpResponse<String> transfer(String source, String targetIban, String amount) throws Exception {
			return post("/api/transfers", "application/json", """
					{"sourceAccountId": "%s", "targetAccountNumber": "%s", "amount": %s}"""
					.formatted(idOf(source), targetIban, amount));
		}

		private String idOf(String path) {
			return path.substring(path.lastIndexOf('/') + 1);
		}
	}

	@Nested
	@DisplayName("Login y roles con el servicio real (#50)")
	class LoginAndRoles {

		@Test
		@DisplayName("Sin sesión, la web lleva al login y la API responde 401 con un ProblemDetail")
		void withoutSessionTheWebLeadsToLoginAndTheApiResponds401() throws Exception {
			HttpResponse<String> web = anonymous("/");
			HttpResponse<String> api = http.send(HttpRequest.newBuilder(URI.create(url("/api/accounts")))
					.header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());

			assertThat(web.statusCode()).isEqualTo(302);
			assertThat(web.headers().firstValue("Location")).hasValueSatisfying(
					location -> assertThat(location).endsWith("/login"));
			assertThat(api.statusCode()).isEqualTo(401);
			assertThat(api.body()).contains("No autenticado");
		}

		@Test
		@DisplayName("Un usuario con una sola cuenta entra directamente en ella y una cuenta ajena es un 404")
		void aUserWithASingleAccountEntersItAndAForeignOneIsA404() throws Exception {
			String alansPath = openAccountFor("alan");
			String othersPath = openAccountFor("grace");
			String alans = idOf(alansPath);
			String others = idOf(othersPath);
			HttpClient alan = loginAs("alan");

			HttpResponse<String> home = send(alan, "/");
			HttpResponse<String> foreign = send(alan, "/accounts/" + others);

			assertThat(home.statusCode()).isEqualTo(302);
			assertThat(home.headers().firstValue("Location")).hasValueSatisfying(
					location -> assertThat(location).endsWith("/accounts/" + alans));
			assertThat(send(alan, "/accounts/" + alans).body()).contains(ibanOf(alansPath))
					.doesNotContain(ibanOf(othersPath));
			assertThat(foreign.statusCode()).isEqualTo(404);
		}

		@Test
		@DisplayName("Por la API, un usuario no abre cuentas (403), no ve las ajenas (404) y transfiere por IBAN")
		void throughTheApiAUserCannotOpenAccountsCannotSeeOthersAndTransfersByIban() throws Exception {
			String graces = idOf(openAccountFor("grace"));
			String adasPath = openAccountFor("ada");
			String adas = idOf(adasPath);

			HttpResponse<String> open = api("grace", "POST", "/api/accounts", """
					{"holderName": "Grace Hopper", "holderTaxId": "87654321X", "holderEmail": "grace@example.com",
					 "initialDeposit": 100.00, "ownerUsername": "grace"}""");
			HttpResponse<String> foreign = api("grace", "GET", "/api/accounts/" + adas, null);
			HttpResponse<String> transfer = api("grace", "POST", "/api/transfers", """
					{"sourceAccountId": "%s", "targetAccountNumber": "%s", "amount": 30}"""
					.formatted(graces, ibanOf(adasPath)));

			assertThat(open.statusCode()).isEqualTo(403);
			assertThat(open.body()).contains("Acceso denegado");
			assertThat(foreign.statusCode()).isEqualTo(404);
			assertThat(transfer.statusCode()).isEqualTo(201);
			assertThat(transfer.body()).contains("\"status\":\"APPROVED\"");
			assertThat(api("ada", "GET", "/api/accounts/" + adas, null).body()).contains("\"balance\":130.00");
		}

		@Test
		@DisplayName("La consola H2 es solo para administradores y la salud pública no muestra detalles")
		void theH2ConsoleIsAdministratorOnlyAndThePublicHealthShowsNoDetails() throws Exception {
			HttpResponse<String> console = send(loginAs("ada"), "/h2-console/");
			HttpResponse<String> health = get("/actuator/health", "application/json");

			assertThat(console.statusCode()).isEqualTo(403);
			assertThat(send(loginAs("admin"), "/h2-console/").statusCode()).isEqualTo(200);
			assertThat(health.statusCode()).isEqualTo(200);
			assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("components", "diskSpace");
		}

		/** Un cliente por usuario (R30): cada propietario tiene su documento. */
		private String openAccountFor(String owner) throws Exception {
			String[] holder = switch (owner) {
				case "ada" -> new String[] {"Ada Lovelace", "12345678Z"};
				case "grace" -> new String[] {"Grace Hopper", "87654321X"};
				default -> new String[] {"Alan Turing", "11111111H"};
			};
			HttpResponse<String> created = post("/api/accounts", "application/json", """
					{"holderName": "%s", "holderTaxId": "%s", "holderEmail": "%s@example.com",
					 "initialDeposit": 100.00, "ownerUsername": "%s"}""".formatted(holder[0], holder[1], owner, owner));
			assertThat(created.statusCode()).isEqualTo(201);
			return created.headers().firstValue("Location").orElseThrow();
		}

		private String idOf(String path) {
			return path.substring(path.lastIndexOf('/') + 1);
		}

		/** Inicia sesión por el formulario (token CSRF incluido) en un navegador nuevo, con sus propias cookies. */
		private HttpClient loginAs(String username) throws Exception {
			HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
			java.util.regex.Matcher csrf = java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"")
					.matcher(send(client, "/login?form").body());
			assertThat(csrf.find()).isTrue();
			HttpResponse<String> login = client.send(HttpRequest.newBuilder(URI.create(url("/login")))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString("username=%s&password=%s&_csrf=%s"
							.formatted(username, PASSWORD, csrf.group(1))))
					.build(), HttpResponse.BodyHandlers.ofString());
			assertThat(login.headers().firstValue("Location")).hasValueSatisfying(
					location -> assertThat(location).doesNotContain("error"));
			return client;
		}

		private HttpResponse<String> send(HttpClient client, String path) throws Exception {
			return client.send(HttpRequest.newBuilder(URI.create(url(path))).header("Accept", "text/html").GET().build(),
					HttpResponse.BodyHandlers.ofString());
		}

		private HttpResponse<String> anonymous(String path) throws Exception {
			return send(HttpClient.newHttpClient(), path);
		}

		private HttpResponse<String> api(String username, String method, String path, String json) throws Exception {
			HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url(path)))
					.header("Authorization", ApiTokens.bearer(http, url(""), username, PASSWORD))
					.header("Accept", "application/json");
			request = json == null ? request.GET() : request.header("Content-Type", "application/json")
					.method(method, HttpRequest.BodyPublishers.ofString(json));
			return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

	@Nested
	@DisplayName("IP del cliente detrás de un proxy (#130)")
	class ClientIpBehindProxy {

		@Test
		@DisplayName("Detrás de un proxy cuenta la IP de X-Forwarded-For: bloqueada esa, otra IP de la misma usuaria entra")
		void behindAProxyTheXForwardedForIpCounts() throws Exception {
			for (int i = 0; i < 3; i++) {
				assertThat(login("grace", "mala", "203.0.113.5")).endsWith("/login?error");
			}

			assertThat(login("grace", PASSWORD, "203.0.113.5")).endsWith("/login?blocked");
			assertThat(login("grace", PASSWORD, "203.0.113.6")).doesNotContain("login");
		}

		@Test
		@DisplayName("Detrás de Cloudflare cuenta la IP del cliente, no la del nodo de Cloudflare (#133)")
		void behindCloudflareTheClientIpCounts() throws Exception {
			String cloudflareEdge = "104.22.136.75";
			for (int i = 0; i < 3; i++) {
				assertThat(login("alan", "mala", "198.51.100.20, " + cloudflareEdge)).endsWith("/login?error");
			}

			assertThat(login("alan", PASSWORD, "198.51.100.20, " + cloudflareEdge)).endsWith("/login?blocked");
			// Otro cliente que entra por el mismo nodo de Cloudflare no queda bloqueado
			assertThat(login("alan", PASSWORD, "198.51.100.21, " + cloudflareEdge)).doesNotContain("login");
		}

		/** Inicio de sesión por el formulario como si llegara a través del proxy; devuelve adónde redirige. */
		private String login(String username, String password, String clientIp) throws Exception {
			HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
			HttpResponse<String> form = client.send(HttpRequest.newBuilder(URI.create(url("/login?form")))
					.header("X-Forwarded-For", clientIp).GET().build(), HttpResponse.BodyHandlers.ofString());
			java.util.regex.Matcher csrf = java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"")
					.matcher(form.body());
			assertThat(csrf.find()).isTrue();
			HttpResponse<String> login = client.send(HttpRequest.newBuilder(URI.create(url("/login")))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.header("X-Forwarded-For", clientIp)
					.POST(HttpRequest.BodyPublishers.ofString("username=%s&password=%s&_csrf=%s"
							.formatted(username, password, csrf.group(1))))
					.build(), HttpResponse.BodyHandlers.ofString());
			return login.headers().firstValue("Location").orElseThrow();
		}
	}

	@Nested
	@DisplayName("El usuario del inicio de sesión no distingue mayúsculas, con el almacén de usuarios real (#157)")
	class CaseInsensitiveUsername {

		/** IP propia: estos inicios de sesión no comparten el límite de intentos con otros tests del contexto. */
		private static final String CLIENT_IP = "203.0.113.157";

		@Test
		@DisplayName("Por la web, «Ada» entra como ada")
		void throughTheWebAdaWithACapitalLogsInAsAda() throws Exception {
			CookieManager cookies = new CookieManager();
			HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
			HttpResponse<String> form = client.send(HttpRequest.newBuilder(URI.create(url("/login?form")))
					.header("X-Forwarded-For", CLIENT_IP).GET().build(), HttpResponse.BodyHandlers.ofString());
			Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(form.body());
			assertThat(csrf.find()).isTrue();

			HttpResponse<String> login = client.send(HttpRequest.newBuilder(URI.create(url("/login")))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.header("X-Forwarded-For", CLIENT_IP)
					.POST(HttpRequest.BodyPublishers.ofString("username=%s&password=%s&_csrf=%s"
							.formatted(URLEncoder.encode(" Ada ", StandardCharsets.UTF_8), PASSWORD, csrf.group(1))))
					.build(), HttpResponse.BodyHandlers.ofString());

			String location = login.headers().firstValue("Location").orElseThrow();
			assertThat(location).doesNotContain("login");
			HttpClient browser = HttpClient.newBuilder().cookieHandler(cookies)
					.followRedirects(HttpClient.Redirect.NORMAL).build();
			HttpResponse<String> home = browser.send(HttpRequest.newBuilder(URI.create(location))
					.header("Accept", "text/html").GET().build(), HttpResponse.BodyHandlers.ofString());
			assertThat(home.body()).contains("<span>ada</span>");
		}

		@Test
		@DisplayName("Por la API, « ADA » recibe un token cuyo sub es ada")
		void throughTheApiAdaInCapitalsGetsATokenForAda() throws Exception {
			String bearer = ApiTokens.bearer(http, url(""), " ADA ", PASSWORD);

			String payload = bearer.substring("Bearer ".length()).split("\\.")[1];
			String claims = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
			assertThat(claims).contains("\"sub\":\"ada\"");
		}
	}

	@Nested
	@DisplayName("Alta de cliente de Mon-A Bank con el servicio real (#103)")
	class CustomerSignUp {

		/** NIE válido que no usa ningún otro test del contexto compartido. */
		private static final String WEB_DOCUMENT = "X1234567L";
		private static final String REST_DOCUMENT = "Y7654321G";
		private static final String NEW_PASSWORD = "clave-segura-1";

		@Test
		@DisplayName("Por la web: dos puertas, alta en tres pasos, sesión iniciada, otra cuenta y de nuevo a entrar")
		void throughTheWebTheThreeStepSignUpLogsInAndAllowsOpeningAnotherAccount() throws Exception {
			HttpClient visitor = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
			assertThat(send(visitor, "/login").body()).contains("Quiero ser cliente").contains("Ya soy cliente");

			HttpResponse<String> done = signUp(visitor, WEB_DOCUMENT, "margaret");

			assertThat(done.statusCode()).isEqualTo(302);
			String firstAccount = done.headers().firstValue("Location").orElseThrow();
			assertThat(firstAccount).contains("/accounts/").doesNotContain("login");
			HttpResponse<String> detail = send(visitor, path(firstAccount));
			assertThat(detail.statusCode()).isEqualTo(200);
			assertThat(detail.body()).contains("Te damos la bienvenida a Mon-A Bank")
					.containsPattern("ES\\d{2}99990001\\d{12}");

			HttpResponse<String> another = postForm(visitor, "/accounts/another", "initialDeposit=60.00&currency=EUR");
			assertThat(another.statusCode()).isEqualTo(302);
			assertThat(send(visitor, path(another.headers().firstValue("Location").orElseThrow())).body())
					.contains("Nueva cuenta abierta");
			HttpResponse<String> list = send(visitor, "/");
			assertThat(list.statusCode()).isEqualTo(200);
			assertThat(list.body()).contains("Margaret Hamilton").contains("Abrir otra cuenta");

			assertThat(postForm(visitor, "/logout", "").headers().firstValue("Location"))
					.hasValueSatisfying(location -> assertThat(location).endsWith("/login?logout"));
			assertThat(login("margaret", NEW_PASSWORD).headers().firstValue("Location"))
					.hasValueSatisfying(location -> assertThat(location).doesNotContain("error"));
		}

		@Test
		@DisplayName("Por la web, un documento que ya es de un cliente vuelve al paso 1 con «Ya eres cliente»")
		void throughTheWebACustomersDocumentReturnsToStep1() throws Exception {
			signUp(HttpClient.newBuilder().cookieHandler(new CookieManager()).build(), "Z7654321H", "dorothy");
			HttpClient again = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

			HttpResponse<String> duplicate = signUp(again, "Z7654321H", "dorothy2");

			assertThat(duplicate.statusCode()).isEqualTo(302);
			assertThat(duplicate.headers().firstValue("Location"))
					.hasValueSatisfying(location -> assertThat(location).endsWith("/signup"));
			assertThat(send(again, "/signup").body()).contains("Ya eres cliente de Mon-A Bank");
			assertThat(login("dorothy2", NEW_PASSWORD).headers().firstValue("Location"))
					.as("el alta rechazada no crea el usuario")
					.hasValueSatisfying(location -> assertThat(location).contains("error"));
		}

		@Test
		@DisplayName("Por la API: alta pública, el cliente ve su cuenta, transfiere y no puede darse de alta dos veces")
		void throughTheApiThePublicSignUpCreatesCustomerAccountAndUser() throws Exception {
			HttpResponse<String> created = publicSignUp(REST_DOCUMENT, "katherine.johnson@example.com", "katherine");

			assertThat(created.statusCode()).isEqualTo(201);
			String location = created.headers().firstValue("Location").orElseThrow();
			assertThat(location).startsWith("/api/accounts/");
			HttpResponse<String> mine = bearer("katherine", "GET", "/api/accounts", null);
			assertThat(mine.statusCode()).isEqualTo(200);
			assertThat(mine.body()).contains("Katherine Johnson").containsPattern("ES\\d{2}99990001\\d{12}");

			String target = openAccount();
			HttpResponse<String> transfer = bearer("katherine", "POST", "/api/transfers", """
					{"sourceAccountId": "%s", "targetAccountNumber": "%s", "amount": 25}"""
					.formatted(location.substring(location.lastIndexOf('/') + 1), ibanOf(target)));
			assertThat(transfer.statusCode()).isEqualTo(201);
			assertThat(transfer.body()).contains("\"status\":\"APPROVED\"");

			HttpResponse<String> duplicate = publicSignUp(REST_DOCUMENT, "otra.direccion@example.com", "katherine2");
			assertThat(duplicate.statusCode()).isEqualTo(422);
			assertThat(duplicate.body()).contains("Ya eres cliente de Mon-A Bank");
		}

		@Test
		@DisplayName("Por la API, el administrador abre otra cuenta a un cliente existente indicando solo su documento")
		void throughTheApiTheAdministratorOpensAnotherAccountForAnExistingCustomerWithOnlyTheirDocument()
				throws Exception {
			openAccount();

			HttpResponse<String> second = post("/api/accounts", "application/json", """
					{"holderTaxId": "12345678z", "initialDeposit": 75.00}""");

			assertThat(second.statusCode()).isEqualTo(201);
			assertThat(second.body()).contains("\"holderName\":\"Ada Lovelace\"");
			String detail = get(second.headers().firstValue("Location").orElseThrow(), "application/json").body();
			assertThat(detail).contains("\"holderTaxId\":\"12345678Z\"").contains("\"holderEmail\":\"ada@example.com\"");
		}

		/** Recorre los tres pasos del alta con un navegador y devuelve la respuesta del envío final. */
		private HttpResponse<String> signUp(HttpClient client, String document, String username) throws Exception {
			assertThat(postForm(client, "/signup", "fullName=Margaret+Hamilton&taxId=%s&email=%s%%40example.com"
					.formatted(document, username)).headers().firstValue("Location"))
					.hasValueSatisfying(location -> assertThat(location).endsWith("/signup/access"));
			assertThat(postForm(client, "/signup/access", "username=%s&password=%s&passwordRepetition=%s"
					.formatted(username, NEW_PASSWORD, NEW_PASSWORD)).headers().firstValue("Location"))
					.hasValueSatisfying(location -> assertThat(location).endsWith("/signup/account"));
			return postForm(client, "/signup/account", "initialDeposit=100.00&currency=EUR&conditionsAccepted=true");
		}

		/** Envía un formulario con el token CSRF de la página que lo muestra (o de la de login, para salir). */
		private HttpResponse<String> postForm(HttpClient client, String path, String form) throws Exception {
			String page = path.equals("/logout") ? "/" : path;
			java.util.regex.Matcher csrf = java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"")
					.matcher(send(client, page).body());
			assertThat(csrf.find()).as("la página %s lleva un token CSRF", page).isTrue();
			String body = (form.isEmpty() ? "" : form + "&") + "_csrf=" + csrf.group(1);
			return client.send(HttpRequest.newBuilder(URI.create(url(path)))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body))
					.build(), HttpResponse.BodyHandlers.ofString());
		}

		private HttpResponse<String> login(String username, String password) throws Exception {
			HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
			java.util.regex.Matcher csrf = java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"")
					.matcher(send(client, "/login?form").body());
			assertThat(csrf.find()).isTrue();
			return client.send(HttpRequest.newBuilder(URI.create(url("/login")))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString("username=%s&password=%s&_csrf=%s"
							.formatted(username, password, csrf.group(1))))
					.build(), HttpResponse.BodyHandlers.ofString());
		}

		private HttpResponse<String> publicSignUp(String document, String email, String username) throws Exception {
			return http.send(HttpRequest.newBuilder(URI.create(url("/api/customers")))
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString("""
							{"fullName": "Katherine Johnson", "taxId": "%s", "email": "%s", "username": "%s",
							 "password": "%s", "initialDeposit": 100.00}""".formatted(document, email, username, NEW_PASSWORD)))
					.build(), HttpResponse.BodyHandlers.ofString());
		}

		private HttpResponse<String> bearer(String username, String method, String path, String json) throws Exception {
			HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url(path)))
					.header("Authorization", ApiTokens.bearer(http, url(""), username, NEW_PASSWORD))
					.header("Accept", "application/json");
			request = json == null ? request.GET() : request.header("Content-Type", "application/json")
					.method(method, HttpRequest.BodyPublishers.ofString(json));
			return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}

		private HttpResponse<String> send(HttpClient client, String path) throws Exception {
			return client.send(HttpRequest.newBuilder(URI.create(url(path))).header("Accept", "text/html").GET().build(),
					HttpResponse.BodyHandlers.ofString());
		}

		/** Ruta de una cabecera Location, absoluta o relativa. */
		private String path(String location) {
			return location.replaceFirst("^https?://[^/]+", "");
		}
	}

	/** Una petición que anuncia multipart sin delimitador no debe llegar a un 500 (#113). */
	@Nested
	@DisplayName("Peticiones multipart sin delimitador (#113)")
	class MultipartWithoutBoundary {

		private static final String MULTIPART = "multipart/form-data";

		@Test
		@DisplayName("Un GET de la API con Content-Type multipart sin delimitador responde 200")
		void anApiGetWithMultipartWithoutBoundaryResponds200() throws Exception {
			assertThat(multipart("GET", "/api/accounts", null, "application/json").statusCode()).isEqualTo(200);
		}

		@Test
		@DisplayName("Un POST de la API con Content-Type multipart sin delimitador responde 415 ProblemDetail")
		void anApiPostWithMultipartWithoutBoundaryResponds415() throws Exception {
			String deposits = openAccount().replaceFirst("^https?://[^/]+", "") + "/deposits";

			HttpResponse<String> response = multipart("POST", deposits, "{\"amount\": 10.00}", "application/json");

			assertThat(response.statusCode()).isEqualTo(415);
			assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
					type -> assertThat(type).startsWith("application/problem+json"));
		}

		@Test
		@DisplayName("Una página de la web con Content-Type multipart sin delimitador responde 200")
		void aWebPageWithMultipartWithoutBoundaryResponds200() throws Exception {
			HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url("/login")))
					.header("Content-Type", MULTIPART).header("Accept", "text/html")
					.method("GET", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(200);
		}

		private HttpResponse<String> multipart(String method, String path, String body, String accept) throws Exception {
			return http.send(HttpRequest.newBuilder(URI.create(url(path)))
					.header("Authorization", ApiTokens.bearer(http, url(""), "admin", PASSWORD))
					.header("Content-Type", MULTIPART)
					.header("Accept", accept)
					.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
					.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

	/** La documentación de la API sale del contrato escrito a mano (#111). */
	@Nested
	@DisplayName("Documentación de la API (#111)")
	class ApiDocumentation {

		@Test
		@DisplayName("El contrato OpenAPI se sirve sin credenciales en /openapi.yaml")
		void theOpenApiContractIsServedWithoutCredentials() throws Exception {
			HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url("/openapi.yaml"))).GET().build(),
					HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).startsWith("openapi: 3.1.0");
		}

		@Test
		@DisplayName("/api-docs lleva sin credenciales hasta la página de Swagger UI")
		void apiDocsLeadsToThePageWithoutCredentials() throws Exception {
			HttpClient following = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

			HttpResponse<String> response = following.send(HttpRequest.newBuilder(URI.create(url("/api-docs"))).GET().build(),
					HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.uri().getPath()).isEqualTo("/api-docs/index.html");
			assertThat(response.body()).contains("SwaggerUIBundle");
		}

		@Test
		@DisplayName("La página de Swagger UI se sirve sin credenciales y carga el contrato escrito a mano")
		void theSwaggerUiPageIsServedWithoutCredentials() throws Exception {
			HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url("/api-docs/index.html"))).GET().build(),
					HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).contains("SwaggerUIBundle", "url: '/openapi.yaml'", "integrity=\"sha384-");
		}
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

	/** Con cookies: el mensaje flash tras la redirección viaja en la sesión. */
	private final HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

	/** Abre una cuenta minorista con 100,00 € y devuelve su ruta de la API ({@code /api/accounts/{id}}). */
	private String openAccount() throws Exception {
		HttpResponse<String> created = post("/api/accounts", "application/json", """
				{"holderName": "Ada Lovelace", "holderTaxId": "12345678Z", "holderEmail": "ada@example.com",
				 "initialDeposit": 100.00, "ownerUsername": "ada"}""");
		assertThat(created.statusCode()).isEqualTo(201);
		return created.headers().firstValue("Location").orElseThrow();
	}

	/** El IBAN generado de una cuenta, leído de su detalle de la API ({@code /api/accounts/{id}}). */
	private String ibanOf(String apiPath) throws Exception {
		Matcher matcher = Pattern.compile("\"accountNumber\":\"(ES\\d{22})\"").matcher(get(apiPath, "application/json").body());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private String webPath(String apiPath) {
		return apiPath.replaceFirst("^/api", "");
	}

	private boolean loggedIn;

	/** Entra como administrador una sola vez por test: GET /login para leer el token CSRF y POST /login con cookies. */
	private void ensureLoggedIn() throws Exception {
		if (loggedIn) {
			return;
		}
		HttpResponse<String> login = browser.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
						.header("Content-Type", "application/x-www-form-urlencoded")
						.POST(HttpRequest.BodyPublishers.ofString("username=admin&password=%s&_csrf=%s"
								.formatted(PASSWORD, csrfToken())))
						.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(login.statusCode()).isEqualTo(302);
		assertThat(login.headers().firstValue("Location"))
				.hasValueSatisfying(location -> assertThat(location).doesNotContain("error"));
		loggedIn = true;
	}

	/** Token CSRF: la página de login lo lleva siempre, también con la sesión iniciada. */
	private String csrfToken() throws Exception {
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"")
				.matcher(rawBrowse("/login?form").body());
		assertThat(matcher.find()).as("la página de login lleva un token CSRF").isTrue();
		return matcher.group(1);
	}

	private HttpResponse<String> rawBrowse(String path) throws Exception {
		return browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Accept", "text/html")
				.GET()
				.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** Envía un formulario web como el administrador con sesión, añadiendo el token CSRF. */
	private HttpResponse<String> submit(String path, String form) throws Exception {
		ensureLoggedIn();
		return browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form + "&_csrf=" + csrfToken()))
				.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** Pide una página web como el administrador con sesión. */
	private HttpResponse<String> page(String path) throws Exception {
		ensureLoggedIn();
		return rawBrowse(path);
	}

	private String browse(String path) throws Exception {
		return page(path).body();
	}

	/** Token Bearer del administrador, que la API exige (#122); la web no lo usa. */
	private HttpRequest.Builder withApiCredentials(HttpRequest.Builder request, String path) throws Exception {
		return path.startsWith("/api")
				? request.header("Authorization", ApiTokens.bearer(http, "http://localhost:" + port, "admin", PASSWORD))
				: request;
	}

	private HttpResponse<String> get(String path, String accept) throws Exception {
		HttpRequest request = withApiCredentials(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)), path)
				.header("Accept", accept)
				.GET()
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> post(String path, String contentType, String body) throws Exception {
		HttpRequest request = withApiCredentials(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)), path)
				.header("Content-Type", contentType)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
