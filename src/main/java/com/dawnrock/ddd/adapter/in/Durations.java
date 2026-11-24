package com.dawnrock.ddd.adapter.in;

import java.time.Duration;

/**
 * Tiempos de espera que se enseñan a quien los sufre (minutos de un bloqueo en la web, segundos de {@code Retry-After}
 * en la API): redondeados hacia arriba, para no prometer antes de tiempo, y nunca menos de uno.
 */
public final class Durations {

    private static final long AT_LEAST_ONE = 1;

    private Durations() {
    }

    /** {@code 30 min} → 30, {@code 30 min 1 s} → 31, {@code 20 s} → 1. */
    public static long minutesRoundedUp(Duration duration) {
        long minutes = duration.toMinutes();
        boolean partialMinute = duration.compareTo(Duration.ofMinutes(minutes)) > 0;
        return Math.max(AT_LEAST_ONE, partialMinute ? minutes + 1 : minutes);
    }

    /** {@code 91 s} → 91, {@code 90,001 s} → 91, {@code 0,2 s} → 1. */
    public static long secondsRoundedUp(Duration duration) {
        long seconds = duration.toSeconds();
        boolean partialSecond = duration.compareTo(Duration.ofSeconds(seconds)) > 0;
        return Math.max(AT_LEAST_ONE, partialSecond ? seconds + 1 : seconds);
    }
}
