package com.dawnrock.ddd.adapter.out.persistence;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.dawnrock.ddd.application.UnitOfWork;

/**
 * Solo para tests (#150, ADR 0019): la aplicación usa el adaptador JPA. Cumple el mismo contrato del puerto.
 *
 * <p>Doble de test en memoria del puerto {@link UnitOfWork}: un único cerrojo justo para todas las
 * unidades, así que los casos de uso se ejecutan de uno en uno. Basta para el repositorio en memoria, que entrega la
 * misma cuenta a todos; una base de datos real usaría una transacción y bloquearía solo las filas implicadas (#78).
 *
 * <p>Es reentrante: una unidad que abre otra dentro no se bloquea. No deshace cambios: si un caso de uso fallara a
 * medias, lo ya cambiado en las cuentas se queda (el repositorio guarda referencias, no copias).
 */
class InMemoryUnitOfWork implements UnitOfWork {

    private final ReentrantLock lock = new ReentrantLock(true);

    @Override
    public <T> T execute(Supplier<T> work) {
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }
}
