package com.dawnrock.ddd.adapter.out.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.dawnrock.ddd.domain.model.DomainEvent;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.model.MoneyDeposited;
import com.dawnrock.ddd.domain.model.MoneyWithdrawn;
import com.dawnrock.ddd.domain.model.MonetaryAmount;

/**
 * El adaptador de eventos con el bus de eventos real de Spring y un oyente de prueba, sin la aplicación ni el
 * hexágono: el contexto solo tiene el adaptador y el oyente.
 */
@SpringJUnitConfig(SpringDomainEventPublisherContextTests.EventsOnly.class)
class SpringDomainEventPublisherContextTests {

    @Configuration
    @Import(SpringDomainEventPublisher.class)
    static class EventsOnly {

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    /** Oyente de prueba: anota los eventos de dominio que recibe y, aparte, solo los ingresos. */
    static class RecordingListener {

        final List<DomainEvent> received = new ArrayList<>();
        final List<MoneyDeposited> deposits = new ArrayList<>();

        @EventListener
        void on(DomainEvent event) {
            received.add(event);
        }

        @EventListener
        void onDeposit(MoneyDeposited event) {
            deposits.add(event);
        }
    }

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private RecordingListener listener;

    private final UUID accountId = UUID.randomUUID();
    private final MoneyDeposited deposit =
            new MoneyDeposited(accountId, UUID.randomUUID(), MonetaryAmount.euros("50.00"), Instant.now());
    private final MoneyWithdrawn withdrawal =
            new MoneyWithdrawn(accountId, UUID.randomUUID(), MonetaryAmount.euros("20.00"), Instant.now());

    @BeforeEach
    void clearListener() {
        listener.received.clear();
        listener.deposits.clear();
    }

    @Test
    @DisplayName("Los eventos publicados por el puerto llegan al oyente de Spring en el orden en que ocurrieron")
    void publishedEventsReachTheListenerInOrder() {
        publisher.publishAll(List.of(deposit, withdrawal));

        assertThat(listener.received).containsExactly(deposit, withdrawal);
    }

    @Test
    @DisplayName("Un oyente de un tipo de evento solo recibe ese tipo")
    void aListenerForOneEventTypeOnlyReceivesThatType() {
        publisher.publishAll(List.of(deposit, withdrawal));

        assertThat(listener.deposits).containsExactly(deposit);
    }
}
