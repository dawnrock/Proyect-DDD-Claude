package com.dawnrock.ddd.application;

/**
 * Puerto de entrada de la revisión periódica de segmentos (#98, #174). Es un proceso interno, sin {@link Caller}: lo
 * dispara la tarea programada ({@code adapter.in.scheduler}), que depende de este puerto y no de la clase que lo
 * implementa.
 */
public interface SegmentReviewCommands {

    /**
     * Revisa el segmento de todas las cuentas a las que les toca: vuelve a verificar los ingresos del titular y lo
     * devuelve a minorista si ya no le corresponde el alto patrimonio.
     *
     * @return cuántos titulares han vuelto a minorista
     */
    int reviewSegments();
}
