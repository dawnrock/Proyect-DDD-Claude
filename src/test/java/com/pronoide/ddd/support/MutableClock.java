package com.pronoide.ddd.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj de pruebas que se puede adelantar: {@code clock.advance(Duration.ofDays(32))}. Empieza en el instante real. */
public final class MutableClock extends Clock {

    private Instant now = Instant.now();

    public void advance(Duration duration) {
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
