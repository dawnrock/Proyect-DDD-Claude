package com.pronoide.ddd.adapter.out.persistence;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.pronoide.ddd.application.UnitOfWork;

/**
 * Adaptador de salida del puerto {@link UnitOfWork} sobre una transacción de la base (#150, ADR 0019): si el caso de
 * uso falla, lo que guardó se deshace. Las unidades siguen pasando de una en una por un cerrojo justo, como en #78:
 * basta con una sola instancia (Render) y evita los interbloqueos de dos transferencias cruzadas que tendría bloquear
 * solo las filas implicadas. Es reentrante: una unidad abierta dentro de otra se une a su transacción.
 */
@Component
class TransactionalUnitOfWork implements UnitOfWork {

    private final ReentrantLock lock = new ReentrantLock(true);
    private final TransactionTemplate transaction;

    TransactionalUnitOfWork(PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        lock.lock();
        try {
            return transaction.execute(status -> work.get());
        } finally {
            lock.unlock();
        }
    }
}
