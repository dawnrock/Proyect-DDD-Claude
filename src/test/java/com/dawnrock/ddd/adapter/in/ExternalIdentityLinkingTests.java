package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/** Vinculación con la contraseña local (ADR 0024): mismo proveedor limitado en intentos que el inicio de sesión. */
class ExternalIdentityLinkingTests {

    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    private static final String PASSWORD = "demo-1234";
    private static final String IP = "203.0.113.5";
    private static final String SUB = "auth0|abc123";

    private final PasswordEncoder encoder = new SecuritySupport().passwordEncoder();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private InMemoryUserDetailsManager users;
    private InMemoryExternalIdentities identities;
    private LoginAttempts attempts;
    private ExternalIdentityLinking linking;
    private PendingIdentity pending;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        users = new InMemoryUserDetailsManager(
                User.withUsername("ada").password(encoder.encode(PASSWORD)).roles("USER").build(),
                User.withUsername("grace").password(encoder.encode(PASSWORD)).roles("USER").build());
        identities = new InMemoryExternalIdentities(users::userExists);
        attempts = new LoginAttempts(new LoginAttemptsProperties(3, 10, Duration.ofMinutes(30)), clock);
        DaoAuthenticationProvider credentials = new DaoAuthenticationProvider(users);
        credentials.setPasswordEncoder(encoder);
        ObjectProvider<Clock> clockProvider = mock(ObjectProvider.class);
        when(clockProvider.getIfAvailable(any())).thenReturn(clock);
        linking = new ExternalIdentityLinking(new ThrottledAuthenticationProvider(credentials, attempts), identities,
                clockProvider);
        pending = new PendingIdentity(ExternalIdentities.AUTH0, SUB, "ada@example.com", NOW);
    }

    @Test
    @DisplayName("Con la contraseña correcta vincula la identidad y devuelve el usuario normalizado")
    void withTheRightPasswordItLinksAndReturnsTheNormalisedUsername() {
        String username = linking.link(pending, "  Ada ", PASSWORD, IP);

        assertThat(username).isEqualTo("ada");
        assertThat(identities.usernameOf(ExternalIdentities.AUTH0, SUB)).contains("ada");
    }

    @Test
    @DisplayName("Con una contraseña incorrecta no vincula y cuenta como intento fallido")
    void aWrongPasswordDoesNotLinkAndCountsAsAFailure() {
        for (int i = 0; i < 2; i++) {
            assertThatExceptionOfType(BadCredentialsException.class)
                    .isThrownBy(() -> linking.link(pending, "ada", "mala", IP));
        }

        assertThat(identities.usernameOf(ExternalIdentities.AUTH0, SUB)).isEmpty();
        assertThatExceptionOfType(BadCredentialsException.class)
                .isThrownBy(() -> linking.link(pending, "ada", "mala", IP));
        assertThat(attempts.lockedFor("ada", IP)).isPresent();
    }

    @Test
    @DisplayName("Pasado el límite de intentos, rechaza aunque la contraseña sea la correcta")
    void afterTheLimitItRejectsEvenWithTheRightPassword() {
        for (int i = 0; i < 3; i++) {
            assertThatExceptionOfType(BadCredentialsException.class)
                    .isThrownBy(() -> linking.link(pending, "ada", "mala", IP));
        }

        assertThatExceptionOfType(TooManyLoginAttemptsException.class)
                .isThrownBy(() -> linking.link(pending, "ada", PASSWORD, IP));
        assertThat(identities.usernameOf(ExternalIdentities.AUTH0, SUB)).isEmpty();
    }

    @Test
    @DisplayName("Un usuario que ya tiene otra identidad de Auth0 lanza IdentityAlreadyLinkedException")
    void aUserWithAnotherAuth0IdentityThrowsIdentityAlreadyLinked() {
        identities.link(ExternalIdentities.AUTH0, "auth0|otra", "ada");

        assertThatExceptionOfType(IdentityAlreadyLinkedException.class)
                .isThrownBy(() -> linking.link(pending, "ada", PASSWORD, IP));
        assertThat(identities.usernameOf(ExternalIdentities.AUTH0, SUB)).isEmpty();
    }

    @Test
    @DisplayName("Una identidad que ya es de otro usuario no se vincula a este")
    void anIdentityOfAnotherUserIsNotLinkedToThisOne() {
        identities.link(ExternalIdentities.AUTH0, SUB, "grace");

        assertThatExceptionOfType(IdentityAlreadyLinkedException.class)
                .isThrownBy(() -> linking.link(pending, "ada", PASSWORD, IP));
        assertThat(identities.usernameOf(ExternalIdentities.AUTH0, SUB)).contains("grace");
    }

    @Test
    @DisplayName("Una identidad pendiente caducada se rechaza como caducada sin comprobar la contraseña")
    void anExpiredPendingIdentityThrowsWithoutCheckingThePassword() {
        PendingIdentity expired = new PendingIdentity(ExternalIdentities.AUTH0, SUB, null,
                NOW.minus(PendingIdentity.VALIDITY));

        assertThatExceptionOfType(PendingIdentityExpiredException.class)
                .isThrownBy(() -> linking.link(expired, "ada", "mala", IP));
        assertThat(attempts.lockedFor("ada", IP)).isEmpty();
        assertThat(identities.usernameOf(ExternalIdentities.AUTH0, SUB)).isEmpty();
    }
}
