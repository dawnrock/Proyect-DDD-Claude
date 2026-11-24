package com.dawnrock.ddd.adapter.out.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

@DisplayName("Repositorio del outbox de eventos")
class OutboxEventJpaRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    private final OutboxEventJpaRepository repository = OutboxTestDatabase.bean(OutboxEventJpaRepository.class);

    @BeforeEach
    void setUp() {
        OutboxTestDatabase.clear();
    }

    @Test
    @DisplayName("Los eventos pendientes se leen en el orden en que se guardaron, hasta el límite, sin los publicados ni los fallidos")
    void pendingEventsAreReadInOrderUpToTheLimitSkippingPublishedAndFailedOnes() {
        OutboxEventEntity first = save(row("MoneyDeposited"));
        OutboxEventEntity published = row("MoneyDeposited");
        published.markPublished(NOW);
        save(published);
        OutboxEventEntity failed = row("MoneyWithdrawn");
        failed.recordFailedAttempt("el consumidor falló", 1, NOW);
        save(failed);
        OutboxEventEntity second = save(row("MoneyWithdrawn"));
        save(row("WithdrawalRejected"));

        List<OutboxEventEntity> pending = OutboxTestDatabase.inTransaction(() ->
                repository.findByPublishedAtIsNullAndFailedAtIsNullOrderByIdAsc(Limit.of(2)));

        assertThat(pending).extracting(event -> event.id).containsExactly(first.id, second.id);
        assertThat(pending.get(0).eventType).isEqualTo("MoneyDeposited");
        assertThat(pending.get(0).attempts).isZero();
    }

    private OutboxEventEntity save(OutboxEventEntity row) {
        return OutboxTestDatabase.inTransaction(() -> repository.save(row));
    }

    private static OutboxEventEntity row(String type) {
        return new OutboxEventEntity(type, UUID.randomUUID(), "{}", NOW, NOW);
    }
}
