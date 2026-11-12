package com.pronoide.ddd.application;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuración de la capa de aplicación: el reloj con el que los casos de uso preguntan la hora. */
@Configuration
public class ApplicationConfig {

    /** Reloj del sistema en UTC; los tests lo sustituyen por uno que se puede adelantar. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
