package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Tiempos de espera redondeados hacia arriba")
class DurationsTests {

    @Nested
    @DisplayName("En minutos")
    class MinutesRoundedUp {

        @ParameterizedTest(name = "{0} → {1} min")
        @CsvSource({
            "PT30M, 30",
            "PT30M1S, 31",
            "PT20S, 1",
            "PT0S, 1",
        })
        @DisplayName("Los minutos se redondean hacia arriba y nunca son menos de uno")
        void minutesAreRoundedUpAndNeverLessThanOne(Duration duration, long expected) {
            assertThat(Durations.minutesRoundedUp(duration)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("En segundos")
    class SecondsRoundedUp {

        @ParameterizedTest(name = "{0} → {1} s")
        @CsvSource({
            "PT1M31S, 91",
            "PT1M30.001S, 91",
            "PT0.2S, 1",
            "PT0S, 1",
        })
        @DisplayName("Los segundos se redondean hacia arriba y nunca son menos de uno")
        void secondsAreRoundedUpAndNeverLessThanOne(Duration duration, long expected) {
            assertThat(Durations.secondsRoundedUp(duration)).isEqualTo(expected);
        }
    }
}
