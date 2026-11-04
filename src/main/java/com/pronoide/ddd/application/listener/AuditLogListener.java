package com.pronoide.ddd.application.listener;

import org.springframework.context.event.EventListener;

import com.pronoide.ddd.domain.model.DomainEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Auditoría (simulada en el log): anota todos los eventos de dominio, con su tipo, su cuenta y su instante.
 *
 * <p>La declara {@code config.DomainConfiguration} (#20). Conserva {@code @EventListener}, la única dependencia de
 * Spring permitida en {@code application}: los eventos le llegan por el mecanismo de eventos de Spring
 * ({@code SpringDomainEventPublisher}), y quitarla obligaría a reimplementarlo.
 */
@Slf4j
public class AuditLogListener {

    @EventListener
    public void on(DomainEvent event) {
        log.info("Auditoría · {} · cuenta {} · {}", event.getClass().getSimpleName(), event.accountId(),
                event.occurredOn());
    }
}
