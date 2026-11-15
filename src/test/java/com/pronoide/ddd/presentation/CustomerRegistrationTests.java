package com.pronoide.ddd.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.CallerDef;
import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.application.OpenAccountCommand;
import com.pronoide.ddd.application.SignUpCommand;
import com.pronoide.ddd.domain.model.DuplicateCustomerException;
import com.pronoide.ddd.domain.model.InsufficientInitialDepositException;
import com.pronoide.ddd.integration.TaxAgencyClient;
import com.pronoide.ddd.persistence.InMemoryAccountNumberSequence;
import com.pronoide.ddd.persistence.InMemoryAccountOwnershipRepository;
import com.pronoide.ddd.persistence.InMemoryCheckingAccountRepository;
import com.pronoide.ddd.persistence.InMemoryCustomerRepository;

/** Alta con usuario: la aplicación no conoce Spring Security, así que las credenciales se crean aquí. */
class CustomerRegistrationTests {

    private static final String PASSWORD = "demo-1234";

    private final PasswordEncoder encoder = new SecuritySupport().passwordEncoder();
    private InMemoryCheckingAccountRepository accounts;
    private InMemoryUserDetailsManager users;
    private CustomerRegistration registration;
    private CheckingAccountService service;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryCheckingAccountRepository();
        users = new InMemoryUserDetailsManager();
        registration = registrationWith(users);
    }

    private CustomerRegistration registrationWith(InMemoryUserDetailsManager manager) {
        service = new CheckingAccountService(accounts, new InMemoryAccountOwnershipRepository(),
                new InMemoryCustomerRepository(), new InMemoryAccountNumberSequence(), new TaxAgencyClient(),
                Clock.systemUTC());
        return new CustomerRegistration(service, manager, encoder);
    }

    private static SignUpCommand.Builder signUp() {
        return SignUpCommand.builder()
                .fullName("Margaret Hamilton")
                .taxId("00000000T")
                .email("margaret@example.com")
                .username("margaret")
                .initialDeposit(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("El alta abre la cuenta y crea un usuario USER que puede autenticarse con su contraseña")
    void el_alta_abre_la_cuenta_y_crea_un_usuario_que_puede_autenticarse() {
        AccountSummary summary = registration.register(signUp().build(), PASSWORD);

        assertThat(summary.holderName()).isEqualTo("Margaret Hamilton");
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        AuthenticationManager authentication = new ProviderManager(provider);
        Authentication result = authentication.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("margaret", PASSWORD));
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getAuthorities()).extracting(Object::toString).contains("ROLE_USER")
                .doesNotContain("ROLE_ADMIN");
    }

    @Test
    @DisplayName("La contraseña se guarda cifrada, nunca en claro")
    void la_contrasena_se_guarda_cifrada() {
        registration.register(signUp().build(), PASSWORD);

        UserDetails user = users.loadUserByUsername("margaret");
        assertThat(user.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("Un usuario que ya existe se rechaza antes de abrir nada")
    void un_usuario_que_ya_existe_se_rechaza_antes_de_abrir_nada() {
        registration.register(signUp().build(), PASSWORD);

        SignUpCommand other = signUp().fullName("Otra Persona").taxId("12345678Z").email("otra@example.com").build();
        assertThatExceptionOfType(UsernameTakenException.class)
                .isThrownBy(() -> registration.register(other, PASSWORD))
                .withMessage("El usuario margaret ya existe: elige otro");
        assertThat(accounts.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("El usuario que el administrador dio a un cliente no se puede tomar en el alta, aunque aún no pueda entrar")
    void el_usuario_de_un_cliente_sin_login_no_se_puede_tomar_en_el_alta() {
        service.openAccount(CallerDef.system(), OpenAccountCommand.builder()
                .holderTaxId("12345678Z").holderName("Ada Lovelace").holderEmail("ada@example.com")
                .ownerUsername("margaret").initialDeposit(new BigDecimal("100")).build());

        assertThatExceptionOfType(UsernameTakenException.class)
                .isThrownBy(() -> registration.register(signUp().build(), PASSWORD));
        assertThat(accounts.findAll()).hasSize(1);
        assertThat(users.userExists("margaret")).isFalse();
    }

    @Test
    @DisplayName("Una contraseña de menos de 8 caracteres se rechaza y no se abre nada")
    void una_contrasena_corta_se_rechaza_y_no_se_abre_nada() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> registration.register(signUp().build(), "corta12"))
                .withMessage("La contraseña debe tener al menos 8 caracteres");

        assertThat(accounts.findAll()).isEmpty();
        assertThat(users.userExists("margaret")).isFalse();
    }

    @Test
    @DisplayName("Si el alta falla por un cliente duplicado no se crea ningún usuario")
    void si_el_alta_falla_por_un_cliente_duplicado_no_se_crea_ningun_usuario() {
        registration.register(signUp().build(), PASSWORD);

        SignUpCommand sameDocument = signUp().username("otra").email("otra@example.com").build();
        assertThatExceptionOfType(DuplicateCustomerException.class)
                .isThrownBy(() -> registration.register(sameDocument, PASSWORD));

        assertThat(users.userExists("otra")).isFalse();
    }

    @Test
    @DisplayName("Si el alta falla por un depósito insuficiente no queda ni cuenta ni usuario")
    void si_el_alta_falla_por_un_deposito_insuficiente_no_queda_ni_cuenta_ni_usuario() {
        SignUpCommand poor = signUp().initialDeposit(new BigDecimal("10")).build();

        assertThatExceptionOfType(InsufficientInitialDepositException.class)
                .isThrownBy(() -> registration.register(poor, PASSWORD));

        assertThat(accounts.findAll()).isEmpty();
        assertThat(users.userExists("margaret")).isFalse();
    }

    @Test
    @DisplayName("Si al crear el usuario ya lo ha tomado otro, se informa como usuario ocupado")
    void si_al_crear_el_usuario_ya_lo_ha_tomado_otro_se_informa_como_usuario_ocupado() {
        InMemoryUserDetailsManager racing = new InMemoryUserDetailsManager() {
            private boolean firstCheck = true;

            @Override
            public boolean userExists(String username) {
                boolean exists = !firstCheck;
                firstCheck = false;
                return exists;
            }

            @Override
            public void createUser(UserDetails user) {
                throw new IllegalArgumentException("user should not exist");
            }
        };

        assertThatExceptionOfType(UsernameTakenException.class)
                .isThrownBy(() -> registrationWith(racing).register(signUp().build(), PASSWORD));
    }

    @Test
    @DisplayName("Con altas simultáneas del mismo usuario solo una prospera")
    void con_altas_simultaneas_del_mismo_usuario_solo_una_prospera() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> calls = List.of(
                    () -> tryRegister("00000000T"), () -> tryRegister("12345678Z"),
                    () -> tryRegister("87654321X"), () -> tryRegister("11111111H"));
            long successes = 0;
            for (Future<Boolean> result : executor.invokeAll(calls)) {
                successes += result.get() ? 1 : 0;
            }
            assertThat(successes).isEqualTo(1);
            assertThat(accounts.findAll()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean tryRegister(String taxId) {
        try {
            registration.register(signUp().taxId(taxId).build(), PASSWORD);
            return true;
        } catch (UsernameTakenException e) {
            return false;
        }
    }

    @Test
    @DisplayName("Un usuario desconocido no existe antes del alta")
    void un_usuario_desconocido_no_existe_antes_del_alta() {
        org.assertj.core.api.Assertions.assertThatExceptionOfType(UsernameNotFoundException.class)
                .isThrownBy(() -> users.loadUserByUsername("margaret"));
    }
}
