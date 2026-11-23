package com.pronoide.ddd.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.SignUpCommand;
import com.pronoide.ddd.domain.model.CurrencyMismatchException;
import com.pronoide.ddd.domain.model.DuplicateCustomerException;
import com.pronoide.ddd.domain.model.InsufficientInitialDepositException;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.TaxId;
import com.pronoide.ddd.adapter.in.CustomerRegistration;
import com.pronoide.ddd.adapter.in.SecuritySupport;
import com.pronoide.ddd.adapter.in.TestUsers;
import com.pronoide.ddd.adapter.in.TooManySignUpsException;
import com.pronoide.ddd.adapter.in.UsernameTakenException;

/** {@code POST /api/customers}: público, sin credenciales ni CSRF. */
@WithAnonymousUser
@WebMvcTest(CustomerController.class)
@Import({ApiSecurityConfig.class, SecuritySupport.class, TestUsers.class})
class CustomerControllerTests {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String IBAN = "ES6699990001740000000001";
    private static final String VALID_BODY = """
            {"fullName": "Margaret Hamilton", "taxId": "00000000T", "email": "margaret@example.com",
             "username": "margaret", "password": "demo-1234", "initialDeposit": 100.00}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerRegistration registration;

    private static AccountSummary summary() {
        return AccountSummary.builder().id(ID).accountNumber(IBAN).holderName("Margaret Hamilton").segment("RETAIL")
                .balance(new BigDecimal("100.00")).currency("EUR").build();
    }

    private String send(String body) throws Exception {
        return mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Un alta válida sin credenciales responde 201 con Location y el resumen de la cuenta")
    void un_alta_valida_sin_credenciales_responde_201_con_location_y_el_resumen() throws Exception {
        given(registration.register(any(SignUpCommand.class), eq("demo-1234"), any())).willReturn(summary());

        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/accounts/" + ID))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.accountNumber").value(IBAN))
                .andExpect(jsonPath("$.holderName").value("Margaret Hamilton"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("El command lleva los datos de la petición y EUR por defecto; la contraseña va aparte")
    void el_command_lleva_los_datos_de_la_peticion_y_eur_por_defecto() throws Exception {
        given(registration.register(any(SignUpCommand.class), any(), any())).willReturn(summary());

        send(VALID_BODY);

        ArgumentCaptor<SignUpCommand> command = ArgumentCaptor.forClass(SignUpCommand.class);
        verify(registration).register(command.capture(), eq("demo-1234"), any());
        assertThat(command.getValue().fullName()).isEqualTo("Margaret Hamilton");
        assertThat(command.getValue().taxId()).isEqualTo("00000000T");
        assertThat(command.getValue().email()).isEqualTo("margaret@example.com");
        assertThat(command.getValue().username()).isEqualTo("margaret");
        assertThat(command.getValue().initialDeposit()).isEqualByComparingTo("100.00");
        assertThat(command.getValue().currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Un NIE con formato válido se acepta en la petición")
    void un_nie_con_formato_valido_se_acepta() throws Exception {
        given(registration.register(any(SignUpCommand.class), any(), any())).willReturn(summary());

        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("00000000T", "X1234567L")))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            fullName       | ""                       | fullName
            taxId          | "1234567Z"               | taxId
            email          | "margaret"               | email
            username       | "Margaret"               | username
            password       | "corta12"                | password
            currency       | "eur"                    | currency
            """)
    @DisplayName("Un campo no válido responde 400 con el error de ese campo y no llama al caso de uso")
    void un_campo_no_valido_responde_400_con_el_error_del_campo(String field, String value, String errorField)
            throws Exception {
        String body = VALID_BODY.replaceFirst("\"" + field + "\": \"[^\"]*\"", "\"" + field + "\": " + value);
        if (field.equals("currency")) {
            body = VALID_BODY.replace("\"initialDeposit\": 100.00", "\"initialDeposit\": 100.00, \"currency\": " + value);
        }

        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"))
                .andExpect(jsonPath("$.errors." + errorField).exists());
        verifyNoInteractions(registration);
    }

    @Test
    @DisplayName("Con un token Bearer no válido el alta pública responde 401 y no llama al caso de uso")
    void con_un_token_no_valido_el_alta_publica_responde_401() throws Exception {
        mvc.perform(post("/api/customers").header("Authorization", "Bearer no-es-un-jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));
        verifyNoInteractions(registration);
    }

    @Test
    @DisplayName("Un método no declarado en la ruta pública responde 405 sin pedir token")
    void un_metodo_no_declarado_en_la_ruta_publica_responde_405_sin_pedir_token() throws Exception {
        mvc.perform(put("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(registration);
    }

    @ParameterizedTest(name = "depósito inicial {0}")
    @CsvSource({"0.004", "1000000000.00", "1.7976931348623155E+308"})
    @DisplayName("Un depósito inicial que redondea a cero o supera el máximo responde 400 con el error del campo")
    void un_deposito_inicial_fuera_de_los_limites_responde_400(String deposit) throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"initialDeposit\": 100.00", "\"initialDeposit\": " + deposit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"))
                .andExpect(jsonPath("$.errors.initialDeposit").exists());
        verifyNoInteractions(registration);
    }

    @Test
    @DisplayName("Un depósito ausente o no positivo responde 400")
    void un_deposito_ausente_o_no_positivo_responde_400() throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"initialDeposit\": 100.00", "\"initialDeposit\": -5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.initialDeposit").exists());
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace(", \"initialDeposit\": 100.00", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.initialDeposit").exists());
        verifyNoInteractions(registration);
    }

    @Test
    @DisplayName("Un documento con la letra incorrecta responde 400 con el mensaje del dominio")
    void un_documento_con_la_letra_incorrecta_responde_400() throws Exception {
        given(registration.register(any(SignUpCommand.class), any(), any())).willThrow(
                new IllegalArgumentException("La letra de control del documento 00000000A no es correcta"));

        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"))
                .andExpect(jsonPath("$.detail").value("La letra de control del documento 00000000A no es correcta"));
    }

    @Test
    @DisplayName("Un cliente duplicado responde 422 Regla de negocio incumplida")
    void un_cliente_duplicado_responde_422() throws Exception {
        given(registration.register(any(SignUpCommand.class), any(), any()))
                .willThrow(new DuplicateCustomerException(new TaxId("00000000T")));

        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Ya eres cliente de Mon-A Bank")));
    }

    @Test
    @DisplayName("Un usuario ocupado responde 422 Regla de negocio incumplida")
    void un_usuario_ocupado_responde_422() throws Exception {
        given(registration.register(any(SignUpCommand.class), any(), any())).willThrow(new UsernameTakenException("margaret"));

        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"))
                .andExpect(jsonPath("$.detail").value("El usuario margaret ya existe: elige otro"));
    }

    @Test
    @DisplayName("Un depósito insuficiente o en otra divisa responde 422")
    void un_deposito_insuficiente_o_en_otra_divisa_responde_422() throws Exception {
        given(registration.register(any(SignUpCommand.class), any(), any()))
                .willThrow(new InsufficientInitialDepositException(MonetaryAmount.euros("10.00"),
                        MonetaryAmount.euros("50.00")));
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableContent());

        given(registration.register(any(SignUpCommand.class), any(), any()))
                .willThrow(new CurrencyMismatchException(MonetaryAmount.euros("1.00").currency(),
                        java.util.Currency.getInstance("USD")));
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"));
    }

    @Test
    @DisplayName("Superado el límite de altas responde 429 con Retry-After en segundos, redondeado hacia arriba (#131)")
    void superado_el_limite_de_altas_responde_429_con_retry_after() throws Exception {
        given(registration.register(any(SignUpCommand.class), any(), any()))
                .willThrow(new TooManySignUpsException(Duration.ofMillis(90_500)));

        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "91"))
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Demasiadas altas"))
                .andExpect(jsonPath("$.detail").value(
                        "Demasiadas altas de clientes en este momento: inténtalo de nuevo más tarde"));
    }

    @Test
    @DisplayName("A través del proxy de Render y de Cloudflare, el alta cuenta para la IP del cliente y no para la de Cloudflare (#131)")
    void a_traves_de_render_y_cloudflare_el_alta_cuenta_para_la_ip_del_cliente() throws Exception {
        given(registration.register(any(SignUpCommand.class), any(), any())).willReturn(summary());

        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)
                        .header("X-Forwarded-For", "198.51.100.40, 104.22.136.75")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.5");
                            return request;
                        }))
                .andExpect(status().isCreated());

        verify(registration).register(any(SignUpCommand.class), eq("demo-1234"), eq("198.51.100.40"));
    }
}
