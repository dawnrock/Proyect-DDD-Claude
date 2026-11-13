package com.pronoide.ddd.adapter.in.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pronoide.ddd.application.CheckingAccountCommands;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de entrada programado (#98): cada día pide la revisión de segmentos. Como la API REST, la web y la línea
 * de comandos, solo usa el puerto de entrada {@link CheckingAccountCommands}; aquí quien llama al hexágono es el
 * reloj. La aplicación también revisa una cuenta antes de cada cargo: esta tarea cubre las cuentas sin movimientos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SegmentReviewScheduler {

    private final CheckingAccountCommands commands;

    /** Todos los días a las 03:00 (hora del servidor). */
    @Scheduled(cron = "0 0 3 * * *")
    public void reviewSegments() {
        int demoted = commands.reviewSegments();
        log.info("Revisión de segmentos: {} titulares vuelven a minorista", demoted);
    }
}
