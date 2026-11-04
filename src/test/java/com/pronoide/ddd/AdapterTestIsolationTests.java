package com.pronoide.ddd;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Reglas sobre el código de test (#21): cada adaptador se prueba sin el hexágono. A diferencia del resto de reglas de
 * ArchUnit, esta clase importa solo los tests.
 */
@AnalyzeClasses(packages = "com.pronoide.ddd",
                importOptions = ImportOption.OnlyIncludeTests.class)
class AdapterTestIsolationTests {

    /**
     * Ningún test de un adaptador de entrada levanta el contexto completo: REST y web usan {@code @WebMvcTest} con los
     * puertos simulados, y la CLI, el servicio con adaptadores en memoria. Los tests de contexto completo viven en la
     * raíz ({@code DddLabApplicationTests}, {@code DemoProfileTests}).
     */
    @ArchTest
    static final ArchRule los_tests_de_adaptadores_de_entrada_no_levantan_el_contexto_completo = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().beMetaAnnotatedWith(SpringBootTest.class);
}
