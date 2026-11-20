package com.pronoide.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/** Proveedor de autenticación con límite de intentos (#130, ADR 0017), sobre un delegado simulado. */
class ThrottledAuthenticationProviderTests {

    private static final String IP = "203.0.113.5";

    private StubDelegate delegate;
    private LoginAttempts attempts;
    private ThrottledAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        delegate = new StubDelegate();
        Clock clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);
        attempts = new LoginAttempts(new LoginAttemptsProperties(3, 10, Duration.ofMinutes(30)), clock);
        provider = new ThrottledAuthenticationProvider(delegate, attempts);
    }

    private static Authentication login(String username, String password, String ip) {
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.unauthenticated(username, password);
        if (ip != null) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(ip);
            token.setDetails(new WebAuthenticationDetails(request));
        }
        return token;
    }

    private void failTimes(String username, int times) {
        for (int i = 0; i < times; i++) {
            assertThatExceptionOfType(BadCredentialsException.class)
                    .isThrownBy(() -> provider.authenticate(login(username, "mala", IP)));
        }
    }

    @Test
    @DisplayName("Una contraseña errónea cuenta como fallo y se relanza el error del delegado")
    void una_contrasena_erronea_cuenta_como_fallo_y_se_relanza() {
        failTimes("ada", 1);

        assertThat(delegate.calls).isEqualTo(1);
        assertThat(attempts.lockedFor("ada", IP)).isEmpty();
    }

    @Test
    @DisplayName("Un acceso correcto devuelve la autenticación del delegado y reinicia los fallos")
    void un_acceso_correcto_devuelve_la_autenticacion_y_reinicia_los_fallos() {
        failTimes("ada", 2);

        Authentication result = provider.authenticate(login("ada", StubDelegate.PASSWORD, IP));
        failTimes("ada", 2);

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(attempts.lockedFor("ada", IP)).isEmpty();
    }

    @Test
    @DisplayName("Bloqueado, rechaza con el tiempo restante sin comprobar la contraseña, aunque sea la correcta")
    void bloqueado_rechaza_sin_comprobar_la_contrasena() {
        failTimes("ada", 3);
        int callsBefore = delegate.calls;

        assertThatExceptionOfType(TooManyLoginAttemptsException.class)
                .isThrownBy(() -> provider.authenticate(login("ada", StubDelegate.PASSWORD, IP)))
                .satisfies(e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofMinutes(30)));
        assertThat(delegate.calls).isEqualTo(callsBefore);
    }

    @Test
    @DisplayName("Un usuario que no existe se bloquea igual y con el mismo mensaje que uno que existe")
    void un_usuario_que_no_existe_se_bloquea_igual() {
        failTimes("ada", 3);
        failTimes("nadie", 3);

        AuthenticationException known = catchLock("ada");
        AuthenticationException unknown = catchLock("nadie");

        assertThat(unknown).isInstanceOf(TooManyLoginAttemptsException.class);
        assertThat(unknown.getMessage()).isEqualTo(known.getMessage());
    }

    @Test
    @DisplayName("Sin detalles de la petición, la IP cuenta como desconocida")
    void sin_detalles_la_ip_cuenta_como_desconocida() {
        assertThatExceptionOfType(BadCredentialsException.class)
                .isThrownBy(() -> provider.authenticate(login("ada", "mala", null)));

        assertThat(attempts.lockedFor("ada", ThrottledAuthenticationProvider.UNKNOWN_IP)).isEmpty();
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
    }

    private AuthenticationException catchLock(String username) {
        try {
            provider.authenticate(login(username, "mala", IP));
        } catch (AuthenticationException e) {
            return e;
        }
        throw new AssertionError("Se esperaba un rechazo");
    }

    /** Delegado que solo acepta la contraseña {@link #PASSWORD} y cuenta sus llamadas. */
    private static final class StubDelegate implements AuthenticationProvider {

        static final String PASSWORD = "demo";

        int calls;

        @Override
        public Authentication authenticate(Authentication authentication) {
            calls++;
            if (!PASSWORD.equals(authentication.getCredentials())) {
                throw new BadCredentialsException("Bad credentials");
            }
            return UsernamePasswordAuthenticationToken.authenticated(authentication.getName(), null, List.of());
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
        }
    }
}
