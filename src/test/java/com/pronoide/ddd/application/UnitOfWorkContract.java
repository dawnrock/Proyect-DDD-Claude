package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato del puerto {@link UnitOfWork}: lo hereda el test de cada implementación, que solo dice cómo crearla.
 */
public abstract class UnitOfWorkContract {

    protected abstract UnitOfWork unitOfWork();

    @Test
    @DisplayName("La unidad de trabajo ejecuta el trabajo y devuelve su resultado")
    void la_unidad_ejecuta_el_trabajo_y_devuelve_su_resultado() {
        assertThat(unitOfWork().execute(() -> "hecho")).isEqualTo("hecho");
    }

    @Test
    @DisplayName("Si el trabajo falla, la excepción se propaga y la siguiente unidad se ejecuta")
    void si_el_trabajo_falla_la_excepcion_se_propaga_y_la_siguiente_unidad_se_ejecuta() {
        UnitOfWork unit = unitOfWork();

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> unit.execute(() -> {
                    throw new IllegalStateException("fallo");
                }));

        assertThat(unit.execute(() -> 42)).isEqualTo(42);
    }

    @Test
    @DisplayName("Una unidad abierta dentro de otra en el mismo hilo no se bloquea")
    void una_unidad_dentro_de_otra_no_se_bloquea() throws Exception {
        UnitOfWork unit = unitOfWork();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> nested = pool.submit(() -> unit.execute(() -> unit.execute(() -> "anidada")));
            assertThat(nested.get(5, TimeUnit.SECONDS)).isEqualTo("anidada");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Mientras una unidad se ejecuta, otra lanzada desde otro hilo espera a que termine")
    void dos_unidades_no_se_solapan() throws Exception {
        UnitOfWork unit = unitOfWork();
        CountDownLatch firstInside = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean firstFinished = new AtomicBoolean(false);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> unit.execute(() -> {
                firstInside.countDown();
                await(releaseFirst);
                firstFinished.set(true);
                return null;
            }));
            assertThat(firstInside.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> second = pool.submit(() -> unit.execute(firstFinished::get));
            Thread.sleep(200);
            assertThat(second.isDone()).as("la segunda unidad no entra mientras la primera sigue dentro").isFalse();

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertThat(second.get(5, TimeUnit.SECONDS)).as("la segunda vio terminada la primera").isTrue();
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
