package com.dawnrock.ddd.adapter.out.event;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dawnrock.ddd.domain.model.DomainEvent;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;

import lombok.RequiredArgsConstructor;

/**
 * Adaptador de salida del puerto {@link DomainEventPublisher} sobre el outbox (#165, ADR 0021): cada evento se guarda
 * como una fila pendiente en la misma transacción que su agregado, y {@link OutboxRelay} lo entrega después a los
 * consumidores. Si la transacción se deshace, el evento desaparece con ella; si se confirma, el evento sobrevive aunque
 * el proceso caiga antes de entregarlo. Sustituye a la entrega directa por los eventos de Spring.
 */
@Component
@RequiredArgsConstructor
class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxEventJpaRepository repository;
    private final DomainEventTypes types;
    private final Clock clock;

    /**
     * @throws IllegalTransactionStateException si no hay una transacción en curso: el evento se guardaría aunque su
     *                                          agregado no llegara a guardarse (lo publica la unidad de trabajo)
     */
    @Override
    public void publish(DomainEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException(
                    "Los eventos de dominio se publican dentro de la unidad de trabajo, con su agregado");
        }
        repository.save(new OutboxEventEntity(types.typeOf(event), event.accountId(), types.toJson(event),
                event.occurredOn(), clock.instant()));
    }
}
