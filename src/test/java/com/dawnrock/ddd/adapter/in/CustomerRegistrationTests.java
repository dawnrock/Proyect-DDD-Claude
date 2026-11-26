package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.CheckingAccountQueryService;
import com.dawnrock.ddd.application.OpenAccountCommand;
import com.dawnrock.ddd.application.SignUpCommand;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.DuplicateCustomerException;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.support.ApplicationServicesFixture;

/** Alta con usuario: la aplicación no conoce Spring Security, así que las credenciales se crean aquí. */
class CustomerRegistrationTests {

    private static final String PASSWORD = "demo-1234";
    private static final String IP = "203.0.113.5";
    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();

    private final PasswordEncoder encoder = new SecuritySupport().passwordEncoder();
    private CheckingAccountRepository accounts;
    private InMemoryUserDetailsManager users;
    private CustomerRegistration registration;
    private CheckingAccountQueryService service;
    private AccountOpeningCommands opening;
    private SignUpLimit limit;

    @BeforeEach
    void setUp() {
        accounts = PersistenceTestAdapters.accounts();
        users = new InMemoryUserDetailsManager();
        registration = registrationWith(users);
    }

    private CustomerRegistration registrationWith(InMemoryUserDetailsManager manager) {
        return registrationWith(manager, new SignUpLimitProperties(1_000, 1_000, Duration.ofHours(1), 1_000));
    }

    private CustomerRegistration registrationWith(InMemoryUserDetailsManager manager, SignUpLimitProperties limits) {
        limit = new SignUpLimit(limits, Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC));
        ApplicationServicesFixture fixture = ApplicationServicesFixture.builder().accounts(accounts).build();
        service = fixture.queryService();
        opening = fixture.accountOpeningService();
        return new CustomerRegistration(opening, service, manager, encoder, limit,
                new InMemoryExternalIdentities(manager::userExists), Clock.systemUTC());
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
    void signUpOpensTheAccountAndCreatesAUserWhoCanAuthenticate() {
        AccountSummary summary = registration.register(signUp().build(), PASSWORD, IP);

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
    void thePasswordIsStoredHashed() {
        registration.register(signUp().build(), PASSWORD, IP);

        UserDetails user = users.loadUserByUsername("margaret");
        assertThat(user.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("Un usuario que ya existe se rechaza antes de abrir nada")
    void anExistingUserIsRejectedBeforeOpeningAnything() {
        registration.register(signUp().build(), PASSWORD, IP);

        SignUpCommand other = signUp().fullName("Otra Persona").taxId("12345678Z").email("otra@example.com").build();
        assertThatExceptionOfType(UsernameTakenException.class)
                .isThrownBy(() -> registration.register(other, PASSWORD, IP))
                .withMessage("El usuario margaret ya existe: elige otro");
        assertThat(accounts.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("El usuario que el administrador dio a un cliente no se puede tomar en el alta, aunque aún no pueda entrar")
    void theUsernameOfACustomerWithoutLoginCannotBeTakenOnSignUp() {
        opening.openAccount(ADMIN, OpenAccountCommand.builder()
                .holderTaxId("12345678Z").holderName("Ada Lovelace").holderEmail("ada@example.com")
                .ownerUsername("margaret").initialDeposit(new BigDecimal("100")).build());

        assertThatExceptionOfType(UsernameTakenException.class)
                .isThrownBy(() -> registration.register(signUp().build(), PASSWORD, IP));
        assertThat(accounts.findAll()).hasSize(1);
        assertThat(users.userExists("margaret")).isFalse();
    }

    @Test
    @DisplayName("Una contraseña de menos de 8 caracteres se rechaza y no se abre nada")
    void aShortPasswordIsRejectedAndNothingIsOpened() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> registration.register(signUp().build(), "corta12", IP))
                .withMessage("La contraseña debe tener al menos 8 caracteres");

        assertThat(accounts.findAll()).isEmpty();
        assertThat(users.userExists("margaret")).isFalse();
    }

    @Test
    @DisplayName("Si el alta falla por un cliente duplicado no se crea ningún usuario")
    void whenSignUpFailsForADuplicateCustomerNoUserIsCreated() {
        registration.register(signUp().build(), PASSWORD, IP);

        SignUpCommand sameDocument = signUp().username("otra").email("otra@example.com").build();
        assertThatExceptionOfType(DuplicateCustomerException.class)
                .isThrownBy(() -> registration.register(sameDocument, PASSWORD, IP));

        assertThat(users.userExists("otra")).isFalse();
    }

    @Test
    @DisplayName("Si el alta falla por un depósito insuficiente no queda ni cuenta ni usuario")
    void whenSignUpFailsForAnInsufficientDepositNeitherAccountNorUserRemains() {
        SignUpCommand poor = signUp().initialDeposit(new BigDecimal("10")).build();

        assertThatExceptionOfType(InsufficientInitialDepositException.class)
                .isThrownBy(() -> registration.register(poor, PASSWORD, IP));

        assertThat(accounts.findAll()).isEmpty();
        assertThat(users.userExists("margaret")).isFalse();
    }

    @Test
    @DisplayName("Si al crear el usuario ya lo ha tomado otro, se informa como usuario ocupado")
    void whenTheUsernameIsTakenWhileCreatingTheUserItIsReportedAsTaken() {
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
                .isThrownBy(() -> registrationWith(racing).register(signUp().build(), PASSWORD, IP));
    }

    @Test
    @DisplayName("Con altas simultáneas del mismo usuario solo una prospera")
    void withConcurrentSignUpsOfTheSameUserOnlyOneSucceeds() throws Exception {
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
            registration.register(signUp().taxId(taxId).build(), PASSWORD, IP);
            return true;
        } catch (UsernameTakenException e) {
            return false;
        }
    }

    @Test
    @DisplayName("Un usuario desconocido no existe antes del alta")
    void anUnknownUserDoesNotExistBeforeSignUp() {
        assertThatExceptionOfType(UsernameNotFoundException.class)
                .isThrownBy(() -> users.loadUserByUsername("margaret"));
    }

    @Nested
    @DisplayName("Límite de altas (#131, ADR 0018)")
    class Limit {

        @BeforeEach
        void oneSignUpPerIpAndTwoInTotal() {
            registration = registrationWith(users, new SignUpLimitProperties(1, 2, Duration.ofHours(1), 1_000));
        }

        @Test
        @DisplayName("Superado el límite de la IP, se rechaza con el tiempo que falta y no se abre nada ni se crea usuario")
        void overTheIpLimitTheSignUpIsRejectedWithoutOpeningAnything() {
            registration.register(signUp().build(), PASSWORD, IP);

            SignUpCommand other = signUp().taxId("12345678Z").username("otra").email("otra@example.com").build();
            assertThatExceptionOfType(TooManySignUpsException.class)
                    .isThrownBy(() -> registration.register(other, PASSWORD, IP))
                    .satisfies(e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofHours(1)))
                    .withMessageContaining("Demasiadas altas");
            assertThat(accounts.findAll()).hasSize(1);
            assertThat(users.userExists("otra")).isFalse();
        }

        @Test
        @DisplayName("Superado el límite total con IP distintas, se rechaza también una IP nueva")
        void overTheTotalLimitANewIpIsRejectedToo() {
            registration.register(signUp().build(), PASSWORD, "198.51.100.1");
            registration.register(signUp().taxId("12345678Z").username("otra").email("otra@example.com").build(),
                    PASSWORD, "198.51.100.2");

            SignUpCommand third = signUp().taxId("87654321X").username("tercera").email("t@example.com").build();
            assertThatExceptionOfType(TooManySignUpsException.class)
                    .isThrownBy(() -> registration.register(third, PASSWORD, IP));
            assertThat(accounts.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("Un alta que falla (usuario ocupado, cliente duplicado, contraseña corta) no gasta el cupo")
        void aFailedSignUpDoesNotUseTheQuota() {
            registration.register(signUp().build(), PASSWORD, "198.51.100.1");

            assertThatExceptionOfType(UsernameTakenException.class).isThrownBy(() -> registration.register(
                    signUp().taxId("12345678Z").email("otra@example.com").build(), PASSWORD, IP));
            assertThatExceptionOfType(DuplicateCustomerException.class).isThrownBy(() -> registration.register(
                    signUp().username("otra").email("otra@example.com").build(), PASSWORD, IP));
            assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> registration.register(
                    signUp().taxId("12345678Z").username("otra").build(), "corta12", IP));

            assertThat(limit.lockedFor(IP)).isEmpty();
            registration.register(signUp().taxId("12345678Z").username("otra").email("otra@example.com").build(),
                    PASSWORD, IP);
            assertThat(limit.lockedFor(IP)).isPresent();
        }

        @Test
        @DisplayName("Si el cliente ya se creó y falla la creación del usuario, el alta sigue contando: ocupa memoria")
        void whenTheCustomerWasCreatedAndTheUserFailsTheSignUpStillCounts() {
            InMemoryUserDetailsManager failing = new InMemoryUserDetailsManager() {
                @Override
                public void createUser(UserDetails user) {
                    throw new IllegalStateException("almacén de usuarios caído");
                }
            };
            registration = registrationWith(failing, new SignUpLimitProperties(1, 2, Duration.ofHours(1), 1_000));

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> registration.register(signUp().build(), PASSWORD, IP));

            assertThat(accounts.findAll()).hasSize(1);
            assertThat(limit.lockedFor(IP)).isPresent();
        }
    }

    @Nested
    @DisplayName("Sondeo de usuarios y documentos (#139, ADR 0018)")
    class Probing {

        @BeforeEach
        void twoRevealingFailuresPerIp() {
            registration = registrationWith(users, new SignUpLimitProperties(1_000, 1_000, Duration.ofHours(1), 2));
            registration.register(signUp().build(), PASSWORD, "198.51.100.1");
        }

        private SignUpCommand.Builder another() {
            return signUp().taxId("12345678Z").username("otra").email("otra@example.com");
        }

        @Test
        @DisplayName("Tras 2 altas con un usuario ocupado o un documento existente, ni un alta válida desde esa IP prospera")
        void afterTwoFailuresRevealingDataNotEvenAValidSignUpSucceeds() {
            assertThatExceptionOfType(UsernameTakenException.class)
                    .isThrownBy(() -> registration.register(another().username("margaret").build(), PASSWORD, IP));
            assertThatExceptionOfType(DuplicateCustomerException.class)
                    .isThrownBy(() -> registration.register(signUp().username("otra").build(), PASSWORD, IP));

            assertThatExceptionOfType(TooManySignUpsException.class)
                    .isThrownBy(() -> registration.register(another().build(), PASSWORD, IP));
            assertThat(accounts.findAll()).hasSize(1);
            assertThat(users.userExists("otra")).isFalse();

            registration.register(another().build(), PASSWORD, "198.51.100.2");
            assertThat(users.userExists("otra")).isTrue();
        }

        @Test
        @DisplayName("Una contraseña corta o un depósito insuficiente no cuentan como sondeo")
        void aShortPasswordOrAnInsufficientDepositDoNotCount() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> registration.register(another().build(), "corta12", IP));
            assertThatExceptionOfType(InsufficientInitialDepositException.class).isThrownBy(() -> registration.register(
                    another().initialDeposit(new BigDecimal("10")).build(), PASSWORD, IP));
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> registration.register(another().build(), "corta12", IP));

            registration.register(another().build(), PASSWORD, IP);
            assertThat(users.userExists("otra")).isTrue();
        }

        @Test
        @DisplayName("Un usuario que se ocupa solo al crearlo, con el cliente ya guardado, no cuenta como sondeo")
        void aUsernameTakenWhileCreatingItDoesNotCountAsProbing() {
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
            accounts = PersistenceTestAdapters.accounts();
            registration = registrationWith(racing, new SignUpLimitProperties(1_000, 1_000, Duration.ofHours(1), 1));

            assertThatExceptionOfType(UsernameTakenException.class)
                    .isThrownBy(() -> registration.register(signUp().build(), PASSWORD, IP));

            assertThat(limit.lockedFor(IP)).isEmpty();
        }
    }
}
