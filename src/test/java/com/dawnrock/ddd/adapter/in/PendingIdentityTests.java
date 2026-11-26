package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PendingIdentityTests {

    private static final Instant CREATED = Instant.parse("2026-11-26T10:00:00Z");

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static PendingIdentity pending(String email) {
        return new PendingIdentity(ExternalIdentities.AUTH0, "auth0|1", email, CREATED);
    }

    @Test
    @DisplayName("Una identidad pendiente vale 10 minutos")
    void aPendingIdentityIsValidForTenMinutes() {
        assertThat(PendingIdentity.VALIDITY).isEqualTo(Duration.ofMinutes(10));
        assertThat(pending("ada@example.com").isExpired(at(CREATED))).isFalse();
        assertThat(pending("ada@example.com").isExpired(at(CREATED.plus(Duration.ofMinutes(10)).minusSeconds(1))))
                .isFalse();
    }

    @Test
    @DisplayName("Caduca al cumplirse los 10 minutos")
    void itExpiresWhenTenMinutesHavePassed() {
        assertThat(pending("ada@example.com").isExpired(at(CREATED.plus(PendingIdentity.VALIDITY)))).isTrue();
        assertThat(pending("ada@example.com").isExpired(at(CREATED.plus(Duration.ofHours(1))))).isTrue();
    }

    @Test
    @DisplayName("No valida nada: el correo puede ser nulo")
    void itValidatesNothingSoTheEmailMayBeNull() {
        assertThat(pending(null).email()).isNull();
    }
}
