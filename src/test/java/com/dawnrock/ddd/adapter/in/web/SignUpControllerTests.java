package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.dawnrock.ddd.application.AccountDetails;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.MoneyMovementCommands;
import com.dawnrock.ddd.application.CheckingAccountQueries;
import com.dawnrock.ddd.application.SignUpCommand;
import com.dawnrock.ddd.domain.model.CurrencyMismatchException;
import com.dawnrock.ddd.domain.model.DuplicateCustomerException;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.domain.model.TaxId;
import com.dawnrock.ddd.adapter.in.CustomerRegistration;
import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;
import com.dawnrock.ddd.adapter.in.SignUpLimit;
import com.dawnrock.ddd.adapter.in.TooManySignUpsException;
import com.dawnrock.ddd.adapter.in.UsernameTakenException;

/** Alta de cliente en tres pasos (#95): datos personales, acceso y cuenta. */
@WebMvcTest({SignUpController.class, AccountDetailController.class})
@Import({WebFormat.class, WebSecurityConfig.class, SecuritySupport.class, TestUsers.class, AccountDetailView.class})
class SignUpControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerRegistration registration;

    @MockitoBean
    private AccountOpeningCommands commands;

    @MockitoBean
    private MoneyMovementCommands movements;

    @MockitoBean
    private CheckingAccountQueries queries;

    /** Sin preparar, nunca bloquea (Mockito devuelve un {@code Optional} vacío). */
    @MockitoBean
    private SignUpLimit signUpLimit;

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
        @DisplayName("El progreso del alta es una barra de Bootstrap accesible con el paso actual (#124)")
        void theSignUpProgressIsAnAccessibleBootstrapBar() throws Exception {
            mvc.perform(get("/signup"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("role=\"progressbar\"")))
                    .andExpect(content().string(containsString("aria-valuemin=\"0\" aria-valuemax=\"3\" aria-valuenow=\"1\"")))
                    // #206: el ancho del paso es una clase de app.css, no un style en línea (CSP)
                    .andExpect(content().string(not(containsString("style=\"width"))))
                    // #126: con nombre de transición de vista, la barra crece de un paso al siguiente
                    .andExpect(content().string(containsString(
                            "<div class=\"progress-bar signup-progress-bar signup-progress-step-1\"")));
        }

        @Test
        @DisplayName("El primer paso va sobre la A de fondo con «Continuar» en turquesa; el logo es el de la cabecera (#185)")
        void theFirstStepSitsOverTheBigAWithTheHeaderLogo() throws Exception {
            mvc.perform(get("/signup"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<div class=\"backdrop\" aria-hidden=\"true\">")))
                    .andExpect(content().string(containsString("navbar-brand")))
                    .andExpect(content().string(not(containsString("monabank-stacked"))))
                    .andExpect(content().string(containsString(
                            "<button class=\"btn-slant btn-slant-rise\" type=\"submit\">Continuar</button>")));
        }

        @Test
        @DisplayName("La página es pública, lleva el token CSRF y los tres campos")
        void thePageIsPublicAndHasTheCsrfTokenAndTheThreeFields() throws Exception {
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
        void submittingNothingKeepsTheFieldErrorsOnStep1() throws Exception {
            mvc.perform(post("/signup"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/data"))
                    .andExpect(content().string(containsString("El nombre y apellidos son obligatorios")))
                    .andExpect(content().string(containsString("invalid-feedback")));
        }

        @Test
        @DisplayName("Un documento o un correo mal formados se quedan en el paso 1 con su mensaje")
        void aMalformedDocumentOrEmailStaysOnStep1() throws Exception {
            mvc.perform(post("/signup").param("fullName", "Margaret").param("taxId", "abc")
                            .param("email", "sin-arroba"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/data"))
                    .andExpect(content().string(containsString(
                            com.dawnrock.ddd.application.OpenAccountCommand.TAX_ID_MESSAGE)))
                    .andExpect(content().string(containsString("El correo electrónico no es válido")));
        }

        @Test
        @DisplayName("Con datos válidos redirige al paso 2")
        void withValidDataItRedirectsToStep2() throws Exception {
            mvc.perform(validStep1(post("/signup")))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/signup/access"));
        }

        @Test
        @DisplayName("Un POST sin token CSRF se rechaza con 403")
        void aPostWithoutCsrfTokenIsRejectedWith403() throws Exception {
            mvc.perform(MockMvcRequestBuilders.post("/signup").param("fullName", "Margaret"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Volver al paso 1 conserva los datos ya escritos")
        void goingBackToStep1KeepsTheDataAlreadyTyped() throws Exception {
            MockHttpSession session = new MockHttpSession();
            mvc.perform(validStep1(post("/signup")).session(session))
                    .andExpect(redirectedUrl("/signup/access"));

            mvc.perform(get("/signup").session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("value=\"Margaret Hamilton\"")))
                    .andExpect(content().string(containsString("value=\"12345678Z\"")))
                    .andExpect(content().string(containsString("value=\"margaret@example.com\"")))
                    .andExpect(content().string(not(containsString("invalid-feedback"))));
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
        void enteringStep2WithoutStep1RedirectsToSignUp() throws Exception {
            mvc.perform(get("/signup/access"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/signup"));
            mvc.perform(validStep2(post("/signup/access")))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/signup"));
        }

        @Test
        @DisplayName("El paso 2 también va sobre la A de fondo, con «Continuar» en turquesa (#185)")
        void step2SitsOverTheBigA() throws Exception {
            mvc.perform(get("/signup/access").session(sessionAfterStep1()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<div class=\"backdrop\" aria-hidden=\"true\">")))
                    .andExpect(content().string(containsString(
                            "<button class=\"btn-slant btn-slant-rise\" type=\"submit\">Continuar</button>")));
        }

        @Test
        @DisplayName("Tras el paso 1, la página pide usuario, contraseña y repetición, y deja volver atrás")
        void afterStep1ThePageAsksForUsernamePasswordAndRepetition() throws Exception {
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
        void fieldErrorsStayOnStep2() throws Exception {
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
        @DisplayName("Una repetición distinta marca como inválida solo la repetición, y una contraseña corta la contraseña (#124)")
        void passwordFieldsWithErrorsAreMarkedInvalid() throws Exception {
            MockHttpSession session = sessionAfterStep1();

            mvc.perform(post("/signup/access").session(session).param("username", "margaret")
                            .param("password", "clave-secreta-1").param("passwordRepetition", "otra-clave-22"))
                    .andExpect(content().string(containsString("<input class=\"form-control is-invalid\" type=\"password\" id=\"passwordRepetition\"")))
                    .andExpect(content().string(containsString("<input class=\"form-control\" type=\"password\" id=\"password\"")));

            mvc.perform(post("/signup/access").session(session).param("username", "margaret")
                            .param("password", "corta").param("passwordRepetition", "corta"))
                    .andExpect(content().string(containsString("<input class=\"form-control is-invalid\" type=\"password\" id=\"password\"")));
        }

        @Test
        @DisplayName("Con datos válidos redirige al paso 3")
        void withValidDataItRedirectsToStep3() throws Exception {
            mvc.perform(validStep2(post("/signup/access")).session(sessionAfterStep1()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/signup/account"));
        }

        @Test
        @DisplayName("La contraseña no aparece nunca en el HTML, ni tras un error ni al volver al paso 2")
        void thePasswordNeverAppearsInTheHtml() throws Exception {
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
        void goingBackToStep1FromStep2KeepsThePersonalData() throws Exception {
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
        return new AccountSummary(id, "ES2199990001220000000001", "Margaret Hamilton", "RETAIL",
                new BigDecimal("100.00"), "EUR");
    }

    @Nested
    @DisplayName("Paso 3: cuenta y confirmación")
    class Paso3 {

        @Test
        @DisplayName("Sin haber completado los pasos anteriores, el paso 3 redirige a /signup")
        void withoutThePreviousStepsStep3RedirectsToSignUp() throws Exception {
            mvc.perform(get("/signup/account"))
                    .andExpect(redirectedUrl("/signup"));
            mvc.perform(get("/signup/account").session(sessionAfterStep1()))
                    .andExpect(redirectedUrl("/signup"));
            mvc.perform(validStep3(post("/signup/account")).session(sessionAfterStep1()))
                    .andExpect(redirectedUrl("/signup"));
            verifyNoInteractions(registration);
        }

        @Test
        @DisplayName("El paso 3 también va sobre la A de fondo, con «Abrir mi cuenta» en turquesa (#185)")
        void step3SitsOverTheBigA() throws Exception {
            mvc.perform(get("/signup/account").session(sessionAfterStep2()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<div class=\"backdrop\" aria-hidden=\"true\">")))
                    .andExpect(content().string(containsString(
                            "<button class=\"btn-slant btn-slant-rise\" type=\"submit\">Abrir mi cuenta</button>")));
        }

        @Test
        @DisplayName("La página muestra el resumen, pide el depósito y la aceptación, y no pinta la contraseña")
        void thePageShowsTheSummaryAndAsksForTheDepositAndTheAcceptance() throws Exception {
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
        void withoutDepositOrAcceptanceTheErrorsStayOnStep3() throws Exception {
            mvc.perform(post("/signup/account").session(sessionAfterStep2()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/account"))
                    .andExpect(content().string(containsString("El depósito inicial es obligatorio")))
                    .andExpect(content().string(containsString("Debes aceptar las condiciones")));
            verifyNoInteractions(registration);
        }

        @Test
        @DisplayName("Un depósito no positivo se queda en el paso 3")
        void aNonPositiveDepositStaysOnStep3() throws Exception {
            mvc.perform(post("/signup/account").session(sessionAfterStep2()).param("initialDeposit", "0")
                            .param("conditionsAccepted", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("El depósito inicial debe ser positivo")));
            verifyNoInteractions(registration);
        }

        @Test
        @DisplayName("Con todo correcto da de alta con los datos de los tres pasos y redirige a la cuenta con la bienvenida")
        void withEverythingRightItSignsUpAndRedirectsToTheAccountWithTheWelcome() throws Exception {
            UUID id = UUID.randomUUID();
            when(registration.register(any(SignUpCommand.class), eq("clave-secreta-1"), any())).thenReturn(openedAccount(id));

            mvc.perform(validStep3(post("/signup/account")).session(sessionAfterStep2()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts/" + id))
                    .andExpect(flash().attribute("message",
                            "Te damos la bienvenida a monA Bank. Tu IBAN es ES2199990001220000000001"));

            ArgumentCaptor<SignUpCommand> command = ArgumentCaptor.forClass(SignUpCommand.class);
            verify(registration).register(command.capture(), eq("clave-secreta-1"), any());
            assertThat(command.getValue().fullName()).isEqualTo("Margaret Hamilton");
            assertThat(command.getValue().taxId()).isEqualTo("12345678Z");
            assertThat(command.getValue().email()).isEqualTo("margaret@example.com");
            assertThat(command.getValue().username()).isEqualTo("margaret");
            assertThat(command.getValue().initialDeposit()).isEqualByComparingTo("100.00");
            assertThat(command.getValue().currency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Un cliente que ya existe vuelve al paso 1 con el mensaje y el enlace para entrar")
        void anExistingCustomerReturnsToStep1WithTheMessageAndTheLoginLink() throws Exception {
            var error = new DuplicateCustomerException(new TaxId("12345678Z"));
            when(registration.register(any(SignUpCommand.class), any(), any())).thenThrow(error);
            MockHttpSession session = sessionAfterStep2();

            mvc.perform(validStep3(post("/signup/account")).session(session))
                    .andExpect(redirectedUrl("/signup"))
                    .andExpect(flash().attribute("signUpError", error.getMessage()))
                    .andExpect(flash().attribute("duplicateCustomer", true));

            mvc.perform(get("/signup").session(session).flashAttr("signUpError", error.getMessage())
                            .flashAttr("duplicateCustomer", true))
                    .andExpect(content().string(containsString("Ya eres cliente de monA Bank")))
                    .andExpect(content().string(containsString("href=\"/login?form\"")));
        }

        @Test
        @DisplayName("Un usuario ocupado vuelve al paso 2 con el mensaje")
        void aTakenUsernameReturnsToStep2WithTheMessage() throws Exception {
            var error = new UsernameTakenException("margaret");
            when(registration.register(any(SignUpCommand.class), any(), any())).thenThrow(error);
            MockHttpSession session = sessionAfterStep2();

            mvc.perform(validStep3(post("/signup/account")).session(session))
                    .andExpect(redirectedUrl("/signup/access"))
                    .andExpect(flash().attribute("signUpError", error.getMessage()));

            mvc.perform(get("/signup/access").session(session).flashAttr("signUpError", error.getMessage()))
                    .andExpect(content().string(containsString("El usuario margaret ya existe")));
        }

        @Test
        @DisplayName("Un documento con la letra de control mal (IllegalArgumentException) vuelve al paso 1 con el mensaje del dominio")
        void aDocumentWithTheWrongLetterReturnsToStep1WithTheDomainMessage() throws Exception {
            when(registration.register(any(SignUpCommand.class), any(), any()))
                    .thenThrow(new IllegalArgumentException("La letra del documento no es correcta"));

            mvc.perform(validStep3(post("/signup/account")).session(sessionAfterStep2()))
                    .andExpect(redirectedUrl("/signup"))
                    .andExpect(flash().attribute("signUpError", "La letra del documento no es correcta"))
                    .andExpect(flash().attribute("duplicateCustomer", false));
        }

        @Test
        @DisplayName("Un depósito insuficiente o una divisa distinta del euro se quedan en el paso 3 con el mensaje del dominio")
        void anInsufficientDepositOrAnotherCurrencyStayOnStep3() throws Exception {
            MockHttpSession session = sessionAfterStep2();
            var insufficient = new InsufficientInitialDepositException(
                    MonetaryAmount.euros("10.00"), MonetaryAmount.euros("50.00"));
            when(registration.register(any(SignUpCommand.class), any(), any())).thenThrow(insufficient);

            mvc.perform(post("/signup/account").session(session).param("initialDeposit", "10.00")
                            .param("conditionsAccepted", "true"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/account"))
                    .andExpect(content().string(containsString("no alcanza el mínimo exigido")))
                    .andExpect(content().string(containsString("value=\"10.00\"")));

            when(registration.register(any(SignUpCommand.class), any(), any())).thenThrow(
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
            return AccountDetails.of(id, "ES2199990001220000000001", "Margaret Hamilton", "RETAIL",
                    new BigDecimal("100.00"), "EUR");
        }

        @Test
        @DisplayName("Antes de terminar el alta, la cuenta pide iniciar sesión")
        void beforeFinishingTheSignUpTheAccountAsksToLogIn() throws Exception {
            mvc.perform(get("/accounts/" + UUID.randomUUID()).session(sessionAfterStep2()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }

        @Test
        @DisplayName("Tras el POST final, la misma sesión abre el detalle como el cliente nuevo y ve la bienvenida")
        void afterTheFinalPostTheSameSessionOpensTheDetailsAsTheNewCustomer() throws Exception {
            UUID id = UUID.randomUUID();
            when(registration.register(any(SignUpCommand.class), any(), any())).thenReturn(openedAccount(id));
            when(queries.findAccount(any(Caller.class), eq(id))).thenReturn(details(id));
            when(queries.listAccounts(any(Caller.class))).thenReturn(List.of(openedAccount(id)));
            MockHttpSession session = sessionAfterStep2();

            mvc.perform(validStep3(post("/signup/account")).session(session))
                    .andExpect(redirectedUrl("/accounts/" + id));

            mvc.perform(get("/accounts/" + id).session(session))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(content().string(containsString("Te damos la bienvenida a monA Bank. Tu IBAN es ES2199990001220000000001")))
                    .andExpect(content().string(containsString("margaret")));

            ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
            verify(queries).findAccount(caller.capture(), eq(id));
            assertThat(caller.getValue().username()).isEqualTo("margaret");
            assertThat(caller.getValue().admin()).isFalse();
        }

        @Test
        @DisplayName("Terminado el alta se borra el asistente: no quedan datos ni contraseña en la sesión")
        void onceSignedUpTheWizardIsCleared() throws Exception {
            UUID id = UUID.randomUUID();
            when(registration.register(any(SignUpCommand.class), any(), any())).thenReturn(openedAccount(id));
            MockHttpSession session = sessionAfterStep2();

            mvc.perform(validStep3(post("/signup/account")).session(session))
                    .andExpect(redirectedUrl("/accounts/" + id));

            assertThat(session.getAttribute("signUpForm")).isNull();
        }

        @Test
        @DisplayName("Si el alta falla, la sesión no queda iniciada")
        void whenTheSignUpFailsTheSessionIsNotLoggedIn() throws Exception {
            when(registration.register(any(SignUpCommand.class), any(), any()))
                    .thenThrow(new UsernameTakenException("margaret"));
            MockHttpSession session = sessionAfterStep2();

            mvc.perform(validStep3(post("/signup/account")).session(session))
                    .andExpect(redirectedUrl("/signup/access"));

            mvc.perform(get("/accounts/" + UUID.randomUUID()).session(session))
                    .andExpect(redirectedUrl("/login"));
        }
    }

    @Nested
    @DisplayName("Límite de altas (#131, ADR 0018)")
    class LimiteDeAltas {

        @Test
        @DisplayName("Superado el límite, el paso 3 se queda con el aviso y los minutos que faltan, sin iniciar sesión")
        void overTheLimitStep3StaysWithTheNotice() throws Exception {
            when(registration.register(any(SignUpCommand.class), any(), any()))
                    .thenThrow(new TooManySignUpsException(Duration.ofSeconds(25 * 60 + 1)));
            MockHttpSession session = sessionAfterStep2();

            mvc.perform(validStep3(post("/signup/account")).session(session))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/account"))
                    .andExpect(content().string(containsString(
                            "Demasiadas altas de clientes en este momento. Inténtalo de nuevo dentro de 26 minutos.")))
                    .andExpect(content().string(containsString("Margaret Hamilton")));

            assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
            mvc.perform(get("/signup/account").session(session))
                    .andExpect(view().name("signup/account"));
        }

        @Test
        @DisplayName("El alta de la web cuenta para la IP del cliente detrás de Render y Cloudflare")
        void theWebSignUpCountsForTheClientIp() throws Exception {
            when(registration.register(any(SignUpCommand.class), any(), any())).thenReturn(openedAccount(UUID.randomUUID()));

            mvc.perform(validStep3(post("/signup/account")).session(sessionAfterStep2())
                            .header("X-Forwarded-For", "198.51.100.40, 104.22.136.75")
                            .with(request -> {
                                request.setRemoteAddr("10.0.0.5");
                                return request;
                            }))
                    .andExpect(status().is3xxRedirection());

            verify(registration).register(any(SignUpCommand.class), any(), eq("198.51.100.40"));
        }

        @Test
        @DisplayName("Con el límite lleno, el paso 1 ya muestra el aviso al entrar")
        void withTheLimitFullStep1ShowsTheNoticeOnEntry() throws Exception {
            when(signUpLimit.lockedFor("198.51.100.40")).thenReturn(Optional.of(Duration.ofMinutes(10)));

            mvc.perform(get("/signup").with(from("198.51.100.40")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "Demasiadas altas de clientes en este momento. Inténtalo de nuevo dentro de 10 minutos.")));
            mvc.perform(get("/signup").with(from("198.51.100.41")))
                    .andExpect(content().string(not(containsString("Demasiadas altas"))));
        }

        @Test
        @DisplayName("Con el límite lleno, el paso 1 no deja seguir: el paso 2 vuelve al 1 y no se reserva nada")
        void withTheLimitFullStep1DoesNotLetYouContinue() throws Exception {
            when(signUpLimit.lockedFor("198.51.100.40")).thenReturn(Optional.of(Duration.ofSeconds(30)));
            MockHttpSession session = new MockHttpSession();

            mvc.perform(validStep1(post("/signup")).session(session).with(from("198.51.100.40")))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/data"))
                    .andExpect(content().string(containsString("Inténtalo de nuevo dentro de 1 minuto.")));
            mvc.perform(get("/signup/access").session(session))
                    .andExpect(redirectedUrl("/signup"));

            verify(signUpLimit, never()).tryReserve(any());
            verifyNoInteractions(registration);
        }

        @Test
        @DisplayName("Con cupo, el paso 1 sigue al paso 2 como siempre")
        void withQuotaStep1MovesOnToStep2() throws Exception {
            mvc.perform(validStep1(post("/signup")).with(from("198.51.100.40")))
                    .andExpect(redirectedUrl("/signup/access"));
        }

        private static RequestPostProcessor from(String ip) {
            return request -> {
                request.setRemoteAddr(ip);
                return request;
            };
        }
    }

    @Nested
    @DisplayName("Errores accesibles en los pasos del alta (#145)")
    class ErroresAccesibles {

        @Test
        @DisplayName("En el paso 1, un nombre vacío marca el nombre como erróneo y deja el resto sin marcar")
        void onStep1ABlankNameMarksTheNameAsInvalid() throws Exception {
            String html = mvc.perform(post("/signup").param("fullName", "").param("taxId", "12345678Z")
                            .param("email", "margaret@example.com"))
                    .andReturn().getResponse().getContentAsString();

            FieldTags.assertInvalid(html, "fullName");
            FieldTags.assertValid(html, "taxId");
            FieldTags.assertValid(html, "email");
        }

        @Test
        @DisplayName("En el paso 2, una contraseña corta marca la contraseña como errónea y no la repetición")
        void onStep2AShortPasswordMarksThePasswordAsInvalid() throws Exception {
            String html = mvc.perform(post("/signup/access").session(sessionAfterStep1()).param("username", "margaret")
                            .param("password", "corta").param("passwordRepetition", "corta"))
                    .andReturn().getResponse().getContentAsString();

            FieldTags.assertInvalid(html, "password");
            FieldTags.assertValid(html, "passwordRepetition");
            FieldTags.assertValid(html, "username");
        }

        @Test
        @DisplayName("En el paso 3, no aceptar las condiciones las marca como erróneas")
        void onStep3NotAcceptingTheTermsMarksThemAsInvalid() throws Exception {
            String html = mvc.perform(post("/signup/account").session(sessionAfterStep2())
                            .param("initialDeposit", "100.00").param("currency", "EUR"))
                    .andReturn().getResponse().getContentAsString();

            FieldTags.assertInvalid(html, "conditionsAccepted");
            FieldTags.assertValid(html, "initialDeposit");
        }
    }

    @Nested
    @DisplayName("Campos obligatorios y teclado móvil en el alta (#146)")
    class CamposObligatoriosYTeclado {

        @Test
        @DisplayName("En el paso 1, nombre, documento y correo son obligatorios, pasan al siguiente campo y el documento va en mayúsculas")
        void onStep1TheFieldsAreRequiredAndMoveToTheNextOne() throws Exception {
            String html = mvc.perform(get("/signup")).andReturn().getResponse().getContentAsString();

            for (String id : List.of("fullName", "taxId", "email")) {
                assertThat(FieldTags.tag(html, id)).as(id).contains(" required").contains("enterkeyhint=\"next\"");
            }
            assertThat(FieldTags.tag(html, "taxId")).contains("autocapitalize=\"characters\"");
        }

        @Test
        @DisplayName("En el paso 2, usuario y contraseñas son obligatorios y pasan al siguiente campo")
        void onStep2UsernameAndPasswordsAreRequired() throws Exception {
            String html = mvc.perform(get("/signup/access").session(sessionAfterStep1()))
                    .andReturn().getResponse().getContentAsString();

            for (String id : List.of("username", "password", "passwordRepetition")) {
                assertThat(FieldTags.tag(html, id)).as(id).contains(" required").contains("enterkeyhint=\"next\"");
            }
        }

        @Test
        @DisplayName("En el paso 3, depósito y condiciones son obligatorios y el depósito termina el formulario")
        void onStep3DepositAndTermsAreRequired() throws Exception {
            String html = mvc.perform(get("/signup/account").session(sessionAfterStep2()))
                    .andReturn().getResponse().getContentAsString();

            assertThat(FieldTags.tag(html, "initialDeposit")).contains(" required").contains("enterkeyhint=\"done\"");
            assertThat(FieldTags.tag(html, "conditionsAccepted")).contains(" required");
        }

        @Test
        @DisplayName("En el paso 2, cada contraseña tiene su botón «Mostrar contraseña», oculto sin JavaScript")
        void onStep2EachPasswordHasItsShowPasswordButton() throws Exception {
            String html = mvc.perform(get("/signup/access").session(sessionAfterStep1()))
                    .andReturn().getResponse().getContentAsString();

            FieldTags.assertPasswordToggle(html, "password");
            FieldTags.assertPasswordToggle(html, "passwordRepetition");
        }

        @Test
        @DisplayName("En el paso 2, una contraseña corta sigue mostrando su error junto al botón")
        void onStep2AShortPasswordStillShowsItsError() throws Exception {
            String html = mvc.perform(post("/signup/access").session(sessionAfterStep1()).param("username", "margaret")
                            .param("password", "corta").param("passwordRepetition", "corta"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).containsPattern("(?s)<div class=\"input-group has-validation\"[^>]*>\\s*<input[^>]*id=\"password\".*?</button>\\s*<p class=\"invalid-feedback\" id=\"password-error\">");
        }
    }

    @Nested
    @DisplayName("El usuario se guarda en minúsculas (#152)")
    class UsuarioEnMinusculas {

        @Test
        @DisplayName("Un usuario con mayúsculas y espacios alrededor pasa al paso 3 y se da de alta en minúsculas")
        void aUsernameWithCapitalsSignsUpInLowerCase() throws Exception {
            when(registration.register(any(SignUpCommand.class), eq("clave-secreta-1"), any()))
                    .thenReturn(openedAccount(UUID.randomUUID()));
            MockHttpSession session = sessionAfterStep1();

            mvc.perform(post("/signup/access").session(session).param("username", " Margaret ")
                            .param("password", "clave-secreta-1").param("passwordRepetition", "clave-secreta-1"))
                    .andExpect(redirectedUrl("/signup/account"));
            mvc.perform(validStep3(post("/signup/account")).session(session))
                    .andExpect(status().is3xxRedirection());

            ArgumentCaptor<SignUpCommand> command = ArgumentCaptor.forClass(SignUpCommand.class);
            verify(registration).register(command.capture(), eq("clave-secreta-1"), any());
            assertThat(command.getValue().username()).isEqualTo("margaret");
        }

        @Test
        @DisplayName("Un usuario con tildes o ñ se sigue rechazando con el mensaje que explica el formato")
        void aUsernameWithAccentsOrEnyeIsStillRejected() throws Exception {
            mvc.perform(post("/signup/access").session(sessionAfterStep1()).param("username", "Íñigo")
                            .param("password", "clave-secreta-1").param("passwordRepetition", "clave-secreta-1"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("signup/access"))
                    .andExpect(content().string(containsString("guiones bajos o guiones")));

            verifyNoInteractions(registration);
        }

        @Test
        @DisplayName("El paso 2 explica el formato del usuario bajo el campo y lo liga al campo, también junto al error")
        void step2ExplainsTheUsernameFormat() throws Exception {
            MockHttpSession session = sessionAfterStep1();
            String html = mvc.perform(get("/signup/access").session(session)).andReturn().getResponse().getContentAsString();

            assertThat(html).containsPattern("<p class=\"form-text[^\"]*\" id=\"username-help\">[^<]*guiones bajos o guiones[^<]*minúsculas");
            assertThat(FieldTags.tag(html, "username"))
                    .contains("aria-describedby=\"username-help\"")
                    .doesNotContain("maxlength=")
                    .doesNotContain("pattern=")
                    .doesNotContain("minlength=");

            String withError = mvc.perform(post("/signup/access").session(session).param("username", "Ab")
                            .param("password", "clave-secreta-1").param("passwordRepetition", "clave-secreta-1"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(FieldTags.tag(withError, "username")).contains("aria-describedby=\"username-help username-error\"");
        }
    }
}
