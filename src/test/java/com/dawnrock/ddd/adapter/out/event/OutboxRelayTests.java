package com.dawnrock.ddd.adapter.out.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import com.dawnrock.ddd.domain.model.DomainEvent;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.domain.model.MoneyDeposited;
import com.dawnrock.ddd.domain.model.MoneyWithdrawn;

@DisplayName("Relé del outbox")
class OutboxRelayTests {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    private static final MonetaryAmount AMOUNT = new MonetaryAmount(new BigDecimal("10.00"), Currency.getInstance("EUR"));
    private static final int MAX_ATTEMPTS = 3;

    private final OutboxEventJpaRepository repository = OutboxTestDatabase.bean(OutboxEventJpaRepository.class);
    private final DomainEventTypes types = new DomainEventTypes();
    private final ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
    private final OutboxRelay relay = new OutboxRelay(repository, types, spring,
            new OutboxProperties(null, MAX_ATTEMPTS, null), Clock.fixed(NOW, ZoneOffset.UTC),
            OutboxTestDatabase.bean(TransactionTemplate.class));

    @BeforeEach
    void setUp() {
        OutboxTestDatabase.clear();
    }

    @Test
    @DisplayName("Entrega los eventos pendientes en orden y los marca como publicados")
    void deliversPendingEventsInOrderAndMarksThemPublished() {
        DomainEvent first = deposited();
        DomainEvent second = withdrawn();
        Long firstId = store(first);
        Long secondId = store(second);

        relay.relayPending();

        InOrder order = inOrder(spring);
        order.verify(spring).publishEvent(first);
        order.verify(spring).publishEvent(second);
        assertThat(row(firstId).publishedAt).isEqualTo(NOW);
        assertThat(row(secondId).publishedAt).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Un evento ya publicado no se vuelve a entregar")
    void aPublishedEventIsNotDeliveredAgain() {
        store(deposited());
        relay.relayPending();

        relay.relayPending();

        verify(spring, times(1)).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Si un consumidor falla, los demás eventos se entregan y el fallido se reintenta en la siguiente pasada")
    void whenAConsumerFailsTheOthersAreDeliveredAndTheFailedOneIsRetried() {
        DomainEvent failing = deposited();
        DomainEvent other = withdrawn();
        Long failingId = store(failing);
        Long otherId = store(other);
        willThrow(new IllegalStateException("el consumidor no está disponible")).given(spring).publishEvent(failing);

        relay.relayPending();

        OutboxEventEntity failed = row(failingId);
        assertThat(failed.publishedAt).isNull();
        assertThat(failed.attempts).isEqualTo(1);
        assertThat(failed.lastError).contains("el consumidor no está disponible");
        assertThat(row(otherId).publishedAt).isEqualTo(NOW);

        reset(spring);
        relay.relayPending();

        verify(spring).publishEvent(failing);
        assertThat(row(failingId).publishedAt).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Tras el máximo de intentos fallidos el evento se marca como fallido y no se vuelve a intentar")
    void afterTheMaximumFailedAttemptsTheEventIsMarkedFailedAndNotRetried() {
        DomainEvent failing = deposited();
        Long id = store(failing);
        willThrow(new IllegalStateException("siempre falla")).given(spring).publishEvent(failing);

        for (int attempt = 0; attempt < MAX_ATTEMPTS + 2; attempt++) {
            relay.relayPending();
        }

        verify(spring, times(MAX_ATTEMPTS)).publishEvent(failing);
        OutboxEventEntity failed = row(id);
        assertThat(failed.attempts).isEqualTo(MAX_ATTEMPTS);
        assertThat(failed.failedAt).isEqualTo(NOW);
        assertThat(failed.publishedAt).isNull();
        verify(spring, never()).publishEvent(withdrawn());
    }

    @Test
    @DisplayName("La limpieza borra los eventos publicados hace más de la retención y conserva los recientes, los pendientes y los fallidos")
    void theCleanUpDeletesOldPublishedEventsAndKeepsTheRest() {
        Long old = store(deposited());
        Long recent = store(withdrawn());
        Long pending = store(deposited());
        Long failed = store(withdrawn());
        OutboxTestDatabase.inTransaction(() -> {
            repository.findById(old).orElseThrow().markPublished(NOW.minus(Duration.ofDays(8)));
            repository.findById(recent).orElseThrow().markPublished(NOW.minus(Duration.ofDays(1)));
            repository.findById(failed).orElseThrow().recordFailedAttempt("falló", 1, NOW.minus(Duration.ofDays(30)));
            return null;
        });

        relay.cleanUp();

        assertThat(OutboxTestDatabase.inTransaction(() -> repository.findAll()))
                .extracting(row -> row.id)
                .containsExactlyInAnyOrder(recent, pending, failed);
    }

    private Long store(DomainEvent event) {
        return OutboxTestDatabase.inTransaction(() -> repository.save(new OutboxEventEntity(types.typeOf(event),
                event.accountId(), types.toJson(event), event.occurredOn(), NOW)).id);
    }

    private OutboxEventEntity row(Long id) {
        return OutboxTestDatabase.inTransaction(() -> repository.findById(id).orElseThrow());
    }

    private static MoneyDeposited deposited() {
        return new MoneyDeposited(UUID.fromString("11111111-2222-3333-4444-555555555555"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"), AMOUNT, NOW);
    }

    private static MoneyWithdrawn withdrawn() {
        return new MoneyWithdrawn(UUID.fromString("11111111-2222-3333-4444-555555555555"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"), AMOUNT, NOW);
    }
}
