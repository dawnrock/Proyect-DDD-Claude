package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;

/**
 * El usuario del inicio de sesión no distingue mayúsculas ni espacios alrededor (#157), sobre el almacén real de
 * usuarios ({@link UserStore}) en una H2 en modo PostgreSQL, que compara el usuario distinguiendo mayúsculas como Neon.
 */
class CaseInsensitiveLoginTests {

    private final SecuritySupport support = new SecuritySupport();
    private final PasswordEncoder encoder = support.passwordEncoder();
    private final ThrottledAuthenticationProvider provider = provider();

    private ThrottledAuthenticationProvider provider() {
        DataSource database = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        Flyway.configure().dataSource(database).load().migrate();
        UserDetailsManager users = new UserStore().userDetailsService(database,
                new SecurityUsersProperties(List.of()), encoder, "", "");
        users.createUser(User.withUsername("elber").password(encoder.encode("contraseña")).roles("USER").build());
        DaoAuthenticationProvider credentials = new DaoAuthenticationProvider(users);
        credentials.setPasswordEncoder(encoder);
        LoginAttempts attempts = new LoginAttempts(new LoginAttemptsProperties(3, 10, Duration.ofMinutes(30)),
                Clock.systemUTC());
        return new ThrottledAuthenticationProvider(credentials, attempts);
    }

    private Authentication login(String username, String password) {
        return provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
    }

    @ParameterizedTest
    @ValueSource(strings = {"elber", "Elber", "ELBER", " elber ", "\tElber "})
    @DisplayName("Un cliente dado de alta en minúsculas entra aunque escriba su usuario con mayúsculas o espacios")
    void logsInEvenWhenTheUsernameIsTypedWithCapitalsOrSpaces(String typed) {
        Authentication result = login(typed, "contraseña");

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo("elber");
    }

    @Test
    @DisplayName("Con mayúsculas en el usuario, una contraseña errónea sigue rechazándose")
    void withCapitalsAWrongPasswordIsStillRejected() {
        assertThatExceptionOfType(BadCredentialsException.class).isThrownBy(() -> login("Elber", "mala"));
    }

    @Test
    @DisplayName("Un usuario que no existe recibe el mismo error que una contraseña errónea")
    void aMissingUserGetsTheSameErrorAsAWrongPassword() {
        assertThatExceptionOfType(BadCredentialsException.class).isThrownBy(() -> login("Nadie", "contraseña"))
                .withMessage(catchMessage(() -> login("Elber", "mala")));
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
        } catch (BadCredentialsException e) {
            return e.getMessage();
        }
        throw new AssertionError("Se esperaba BadCredentialsException");
    }
}
