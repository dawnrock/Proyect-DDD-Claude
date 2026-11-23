package com.dawnrock.ddd.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las tareas programadas de los adaptadores de entrada ({@code adapter.in.scheduler}). */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
