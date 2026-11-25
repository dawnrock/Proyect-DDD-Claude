package com.dawnrock.ddd.application;

import java.util.ArrayList;
import java.util.List;

import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.DomainEvent;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;

/**
 * Las cuentas que guarda un caso de uso dentro de su {@link AccountUnitOfWork}, con los eventos que han registrado,
 * pendientes de publicar al final de la unidad: un lote por cuenta guardada.
 */
final class AccountChanges {

    private final CheckingAccountRepository repository;
    private final List<List<DomainEvent>> batches = new ArrayList<>();

    AccountChanges(CheckingAccountRepository repository) {
        this.repository = repository;
    }

    /** Guarda la cuenta y recoge los eventos que ha registrado, para publicarlos al final de la unidad. */
    CheckingAccount save(CheckingAccount account) {
        CheckingAccount saved = repository.save(account);
        batches.add(account.pullDomainEvents());
        return saved;
    }

    void publishTo(DomainEventPublisher publisher) {
        batches.forEach(publisher::publishAll);
    }
}
