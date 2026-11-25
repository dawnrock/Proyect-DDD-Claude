package com.dawnrock.ddd.application;

import java.util.function.Function;

import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;

/**
 * Ejecuta un caso de uso de cuentas dentro de la {@link UnitOfWork} (#78) y, al final del trabajo y todavía dentro de
 * ella, publica los eventos de cada cuenta guardada con {@link AccountChanges#save}: una llamada por cuenta y en el
 * orden en que se guardaron. Así el outbox los guarda en la misma transacción que las cuentas (#165, ADR 0021): si la
 * unidad no termina bien, desaparecen con ella. Si el trabajo falla antes, no publica nada. Lo comparten los servicios
 * de aplicación que cambian cuentas (#174).
 */
final class AccountUnitOfWork {

    private final UnitOfWork unitOfWork;
    private final CheckingAccountRepository repository;
    private final DomainEventPublisher publisher;

    AccountUnitOfWork(UnitOfWork unitOfWork, CheckingAccountRepository repository, DomainEventPublisher publisher) {
        this.unitOfWork = unitOfWork;
        this.repository = repository;
        this.publisher = publisher;
    }

    <T> T execute(Function<AccountChanges, T> work) {
        return unitOfWork.execute(() -> {
            AccountChanges changes = new AccountChanges(repository);
            T result = work.apply(changes);
            changes.publishTo(publisher);
            return result;
        });
    }
}
