package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.SignUpCommand;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.DuplicateCustomerException;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.support.ApplicationServicesFixture;

/**
 * Alta con una identidad de Auth0, sin contraseña local (#198, ADR 0024). Contrato:
 * {@code docs/contracts/auth0-sign-up.md}.
 */
class CustomerRegistrationExternalIdentityTests {

    private static final String PASSWORD = "demo-1234";
    private static final String IP = "203.0.113.5";
    private static final Clock NOW_CLOCK = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC);

    private final PasswordEncoder encoder = new SecuritySupport().passwordEncoder();
    private CheckingAccountRepository accounts;
    private InMemoryUserDetailsManager users;
    private InMemoryExternalIdentities identities;
    private CustomerRegistration registration;
    private SignUpLimit limit;

    @BeforeEach
    void setUp() {
        accounts = PersistenceTestAdapters.accounts();
        users = new InMemoryUserDetailsManager();
        identities = new InMemoryExternalIdentities(users::userExists);
        registration = registrationWith(new SignUpLimitProperties(1_000, 1_000, Duration.ofHours(1), 1_000));
    }

    private CustomerRegistration registrationWith(SignUpLimitProperties limits) {
        return registrationWith(limits, identities);
    }

    private CustomerRegistration registrationWith(SignUpLimitProperties limits, ExternalIdentities externalIdentities) {
        limit = new SignUpLimit(limits, NOW_CLOCK);
        ApplicationServicesFixture fixture = ApplicationServicesFixture.builder().accounts(accounts).build();
        return new CustomerRegistration(fixture.accountOpeningService(), fixture.queryService(), users, encoder, limit,
                externalIdentities, NOW_CLOCK);
    }

    private static SignUpCommand.Builder signUp() {
        return SignUpCommand.builder()
                .fullName("Margaret Hamilton")
                .taxId("00000000T")
                .email("margaret@example.com")
                .username("margaret")
                .initialDeposit(new BigDecimal("100.00"));
    }

    private static PendingIdentity pending(String subject, Instant createdAt) {
        return new PendingIdentity(ExternalIdentities.AUTH0, subject, "margaret@example.com", createdAt);
    }

    private static PendingIdentity freshPending() {
        return pending("auth0|margaret", NOW_CLOCK.instant());
    }

    @Test
    @DisplayName("Abre la cuenta, crea un usuario USER del titular y lo vincula a la identidad")
    void opensTheAccountCreatesTheUserAndLinksTheIdentity() {
        AccountSummary summary = registration.registerWithExternalIdentity(signUp().build(), freshPending(), IP);

        assertThat(summary.holderName()).isEqualTo("Margaret Hamilton");
        assertThat(accounts.findAll()).hasSize(1);
        assertThat(users.loadUserByUsername("margaret").getAuthorities()).extracting(Object::toString)
                .containsExactly("ROLE_USER");
        assertThat(identities.usernameOf(ExternalIdentities.AUTH0, "auth0|margaret")).contains("margaret");
    }

    @Test
    @DisplayName("El usuario no entra con ninguna contraseña, ni la vacía, aunque pase por el límite de intentos")
    void theUserCannotSignInWithAnyPassword() {
        registration.registerWithExternalIdentity(signUp().build(), freshPending(), IP);

        DaoAuthenticationProvider dao = new DaoAuthenticationProvider(users);
        dao.setPasswordEncoder(encoder);
        LoginAttempts attempts = new LoginAttempts(
                new LoginAttemptsProperties(1_000, 1_000, Duration.ofMinutes(30)), NOW_CLOCK);
        AuthenticationManager authentication = new ProviderManager(new ThrottledAuthenticationProvider(dao, attempts));
        for (String password : List.of("", " ", PASSWORD, "margaret", "auth0|margaret", "null")) {
            assertThatExceptionOfType(BadCredentialsException.class).as("contraseña '%s'", password)
                    .isThrownBy(() -> authentication.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated("margaret", password)));
        }
    }

    @Test
    @DisplayName("Cada alta guarda una contraseña inutilizable distinta, cifrada y sin relación con el usuario")
    void eachSignUpStoresADifferentHashedUnusablePassword() {
        registration.registerWithExternalIdentity(signUp().build(), freshPending(), IP);
        registration.registerWithExternalIdentity(
                signUp().taxId("12345678Z").username("otra").email("otra@example.com").build(),
                pending("auth0|otra", NOW_CLOCK.instant()), IP);

        String first = users.loadUserByUsername("margaret").getPassword();
        String second = users.loadUserByUsername("otra").getPassword();
        assertThat(first).startsWith("$2").isNotEqualTo(second);
    }

    @Test
    @DisplayName("Una identidad caducada se rechaza sin abrir nada ni tocar el límite de altas")
    void anExpiredIdentityIsRejectedWithoutTouchingTheLimits() {
        registration = registrationWith(new SignUpLimitProperties(1, 1, Duration.ofHours(1), 1));
        PendingIdentity expired = pending("auth0|margaret", NOW_CLOCK.instant().minus(PendingIdentity.VALIDITY));

        assertThatExceptionOfType(PendingIdentityExpiredException.class)
                .isThrownBy(() -> registration.registerWithExternalIdentity(signUp().build(), expired, IP));

        assertThat(accounts.findAll()).isEmpty();
        assertThat(users.userExists("margaret")).isFalse();
        assertThat(identities.usernameOf(ExternalIdentities.AUTH0, "auth0|margaret")).isEmpty();
        registration.registerWithExternalIdentity(signUp().build(), freshPending(), IP);
        assertThat(accounts.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Un usuario ocupado, un documento existente o un depósito insuficiente fallan como en el alta clásica")
    void theSameFailuresAsTheClassicSignUp() {
        registration.register(signUp().build(), PASSWORD, IP);

        SignUpCommand takenUsername = signUp().taxId("12345678Z").email("otra@example.com").build();
        assertThatExceptionOfType(UsernameTakenException.class).isThrownBy(
                () -> registration.registerWithExternalIdentity(takenUsername, freshPending(), IP));
        SignUpCommand sameDocument = signUp().username("otra").email("otra@example.com").build();
        assertThatExceptionOfType(DuplicateCustomerException.class).isThrownBy(
                () -> registration.registerWithExternalIdentity(sameDocument, freshPending(), IP));
        SignUpCommand poor = signUp().taxId("12345678Z").username("otra").initialDeposit(new BigDecimal("10"))
                .build();
        assertThatExceptionOfType(InsufficientInitialDepositException.class).isThrownBy(
                () -> registration.registerWithExternalIdentity(poor, freshPending(), IP));

        assertThat(accounts.findAll()).hasSize(1);
        assertThat(identities.usernameOf(ExternalIdentities.AUTH0, "auth0|margaret")).isEmpty();
    }

    @Test
    @DisplayName("Los fallos que revelan datos cuentan para el sondeo y el hueco de altas se devuelve")
    void revealingFailuresCountForProbingAndTheSlotIsGivenBack() {
        registration = registrationWith(new SignUpLimitProperties(1, 1_000, Duration.ofHours(1), 2));
        registration.register(signUp().build(), PASSWORD, "198.51.100.1");

        SignUpCommand takenUsername = signUp().taxId("12345678Z").email("otra@example.com").build();
        SignUpCommand sameDocument = signUp().username("otra").email("otra@example.com").build();
        assertThatExceptionOfType(UsernameTakenException.class).isThrownBy(
                () -> registration.registerWithExternalIdentity(takenUsername, freshPending(), IP));
        assertThatExceptionOfType(DuplicateCustomerException.class).isThrownBy(
                () -> registration.registerWithExternalIdentity(sameDocument, freshPending(), IP));

        // El hueco (1 por IP) se devolvió, pero el sondeo (2 fallos) bloquea a la IP
        assertThat(limit.lockedFor(IP)).isPresent();
        SignUpCommand valid = signUp().taxId("12345678Z").username("otra").email("otra@example.com").build();
        assertThatExceptionOfType(TooManySignUpsException.class).isThrownBy(
                () -> registration.registerWithExternalIdentity(valid, freshPending(), IP));
    }

    @Test
    @DisplayName("Superado el límite de altas de la IP, se rechaza sin abrir nada")
    void overTheSignUpLimitNothingIsOpened() {
        registration = registrationWith(new SignUpLimitProperties(1, 2, Duration.ofHours(1), 1_000));
        registration.registerWithExternalIdentity(signUp().build(), freshPending(), IP);

        SignUpCommand other = signUp().taxId("12345678Z").username("otra").email("otra@example.com").build();
        assertThatExceptionOfType(TooManySignUpsException.class).isThrownBy(() -> registration
                .registerWithExternalIdentity(other, pending("auth0|otra", NOW_CLOCK.instant()), IP));
        assertThat(accounts.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Una identidad ya vinculada se rechaza sin abrir nada ni tocar el límite de altas")
    void anAlreadyLinkedIdentityIsRejectedWithoutOpeningAnything() {
        registration = registrationWith(new SignUpLimitProperties(1, 1, Duration.ofHours(1), 1));
        users.createUser(User.withUsername("ada")
                .password("{noop}x").roles("USER").build());
        identities.link(ExternalIdentities.AUTH0, "auth0|margaret", "ada");

        assertThatExceptionOfType(IdentityAlreadyLinkedException.class).isThrownBy(
                () -> registration.registerWithExternalIdentity(signUp().build(), freshPending(), IP));

        assertThat(accounts.findAll()).isEmpty();
        assertThat(users.userExists("margaret")).isFalse();
        registration.registerWithExternalIdentity(signUp().build(), pending("auth0|otra", NOW_CLOCK.instant()), IP);
        assertThat(accounts.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Si otra pestaña vincula la identidad justo antes del enlace, la excepción llega y no se deshace nada")
    void aLinkRaceReachesTheCallerAndUndoesNothing() {
        ExternalIdentities racing = new ExternalIdentities() {
            @Override
            public Optional<String> usernameOf(String provider, String subject) {
                return Optional.empty();
            }

            @Override
            public void link(String provider, String subject, String username) {
                throw new IdentityAlreadyLinkedException(provider, username);
            }
        };
        registration = registrationWith(new SignUpLimitProperties(1_000, 1_000, Duration.ofHours(1), 1_000), racing);

        assertThatExceptionOfType(IdentityAlreadyLinkedException.class).isThrownBy(
                () -> registration.registerWithExternalIdentity(signUp().build(), freshPending(), IP));

        assertThat(accounts.findAll()).hasSize(1);
        assertThat(users.userExists("margaret")).isTrue();
    }
}
