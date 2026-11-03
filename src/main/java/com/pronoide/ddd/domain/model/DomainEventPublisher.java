package com.pronoide.ddd.domain.model;

import java.util.List;

/**
 * Puerto de salida de los eventos de dominio: los entrega a quien quiera reaccionar a ellos, sin que el núcleo sepa
 * cómo. Lo usa la aplicación después de guardar el agregado; lo implementa un adaptador de {@code adapter.out.event}.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);

    /** Publica los eventos en el orden en que ocurrieron. */
    default void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }
}
