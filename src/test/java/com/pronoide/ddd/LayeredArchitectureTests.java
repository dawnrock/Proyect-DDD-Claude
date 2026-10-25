package com.pronoide.ddd;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Arquitectura en capas de la fase 1: cada regla es una fila de la tabla de dependencias (D1-D5) de
 * {@code docs/arquitectura/fase-1-capas.md}, más las que pide la #12.
 */
@AnalyzeClasses(packages = "com.pronoide.ddd",
                importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTests {

    /** D2 y D3: nadie depende de {@code presentation}, ni de {@code application} desde abajo. */
    @ArchTest
    static final ArchRule las_capas_solo_dependen_hacia_abajo = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Presentation").definedBy("..presentation..")
            .layer("Application").definedBy("..application..")
            .layer("Domain").definedBy("..domain..")
            .layer("Persistence").definedBy("..persistence..")
            .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation")
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Application")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Persistence", "Presentation");
}
