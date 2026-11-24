package com.dawnrock.ddd.adapter.out.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.dawnrock.ddd.domain.model.DomainEvent;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.domain.model.MoneyDeposited;
import com.dawnrock.ddd.domain.model.MoneyWithdrawn;

@DisplayName("Publicador de eventos sobre el outbox")
class OutboxDomainEventPublisherTests {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    private static final UUID ACCOUNT = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final MonetaryAmount AMOUNT = new MonetaryAmount(new BigDecimal("10.00"), Currency.getInstance("EUR"));

    private final OutboxEventJpaRepository repository = OutboxTestDatabase.bean(OutboxEventJpaRepository.class);
    private final TransactionTemplate transactions = OutboxTestDatabase.bean(TransactionTemplate.class);
    private final DomainEventTypes types = new DomainEventTypes();
    private final OutboxDomainEventPublisher publisher =
            new OutboxDomainEventPublisher(repository, types, Clock.fixed(NOW, ZoneOffset.UTC));

    private final DomainEvent deposited = new MoneyDeposited(ACCOUNT, UUID.randomUUID(), AMOUNT, NOW.minusSeconds(5));
    private final DomainEvent withdrawn = new MoneyWithdrawn(ACCOUNT, UUID.randomUUID(), AMOUNT, NOW.minusSeconds(4));

    @BeforeEach
    void setUp() {
        OutboxTestDatabase.clear();
    }

    @Test
    @DisplayName("Dentro de una transacción que se confirma, deja una fila pendiente por evento, en orden, con su tipo, su cuenta y su JSON")
    void insideACommittedTransactionItLeavesOnePendingRowPerEventInOrder() {
        transactions.executeWithoutResult(status -> publisher.publishAll(List.of(deposited, withdrawn)));

        List<OutboxEventEntity> rows = OutboxTestDatabase.inTransaction(() -> repository.findAll().stream()
                .sorted((a, b) -> Long.compare(a.id, b.id))
                .toList());
        assertThat(rows).extracting(row -> row.eventType).containsExactly("MoneyDeposited", "MoneyWithdrawn");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.accountId).isEqualTo(ACCOUNT);
            assertThat(row.recordedAt).isEqualTo(NOW);
            assertThat(row.publishedAt).isNull();
        });
        assertThat(types.fromJson(rows.get(0).eventType, rows.get(0).payload)).isEqualTo(deposited);
        assertThat(rows.get(0).occurredOn).isEqualTo(deposited.occurredOn());
    }

    @Test
    @DisplayName("Dentro de una transacción que se deshace, no deja ninguna fila")
    void insideARolledBackTransactionItLeavesNoRow() {
        transactions.executeWithoutResult(status -> {
            publisher.publish(deposited);
            status.setRollbackOnly();
        });

        assertThat(OutboxTestDatabase.inTransaction(() -> repository.count())).isZero();
    }

    @Test
    @DisplayName("Fuera de una transacción falla: el evento se perdería si el agregado no llega a guardarse")
    void outsideATransactionItFails() {
        assertThatExceptionOfType(IllegalTransactionStateException.class).isThrownBy(() -> publisher.publish(deposited));
        assertThat(OutboxTestDatabase.inTransaction(() -> repository.count())).isZero();
    }
}
