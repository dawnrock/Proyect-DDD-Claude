package com.pronoide.ddd.adapter.out.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.pronoide.ddd.domain.model.DomainEvent;
import com.pronoide.ddd.domain.model.DomainEventPublisher;

import lombok.RequiredArgsConstructor;

/**
 * Adaptador de salida del puerto {@link DomainEventPublisher} sobre los eventos de Spring: cada evento de dominio llega
 * a los métodos {@code @EventListener} que lo escuchan ({@code application.listener}), en el mismo hilo.
 */
@Component
@RequiredArgsConstructor
class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher spring;

    @Override
    public void publish(DomainEvent event) {
        spring.publishEvent(event);
    }
}
