package com.pronoide.ddd.application.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.pronoide.ddd.domain.model.DomainEvent;

import lombok.extern.slf4j.Slf4j;

/** Auditoría (simulada en el log): anota todos los eventos de dominio, con su tipo, su cuenta y su instante. */
@Slf4j
@Component
public class AuditLogListener {

    @EventListener
    public void on(DomainEvent event) {
        log.info("Auditoría · {} · cuenta {} · {}", event.getClass().getSimpleName(), event.accountId(),
                event.occurredOn());
    }
}
