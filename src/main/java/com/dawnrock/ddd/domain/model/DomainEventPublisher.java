package com.dawnrock.ddd.domain.model;

import java.util.List;

/**
 * Puerto de salida de los eventos de dominio: los entrega a quien quiera reaccionar a ellos, sin que el núcleo sepa
 * cómo. Lo usa la aplicación después de guardar el agregado y dentro de la misma unidad de trabajo, para que un outbox
 * comparta su transacción (#165, ADR 0021); lo implementa un adaptador de {@code adapter.out.event}.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);

    /** Publica los eventos en el orden en que ocurrieron. */
    default void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }
}
