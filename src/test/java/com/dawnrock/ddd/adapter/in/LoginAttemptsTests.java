package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contadores de intentos fallidos de inicio de sesión (#130, ADR 0017): 3 por usuario e IP, 10 por IP, 30 minutos. */
class LoginAttemptsTests {

    private static final String ADA = "ada";
    private static final String IP = "203.0.113.5";
    private static final String OTHER_IP = "203.0.113.6";

    private MovableClock clock;
    private LoginAttempts attempts;

    @BeforeEach
    void setUp() {
        clock = new MovableClock(Instant.parse("2026-11-20T10:00:00Z"));
        attempts = new LoginAttempts(new LoginAttemptsProperties(3, 10, Duration.ofMinutes(30)), clock);
    }

    private void failTimes(String username, String ip, int times) {
        for (int i = 0; i < times; i++) {
            attempts.tryAttempt(username, ip);
        }
    }

    @Test
    @DisplayName("Cada intento se reserva al empezar: el cuarto intento simultáneo ya encuentra el bloqueo")
    void eachAttemptIsReservedWhenItStarts() {
        assertThat(attempts.tryAttempt(ADA, IP)).isEmpty();
        assertThat(attempts.tryAttempt(ADA, IP)).isEmpty();
        assertThat(attempts.tryAttempt(ADA, IP)).isEmpty();

        assertThat(attempts.tryAttempt(ADA, IP)).contains(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("Un intento liberado, porque no falló la contraseña, no cuenta como fallo")
    void aReleasedAttemptDoesNotCountAsAFailure() {
        failTimes(ADA, IP, 2);

        attempts.tryAttempt(ADA, IP);
        attempts.released(ADA, IP);

        assertThat(attempts.lockedFor(ADA, IP)).isEmpty();
    }

    @Test
    @DisplayName("Un nombre de usuario larguísimo se recorta en la clave y no crece sin límite")
    void aVeryLongUsernameIsTruncatedInTheKey() {
        String longName = "a".repeat(5_000);
        failTimes(longName, IP, 3);

        assertThat(attempts.lockedFor(longName.substring(0, 128) + "zzz", IP)).isPresent();
    }

    @Test
    @DisplayName("Sin fallos no hay bloqueo")
    void withoutFailuresThereIsNoLockout() {
        assertThat(attempts.lockedFor(ADA, IP)).isEmpty();
    }

    @Test
    @DisplayName("Tras 3 fallos el usuario queda bloqueado desde esa IP 30 minutos, pero no desde otra")
    void afterThreeFailuresTheUserIsLockedFromThatIp() {
        failTimes(ADA, IP, 2);
        assertThat(attempts.lockedFor(ADA, IP)).isEmpty();

        attempts.tryAttempt(ADA, IP);

        assertThat(attempts.lockedFor(ADA, IP)).contains(Duration.ofMinutes(30));
        assertThat(attempts.lockedFor(ADA, OTHER_IP)).isEmpty();
    }

    @Test
    @DisplayName("Tras 10 fallos desde una IP con usuarios distintos, esa IP queda bloqueada para cualquier usuario")
    void afterTenFailuresFromAnIpItIsLockedForAnyUser() {
        for (int i = 0; i < 10; i++) {
            attempts.tryAttempt("usuario" + i, IP);
        }

        assertThat(attempts.lockedFor("grace", IP)).contains(Duration.ofMinutes(30));
        assertThat(attempts.lockedFor("grace", OTHER_IP)).isEmpty();
    }

    @Test
    @DisplayName("Un acceso correcto antes del límite reinicia el contador del usuario en esa IP")
    void aSuccessfulLoginBeforeTheLimitResetsTheCounter() {
        failTimes(ADA, IP, 2);

        attempts.succeeded(ADA, IP);
        failTimes(ADA, IP, 2);

        assertThat(attempts.lockedFor(ADA, IP)).isEmpty();
    }

    @Test
    @DisplayName("El bloqueo termina 30 minutos después del último fallo y el tiempo restante va bajando")
    void theLockoutEndsThirtyMinutesAfterTheLastFailure() {
        failTimes(ADA, IP, 3);

        clock.advance(Duration.ofMinutes(20));
        assertThat(attempts.lockedFor(ADA, IP)).contains(Duration.ofMinutes(10));

        clock.advance(Duration.ofMinutes(10));
        assertThat(attempts.lockedFor(ADA, IP)).isEmpty();
    }

    @Test
    @DisplayName("El usuario se compara sin distinguir mayúsculas")
    void theUsernameIsComparedCaseInsensitively() {
        attempts.tryAttempt("Ada", IP);
        attempts.tryAttempt("ADA", IP);
        attempts.tryAttempt("ada", IP);

        assertThat(attempts.lockedFor("aDa", IP)).isPresent();
    }

    @Test
    @DisplayName("Los fallos más antiguos que la ventana de 30 minutos dejan de contar")
    void oldFailuresStopCounting() {
        failTimes(ADA, IP, 2);
        clock.advance(Duration.ofMinutes(31));

        attempts.tryAttempt(ADA, IP);

        assertThat(attempts.lockedFor(ADA, IP)).isEmpty();
    }

    @Test
    @DisplayName("Sin valores, 3 fallos por usuario, 10 por IP y 30 minutos de bloqueo")
    void defaultValues() {
        LoginAttemptsProperties properties = new LoginAttemptsProperties(null, null, null);

        assertThat(properties.maxPerUser()).isEqualTo(3);
        assertThat(properties.maxPerIp()).isEqualTo(10);
        assertThat(properties.lockout()).isEqualTo(Duration.ofMinutes(30));
    }

    /** Reloj fijo que el test adelanta a mano. */
    private static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
