package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Límite de altas de clientes (ADR 0018): 3 por IP y 50 en total por hora (#131) y 10 fallos que revelan datos por IP
 * (#139); aquí, 3 por IP, 5 en total y 2 fallos.
 */
class SignUpLimitTests {

    private static final String IP = "203.0.113.5";
    private static final String OTHER_IP = "203.0.113.6";

    private MovableClock clock;
    private SignUpLimit limit;

    @BeforeEach
    void setUp() {
        clock = new MovableClock(Instant.parse("2026-11-23T10:00:00Z"));
        limit = new SignUpLimit(new SignUpLimitProperties(3, 5, Duration.ofHours(1), 2), clock);
    }

    private void reserve(String ip, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(limit.tryReserve(ip)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Por IP")
    class PerIp {

        @Test
        @DisplayName("Una IP puede dar 3 altas; la cuarta se rechaza con el tiempo que falta para que caduque la más antigua")
        void theFourthSignUpFromAnIpIsRejected() {
            limit.tryReserve(IP);
            clock.advance(Duration.ofMinutes(10));
            reserve(IP, 2);

            assertThat(limit.tryReserve(IP)).contains(Duration.ofMinutes(50));
        }

        @Test
        @DisplayName("Pasada la ventana desde el alta más antigua, la IP puede volver a darse de alta")
        void afterTheWindowTheIpCanSignUpAgain() {
            reserve(IP, 3);
            clock.advance(Duration.ofHours(1));

            assertThat(limit.tryReserve(IP)).isEmpty();
        }

        @Test
        @DisplayName("Un alta rechazada no se anota: no alarga el bloqueo")
        void aRejectedSignUpIsNotRecorded() {
            reserve(IP, 3);
            clock.advance(Duration.ofMinutes(30));
            limit.tryReserve(IP);
            clock.advance(Duration.ofMinutes(30));

            assertThat(limit.tryReserve(IP)).isEmpty();
        }

        @Test
        @DisplayName("Una reserva devuelta, porque el alta falló, no cuenta")
        void aReturnedReservationDoesNotCount() {
            reserve(IP, 3);
            limit.release(IP);

            assertThat(limit.tryReserve(IP)).isEmpty();
        }

        @Test
        @DisplayName("Las IP son independientes mientras queda cupo global")
        void ipsAreIndependent() {
            reserve(IP, 3);

            assertThat(limit.tryReserve(OTHER_IP)).isEmpty();
        }

        @Test
        @DisplayName("Consultar el límite no reserva nada")
        void checkingTheLimitReservesNothing() {
            reserve(IP, 2);
            assertThat(limit.lockedFor(IP)).isEmpty();
            assertThat(limit.lockedFor(IP)).isEmpty();

            assertThat(limit.tryReserve(IP)).isEmpty();
            assertThat(limit.lockedFor(IP)).contains(Duration.ofHours(1));
        }
    }

    @Nested
    @DisplayName("En total")
    class Total {

        @Test
        @DisplayName("Lleno el cupo global con IP distintas, se rechaza cualquier IP, aunque no haya dado ningún alta")
        void whenTheGlobalQuotaIsFullAnyIpIsRejected() {
            for (int i = 1; i <= 5; i++) {
                assertThat(limit.tryReserve("198.51.100." + i)).isEmpty();
                clock.advance(Duration.ofMinutes(1));
            }

            assertThat(limit.tryReserve(IP)).contains(Duration.ofMinutes(55));
            assertThat(limit.lockedFor(IP)).contains(Duration.ofMinutes(55));
        }

        @Test
        @DisplayName("Una reserva devuelta libera también el cupo global")
        void aReturnedReservationAlsoFreesTheGlobalQuota() {
            for (int i = 1; i <= 5; i++) {
                limit.tryReserve("198.51.100." + i);
            }
            limit.release("198.51.100.5");

            assertThat(limit.tryReserve(IP)).isEmpty();
        }

        @Test
        @DisplayName("Con los dos límites llenos, el tiempo que falta es el más largo")
        void withBothLimitsFullTheWaitIsTheLongest() {
            limit.tryReserve("198.51.100.1");
            limit.tryReserve("198.51.100.2");
            clock.advance(Duration.ofMinutes(20));
            reserve(IP, 3);

            assertThat(limit.tryReserve(IP)).contains(Duration.ofHours(1));
        }

        @Test
        @DisplayName("Pasada la ventana, el cupo global se libera")
        void afterTheWindowTheGlobalQuotaIsFreed() {
            for (int i = 1; i <= 5; i++) {
                limit.tryReserve("198.51.100." + i);
            }
            clock.advance(Duration.ofHours(1));

            assertThat(limit.tryReserve(IP)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Fallos que revelan datos (#139)")
    class Failures {

        @Test
        @DisplayName("Tras 2 fallos, la IP no puede reservar un alta hasta que caduque el más antiguo, y no se reserva nada")
        void afterTheFailuresTheIpCannotReserve() {
            limit.recordFailure(IP);
            clock.advance(Duration.ofMinutes(15));
            limit.recordFailure(IP);

            assertThat(limit.lockedFor(IP)).contains(Duration.ofMinutes(45));
            assertThat(limit.tryReserve(IP)).contains(Duration.ofMinutes(45));
            clock.advance(Duration.ofMinutes(45));
            assertThat(limit.tryReserve(IP)).isEmpty();
        }

        @Test
        @DisplayName("Un solo fallo no bloquea, y los fallos de una IP no afectan a otra")
        void oneFailureDoesNotLockAndIpsAreIndependent() {
            limit.recordFailure(IP);
            assertThat(limit.tryReserve(IP)).isEmpty();

            limit.recordFailure(IP);
            assertThat(limit.tryReserve(OTHER_IP)).isEmpty();
        }

        @Test
        @DisplayName("Solo cuentan los últimos fallos: con 3 fallos y un máximo de 2, el bloqueo acaba cuando caduca el segundo")
        void onlyTheLatestFailuresCount() {
            limit.recordFailure(IP);
            clock.advance(Duration.ofMinutes(10));
            limit.recordFailure(IP);
            clock.advance(Duration.ofMinutes(10));
            limit.recordFailure(IP);

            assertThat(limit.lockedFor(IP)).contains(Duration.ofMinutes(50));
        }

        @Test
        @DisplayName("Pasada la ventana desde el último fallo, la IP vuelve a poder darse de alta")
        void afterTheWindowTheFailuresExpire() {
            limit.recordFailure(IP);
            limit.recordFailure(IP);
            clock.advance(Duration.ofHours(1));

            assertThat(limit.lockedFor(IP)).isEmpty();
            assertThat(limit.tryReserve(IP)).isEmpty();
        }

        @Test
        @DisplayName("Los fallos no gastan el cupo global de altas")
        void failuresDoNotUseTheGlobalQuota() {
            for (int i = 1; i <= 5; i++) {
                limit.recordFailure("198.51.100." + i);
            }

            assertThat(limit.tryReserve(IP)).isEmpty();
        }

        @Test
        @DisplayName("Con los fallos y las altas de la IP llenos, el tiempo que falta es el más largo")
        void withFailuresAndSignUpsFullTheWaitIsTheLongest() {
            limit.recordFailure(IP);
            limit.recordFailure(IP);
            clock.advance(Duration.ofMinutes(50));
            assertThat(limit.lockedFor(IP)).contains(Duration.ofMinutes(10));

            clock.advance(Duration.ofMinutes(10));
            reserve(IP, 3);
            limit.recordFailure(IP);
            limit.recordFailure(IP);

            assertThat(limit.lockedFor(IP)).contains(Duration.ofHours(1));
        }
    }

    @Nested
    @DisplayName("Configuración")
    class Configuration {

        @Test
        @DisplayName("Sin valores, 3 altas por IP y 50 en total por hora")
        void defaultValues() {
            SignUpLimitProperties properties = new SignUpLimitProperties(null, null, null, null);

            assertThat(properties.maxPerIp()).isEqualTo(3);
            assertThat(properties.maxTotal()).isEqualTo(50);
            assertThat(properties.window()).isEqualTo(Duration.ofHours(1));
            assertThat(properties.maxFailuresPerIp()).isEqualTo(10);
        }

        @Test
        @DisplayName("Los límites y la ventana deben ser positivos")
        void theLimitsMustBePositive() {
            assertThatIllegalStateException().isThrownBy(() -> new SignUpLimitProperties(0, 50, Duration.ofHours(1), 10));
            assertThatIllegalStateException().isThrownBy(() -> new SignUpLimitProperties(3, 0, Duration.ofHours(1), 10));
            assertThatIllegalStateException().isThrownBy(() -> new SignUpLimitProperties(3, 50, Duration.ZERO, 10));
            assertThatIllegalStateException().isThrownBy(() -> new SignUpLimitProperties(3, 50, Duration.ofHours(1), 0));
        }

        @Test
        @DisplayName("El cupo global no puede ser menor que el de una IP")
        void theGlobalQuotaCannotBeLowerThanThePerIpOne() {
            assertThatIllegalStateException().isThrownBy(() -> new SignUpLimitProperties(10, 5, Duration.ofHours(1), 10));
        }
    }

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
