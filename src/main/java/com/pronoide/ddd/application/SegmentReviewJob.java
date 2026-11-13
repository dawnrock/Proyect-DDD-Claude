package com.pronoide.ddd.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Revisa cada día el segmento de las cuentas a las que les toca: un titular de alto patrimonio que lleva un mes por
 * debajo del patrimonio mínimo vuelve a minorista, salvo que sus ingresos verificados sigan siendo altos (entonces se
 * revisa de nuevo a los seis meses). El servicio también lo revisa antes de cada cargo; esta tarea cubre las cuentas
 * sin movimientos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SegmentReviewJob {

    private final CheckingAccountService service;

    /** Todos los días a las 03:00 (hora del servidor). */
    @Scheduled(cron = "0 0 3 * * *")
    public void reviewSegments() {
        int demoted = service.reviewSegments();
        log.info("Revisión de segmentos: {} titulares vuelven a minorista", demoted);
    }
}
