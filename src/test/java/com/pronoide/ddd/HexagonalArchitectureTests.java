package com.pronoide.ddd;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Arquitectura hexagonal de la fase 2 (#22, Lab 27.10): todas las dependencias apuntan hacia el hexágono. Sustituye a
 * {@code LayeredArchitectureTests}; las reglas de la fase 1 siguen en la rama {@code Monolithic}. Diseño:
 * {@code docs/arquitectura/fase-2-hexagonal.md}.
 */
@AnalyzeClasses(packages = "com.pronoide.ddd",
                importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTests {

    /** Raíz de {@code adapter.in}: soporte de seguridad compartido por los adaptadores de entrada (ADR 0008). */
    private static final String ADAPTER_IN_SUPPORT = "com.pronoide.ddd.adapter.in";

    /**
     * Los anillos del hexágono: el modelo no depende de nadie, los servicios de dominio solo del modelo, la aplicación
     * del dominio, y cada adaptador del hexágono, nunca de otro adaptador. El soporte de seguridad de la raíz de
     * {@code adapter.in} (que comparten REST y web), {@code config} y la clase de arranque quedan fuera de los anillos: se
     * ignoran las dependencias que salen de ellos y los vigilan otras reglas de esta clase.
     */
    @ArchTest
    static final ArchRule las_dependencias_apuntan_hacia_el_hexagono = onionArchitecture()
            .domainModels("..domain.model..")
            .domainServices("..domain.service..")
            .applicationServices("..application..")
            .adapter("cli", "..adapter.in.cli..")
            .adapter("rest", "..adapter.in.rest..")
            .adapter("web", "..adapter.in.web..")
            .adapter("persistence", "..adapter.out.persistence..")
            .adapter("event", "..adapter.out.event..")
            .adapter("taxagency", "..adapter.out.taxagency..")
            .ignoreDependency(resideInAnyPackage(ADAPTER_IN_SUPPORT, "..config.."), alwaysTrue());
}
