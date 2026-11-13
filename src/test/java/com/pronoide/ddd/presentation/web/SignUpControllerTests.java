package com.pronoide.ddd.presentation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.pronoide.ddd.application.AccountDetails;
import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.Caller;
import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.application.SignUpCommand;
import com.pronoide.ddd.domain.model.CurrencyMismatchException;
import com.pronoide.ddd.domain.model.DuplicateCustomerException;
import com.pronoide.ddd.domain.model.InsufficientInitialDepositException;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.TaxId;
import com.pronoide.ddd.presentation.CustomerRegistration;
import com.pronoide.ddd.presentation.SecuritySupport;
import com.pronoide.ddd.presentation.UsernameTakenException;

/** Alta de cliente en tres pasos (#95): datos personales, acceso y cuenta. */
@WebMvcTest({SignUpController.class, AccountWebController.class})
@Import({WebFormat.class, WebSecurityConfig.class, SecuritySupport.class})
class SignUpControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerRegistration registration;

    @MockitoBean
    private CheckingAccountService service;

    /** POST con token CSRF: la cadena web lo exige para las rutas públicas del alta. */
    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url).with(csrf());
    }

    private static MockHttpServletRequestBuilder validStep1(MockHttpServletRequestBuilder request) {
        return request.param("fullName", "Margaret Hamilton").param("taxId", "12345678Z")
                .param("email", "margaret@example.com");
    }

    @Nested
    @DisplayName("Paso 1: datos personales")
    class Paso1 {

        @Test
        @DisplayName("La página es pública, lleva el token CSRF y los tres campos")
        void la_pagina_es_publica_lleva_el_token_csrf_y_los_tres_campos() throws Exception {
            mvc.perform(get("/signup"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/data"))
                    .andExpect(content().string(containsString("name=\"_csrf\"")))
                    .andExpect(content().string(containsString("name=\"fullName\"")))
                    .andExpect(content().string(containsString("name=\"taxId\"")))
                    .andExpect(content().string(containsString("name=\"email\"")))
                    .andExpect(content().string(containsString("Paso 1 de 3")));
        }

        @Test
        @DisplayName("Sin enviar nada, los errores de campo se quedan en el paso 1")
        void sin_enviar_nada_los_errores_de_campo_se_quedan_en_el_paso_1() throws Exception {
            mvc.perform(post("/signup"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/data"))
                    .andExpect(content().string(containsString("El nombre y apellidos son obligatorios")))
                    .andExpect(content().string(containsString("field-error")));
        }

        @Test
        @DisplayName("Un documento o un correo mal formados se quedan en el paso 1 con su mensaje")
        void un_documento_o_un_correo_mal_formados_se_quedan_en_el_paso_1() throws Exception {
            mvc.perform(post("/signup").param("fullName", "Margaret").param("taxId", "abc")
                            .param("email", "sin-arroba"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/data"))
                    .andExpect(content().string(containsString(
                            com.pronoide.ddd.application.OpenAccountCommandDef.TAX_ID_MESSAGE)))
                    .andExpect(content().string(containsString("El correo electrónico no es válido")));
        }

        @Test
        @DisplayName("Con datos válidos redirige al paso 2")
        void con_datos_validos_redirige_al_paso_2() throws Exception {
            mvc.perform(validStep1(post("/signup")))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/signup/access"));
        }

        @Test
        @DisplayName("Un POST sin token CSRF se rechaza con 403")
        void un_post_sin_token_csrf_se_rechaza_con_403() throws Exception {
            mvc.perform(MockMvcRequestBuilders.post("/signup").param("fullName", "Margaret"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Volver al paso 1 conserva los datos ya escritos")
        void volver_al_paso_1_conserva_los_datos_ya_escritos() throws Exception {
            MockHttpSession session = new MockHttpSession();
            mvc.perform(validStep1(post("/signup")).session(session))
                    .andExpect(redirectedUrl("/signup/access"));

            mvc.perform(get("/signup").session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("value=\"Margaret Hamilton\"")))
                    .andExpect(content().string(containsString("value=\"12345678Z\"")))
                    .andExpect(content().string(containsString("value=\"margaret@example.com\"")))
                    .andExpect(content().string(not(containsString("field-error"))));
        }
    }

    /** Sesión con el paso 1 ya completado. */
    private MockHttpSession sessionAfterStep1() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(validStep1(post("/signup")).session(session)).andExpect(redirectedUrl("/signup/access"));
        return session;
    }

    private static MockHttpServletRequestBuilder validStep2(MockHttpServletRequestBuilder request) {
        return request.param("username", "margaret").param("password", "clave-secreta-1")
                .param("passwordRepetition", "clave-secreta-1");
    }

    @Nested
    @DisplayName("Paso 2: acceso")
    class Paso2 {

        @Test
        @DisplayName("Entrar en el paso 2 sin haber hecho el 1 redirige a /signup")
        void entrar_en_el_paso_2_sin_haber_hecho_el_1_redirige_a_signup() throws Exception {
            mvc.perform(get("/signup/access"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/signup"));
            mvc.perform(validStep2(post("/signup/access")))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/signup"));
        }

        @Test
        @DisplayName("Tras el paso 1, la página pide usuario, contraseña y repetición, y deja volver atrás")
        void tras_el_paso_1_la_pagina_pide_usuario_contrasena_y_repeticion() throws Exception {
            mvc.perform(get("/signup/access").session(sessionAfterStep1()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/access"))
                    .andExpect(content().string(containsString("name=\"_csrf\"")))
                    .andExpect(content().string(containsString("name=\"username\"")))
                    .andExpect(content().string(containsString("name=\"password\"")))
                    .andExpect(content().string(containsString("name=\"passwordRepetition\"")))
                    .andExpect(content().string(containsString("Paso 2 de 3")))
                    .andExpect(content().string(containsString("href=\"/signup\"")));
        }

        @Test
        @DisplayName("Un usuario mal formado, una contraseña corta o una repetición distinta se quedan en el paso 2")
        void los_errores_de_campo_se_quedan_en_el_paso_2() throws Exception {
            MockHttpSession session = sessionAfterStep1();

            mvc.perform(post("/signup/access").session(session).param("username", "Ab")
                            .param("password", "corta").param("passwordRepetition", "corta"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/access"))
                    .andExpect(content().string(containsString("El usuario debe tener de 3 a 30")))
                    .andExpect(content().string(containsString("al menos 8 caracteres")));

            mvc.perform(post("/signup/access").session(session).param("username", "margaret")
                            .param("password", "clave-secreta-1").param("passwordRepetition", "otra-clave-22"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/access"))
                    .andExpect(content().string(containsString("Las contraseñas no coinciden")));
        }

        @Test
        @DisplayName("Con datos válidos redirige al paso 3")
        void con_datos_validos_redirige_al_paso_3() throws Exception {
            mvc.perform(validStep2(post("/signup/access")).session(sessionAfterStep1()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/signup/account"));
        }

        @Test
        @DisplayName("La contraseña no aparece nunca en el HTML, ni tras un error ni al volver al paso 2")
        void la_contrasena_no_aparece_nunca_en_el_html() throws Exception {
            MockHttpSession session = sessionAfterStep1();

            mvc.perform(post("/signup/access").session(session).param("username", "margaret")
                            .param("password", "clave-secreta-1").param("passwordRepetition", "otra-clave-22"))
                    .andExpect(content().string(not(containsString("clave-secreta-1"))))
                    .andExpect(content().string(not(containsString("otra-clave-22"))));

            mvc.perform(validStep2(post("/signup/access")).session(session))
                    .andExpect(redirectedUrl("/signup/account"));
            mvc.perform(get("/signup/access").session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("value=\"margaret\"")))
                    .andExpect(content().string(not(containsString("clave-secreta-1"))));
        }

        @Test
        @DisplayName("Volver al paso 1 desde el 2 conserva los datos personales")
        void volver_al_paso_1_desde_el_2_conserva_los_datos_personales() throws Exception {
            MockHttpSession session = sessionAfterStep1();
            mvc.perform(get("/signup").session(session))
                    .andExpect(content().string(containsString("value=\"Margaret Hamilton\"")));
        }
    }

    /** Sesión con los pasos 1 y 2 completados. */
    private MockHttpSession sessionAfterStep2() throws Exception {
        MockHttpSession session = sessionAfterStep1();
        mvc.perform(validStep2(post("/signup/access")).session(session)).andExpect(redirectedUrl("/signup/account"));
        return session;
    }

    private static MockHttpServletRequestBuilder validStep3(MockHttpServletRequestBuilder request) {
        return request.param("initialDeposit", "100.00").param("currency", "EUR").param("conditionsAccepted", "true");
    }

    private static AccountSummary openedAccount(UUID id) {
        return AccountSummary.builder().id(id).accountNumber("ES2199990001220000000001")
                .holderName("Margaret Hamilton").balance(new BigDecimal("100.00")).currency("EUR").build();
    }

    @Nested
    @DisplayName("Paso 3: cuenta y confirmación")
    class Paso3 {

        @Test
        @DisplayName("Sin haber completado los pasos anteriores, el paso 3 redirige a /signup")
        void sin_haber_completado_los_pasos_anteriores_el_paso_3_redirige_a_signup() throws Exception {
            mvc.perform(get("/signup/account"))
                    .andExpect(redirectedUrl("/signup"));
            mvc.perform(get("/signup/account").session(sessionAfterStep1()))
                    .andExpect(redirectedUrl("/signup"));
            mvc.perform(validStep3(post("/signup/account")).session(sessionAfterStep1()))
                    .andExpect(redirectedUrl("/signup"));
            verifyNoInteractions(registration);
        }

        @Test
        @DisplayName("La página muestra el resumen, pide el depósito y la aceptación, y no pinta la contraseña")
        void la_pagina_muestra_el_resumen_pide_el_deposito_y_la_aceptacion() throws Exception {
            mvc.perform(get("/signup/account").session(sessionAfterStep2()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/account"))
                    .andExpect(content().string(containsString("Paso 3 de 3")))
                    .andExpect(content().string(containsString("Margaret Hamilton")))
                    .andExpect(content().string(containsString("12345678Z")))
                    .andExpect(content().string(containsString("margaret@example.com")))
                    .andExpect(content().string(containsString("margaret")))
                    .andExpect(content().string(containsString("name=\"initialDeposit\"")))
                    .andExpect(content().string(containsString("name=\"conditionsAccepted\"")))
                    .andExpect(content().string(containsString("Abrir mi cuenta")))
                    .andExpect(content().string(containsString("name=\"_csrf\"")))
                    .andExpect(content().string(not(containsString("clave-secreta-1"))));
        }

        @Test
        @DisplayName("Sin depósito ni aceptación, los errores se quedan en el paso 3 y no se llama al alta")
        void sin_deposito_ni_aceptacion_los_errores_se_quedan_en_el_paso_3() throws Exception {
            mvc.perform(post("/signup/account").session(sessionAfterStep2()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/account"))
                    .andExpect(content().string(containsString("El depósito inicial es obligatorio")))
                    .andExpect(content().string(containsString("Debes aceptar las condiciones")));
            verifyNoInteractions(registration);
        }

        @Test
        @DisplayName("Un depósito no positivo se queda en el paso 3")
        void un_deposito_no_positivo_se_queda_en_el_paso_3() throws Exception {
            mvc.perform(post("/signup/account").session(sessionAfterStep2()).param("initialDeposit", "0")
                            .param("conditionsAccepted", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("El depósito inicial debe ser positivo")));
            verifyNoInteractions(registration);
        }

        @Test
        @DisplayName("Con todo correcto da de alta con los datos de los tres pasos y redirige a la cuenta con la bienvenida")
        void con_todo_correcto_da_de_alta_y_redirige_a_la_cuenta_con_la_bienvenida() throws Exception {
            UUID id = UUID.randomUUID();
            when(registration.register(any(SignUpCommand.class), eq("clave-secreta-1"))).thenReturn(openedAccount(id));

            mvc.perform(validStep3(post("/signup/account")).session(sessionAfterStep2()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + id))
                    .andExpect(flash().attribute("message",
                            "Te damos la bienvenida a Mon-A Bank. Tu IBAN es ES2199990001220000000001"));

            ArgumentCaptor<SignUpCommand> command = ArgumentCaptor.forClass(SignUpCommand.class);
            verify(registration).register(command.capture(), eq("clave-secreta-1"));
            assertThat(command.getValue().fullName()).isEqualTo("Margaret Hamilton");
            assertThat(command.getValue().taxId()).isEqualTo("12345678Z");
            assertThat(command.getValue().email()).isEqualTo("margaret@example.com");
            assertThat(command.getValue().username()).isEqualTo("margaret");
            assertThat(command.getValue().initialDeposit()).isEqualByComparingTo("100.00");
            assertThat(command.getValue().currency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Un cliente que ya existe vuelve al paso 1 con el mensaje y el enlace para entrar")
        void un_cliente_que_ya_existe_vuelve_al_paso_1_con_el_mensaje_y_el_enlace_para_entrar() throws Exception {
            var error = new DuplicateCustomerException(new TaxId("12345678Z"));
            when(registration.register(any(SignUpCommand.class), any())).thenThrow(error);
            MockHttpSession session = sessionAfterStep2();

            mvc.perform(validStep3(post("/signup/account")).session(session))
                    .andExpect(redirectedUrl("/signup"))
                    .andExpect(flash().attribute("signUpError", error.getMessage()))
                    .andExpect(flash().attribute("duplicateCustomer", true));

            mvc.perform(get("/signup").session(session).flashAttr("signUpError", error.getMessage())
                            .flashAttr("duplicateCustomer", true))
                    .andExpect(content().string(containsString("Ya eres cliente de Mon-A Bank")))
                    .andExpect(content().string(containsString("href=\"/login?form\"")));
        }

        @Test
        @DisplayName("Un usuario ocupado vuelve al paso 2 con el mensaje")
        void un_usuario_ocupado_vuelve_al_paso_2_con_el_mensaje() throws Exception {
            var error = new UsernameTakenException("margaret");
            when(registration.register(any(SignUpCommand.class), any())).thenThrow(error);
            MockHttpSession session = sessionAfterStep2();

            mvc.perform(validStep3(post("/signup/account")).session(session))
                    .andExpect(redirectedUrl("/signup/access"))
                    .andExpect(flash().attribute("signUpError", error.getMessage()));

            mvc.perform(get("/signup/access").session(session).flashAttr("signUpError", error.getMessage()))
                    .andExpect(content().string(containsString("El usuario margaret ya existe")));
        }

        @Test
        @DisplayName("Un documento con la letra de control mal (IllegalArgumentException) vuelve al paso 1 con el mensaje del dominio")
        void un_documento_con_la_letra_mal_vuelve_al_paso_1_con_el_mensaje_del_dominio() throws Exception {
            when(registration.register(any(SignUpCommand.class), any()))
                    .thenThrow(new IllegalArgumentException("La letra del documento no es correcta"));

            mvc.perform(validStep3(post("/signup/account")).session(sessionAfterStep2()))
                    .andExpect(redirectedUrl("/signup"))
                    .andExpect(flash().attribute("signUpError", "La letra del documento no es correcta"))
                    .andExpect(flash().attribute("duplicateCustomer", false));
        }

        @Test
        @DisplayName("Un depósito insuficiente o una divisa distinta del euro se quedan en el paso 3 con el mensaje del dominio")
        void un_deposito_insuficiente_o_una_divisa_distinta_se_quedan_en_el_paso_3() throws Exception {
            MockHttpSession session = sessionAfterStep2();
            var insufficient = new InsufficientInitialDepositException(
                    MonetaryAmount.euros("10.00"), MonetaryAmount.euros("50.00"));
            when(registration.register(any(SignUpCommand.class), any())).thenThrow(insufficient);

            mvc.perform(post("/signup/account").session(session).param("initialDeposit", "10.00")
                            .param("conditionsAccepted", "true"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/account"))
                    .andExpect(content().string(containsString("no alcanza el mínimo exigido")))
                    .andExpect(content().string(containsString("value=\"10.00\"")));

            when(registration.register(any(SignUpCommand.class), any())).thenThrow(
                    new CurrencyMismatchException(Currency.getInstance("EUR"), Currency.getInstance("USD")));

            mvc.perform(post("/signup/account").session(session).param("initialDeposit", "100.00")
                            .param("currency", "USD").param("conditionsAccepted", "true"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/account"));
        }
    }

    @Nested
    @DisplayName("Sesión iniciada tras el alta")
    class SesionTrasElAlta {

        private AccountDetails details(UUID id) {
            return AccountDetails.builder().id(id).accountNumber("ES2199990001220000000001")
                    .holderName("Margaret Hamilton").segment("RETAIL").balance(new BigDecimal("100.00"))
                    .currency("EUR").build();
        }

        @Test
        @DisplayName("Antes de terminar el alta, la cuenta pide iniciar sesión")
        void antes_de_terminar_el_alta_la_cuenta_pide_iniciar_sesion() throws Exception {
            mvc.perform(get("/accounts/" + UUID.randomUUID()).session(sessionAfterStep2()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }

        @Test
        @DisplayName("Tras el POST final, la misma sesión abre el detalle como el cliente nuevo y ve la bienvenida")
        void tras_el_post_final_la_misma_sesion_abre_el_detalle_como_el_cliente_nuevo() throws Exception {
            UUID id = UUID.randomUUID();
            when(registration.register(any(SignUpCommand.class), any())).thenReturn(openedAccount(id));
            when(service.findAccount(any(Caller.class), eq(id))).thenReturn(details(id));
            when(service.listAccounts(any(Caller.class))).thenReturn(List.of(openedAccount(id)));
            MockHttpSession session = sessionAfterStep2();

            mvc.perform(validStep3(post("/signup/account")).session(session))
                    .andExpect(redirectedUrl("/accounts/" + id));

            mvc.perform(get("/accounts/" + id).session(session))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(content().string(containsString("Te damos la bienvenida a Mon-A Bank. Tu IBAN es ES2199990001220000000001")))
                    .andExpect(content().string(containsString("margaret")));

            ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
            verify(service).findAccount(caller.capture(), eq(id));
            assertThat(caller.getValue().username()).isEqualTo("margaret");
            assertThat(caller.getValue().admin()).isFalse();
        }

        @Test
        @DisplayName("Terminado el alta se borra el asistente: no quedan datos ni contraseña en la sesión")
        void terminado_el_alta_se_borra_el_asistente() throws Exception {
            UUID id = UUID.randomUUID();
            when(registration.register(any(SignUpCommand.class), any())).thenReturn(openedAccount(id));
            MockHttpSession session = sessionAfterStep2();

            mvc.perform(validStep3(post("/signup/account")).session(session))
                    .andExpect(redirectedUrl("/accounts/" + id));

            assertThat(session.getAttribute("signUpForm")).isNull();
        }

        @Test
        @DisplayName("Si el alta falla, la sesión no queda iniciada")
        void si_el_alta_falla_la_sesion_no_queda_iniciada() throws Exception {
            when(registration.register(any(SignUpCommand.class), any()))
                    .thenThrow(new UsernameTakenException("margaret"));
            MockHttpSession session = sessionAfterStep2();

            mvc.perform(validStep3(post("/signup/account")).session(session))
                    .andExpect(redirectedUrl("/signup/access"));

            mvc.perform(get("/accounts/" + UUID.randomUUID()).session(session))
                    .andExpect(redirectedUrl("/login"));
        }
    }
}
