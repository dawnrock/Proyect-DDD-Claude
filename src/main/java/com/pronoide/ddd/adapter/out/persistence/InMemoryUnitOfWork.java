package com.pronoide.ddd.adapter.out.persistence;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.pronoide.ddd.application.UnitOfWork;

/**
 * Adaptador de salida en memoria (simulado) del puerto {@link UnitOfWork}: un único cerrojo justo para todas las
 * unidades, así que los casos de uso se ejecutan de uno en uno. Basta para el repositorio en memoria, que entrega la
 * misma cuenta a todos; una base de datos real usaría una transacción y bloquearía solo las filas implicadas (#78).
 *
 * <p>Es reentrante: una unidad que abre otra dentro no se bloquea. No deshace cambios: si un caso de uso fallara a
 * medias, lo ya cambiado en las cuentas se queda (el repositorio guarda referencias, no copias).
 */
@Component
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
