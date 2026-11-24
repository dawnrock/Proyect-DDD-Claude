package com.dawnrock.ddd.adapter.out.event;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.dawnrock.ddd.domain.model.DomainEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Relé del outbox de eventos (#165, ADR 0021): cada pocos segundos lee los eventos pendientes en el orden en que se
 * guardaron y los entrega a los consumidores ({@code @EventListener} de {@code application.listener}) por los eventos
 * de Spring. Cada evento se entrega y se marca en su propia transacción: si un consumidor falla, se cuenta el intento,
 * el resto sigue y ese evento se reintenta en la pasada siguiente, hasta {@code maxAttempts}. Entrega al menos una
 * vez: si el proceso cae entre la entrega y la marca, el evento se entrega otra vez.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxRelay {

    private final OutboxEventJpaRepository repository;
    private final DomainEventTypes types;
    private final ApplicationEventPublisher spring;
    private final OutboxProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    /** Una sola pasada a la vez, también si un test la llama mientras corre la programada. */
    private final ReentrantLock lock = new ReentrantLock();

    @Scheduled(fixedDelayString = "${app.events.outbox.relay-interval:2s}")
    public void relayPending() {
        lock.lock();
        try {
            List<Long> pending = transactions.execute(status -> repository
                    .findByPublishedAtIsNullAndFailedAtIsNullOrderByIdAsc(Limit.of(properties.batchSize()))
                    .stream()
                    .map(row -> row.id)
                    .toList());
            if (pending != null) {
                pending.forEach(this::deliver);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cada día borra los eventos publicados hace más de la retención (7 días por defecto). Los fallidos se quedan para
     * poder revisarlos.
     */
    @Scheduled(cron = "${app.events.outbox.clean-up-cron:0 30 3 * * *}")
    public void cleanUp() {
        Long deleted = transactions.execute(status ->
                repository.deleteByPublishedAtBefore(clock.instant().minus(properties.retention())));
        log.info("Outbox · limpieza: {} eventos publicados borrados", deleted);
    }

    private void deliver(Long id) {
        transactions.executeWithoutResult(status -> repository.findById(id).ifPresent(row -> {
            try {
                DomainEvent event = types.fromJson(row.eventType, row.payload);
                spring.publishEvent(event);
                row.markPublished(clock.instant());
            } catch (RuntimeException e) {
                row.recordFailedAttempt(e.toString(), properties.maxAttempts(), clock.instant());
                if (row.failedAt != null) {
                    log.error("Outbox · el evento {} {} falla tras {} intentos y no se reintentará: {}", row.id,
                            row.eventType, row.attempts, e.toString());
                } else {
                    log.warn("Outbox · el evento {} {} ha fallado (intento {}), se reintentará: {}", row.id,
                            row.eventType, row.attempts, e.toString());
                }
            }
        }));
    }
}
