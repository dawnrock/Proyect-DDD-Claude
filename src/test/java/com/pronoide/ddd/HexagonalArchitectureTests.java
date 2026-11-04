package com.pronoide.ddd;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import com.pronoide.ddd.application.CheckingAccountService;

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

    /** Donde viven los puertos: los de salida en {@code domain.model}; el de propiedad, en {@code application}. */
    private static final List<String> PORT_PACKAGES =
            List.of("com.pronoide.ddd.domain.model", "com.pronoide.ddd.application");

    /**
     * Los adaptadores de salida solo implementan puertos: interfaces de {@code domain.model} o de {@code application}
     * (ADR 0008). Un adaptador que implementara una interfaz propia o de otro sitio no sería intercambiable.
     */
    @ArchTest
    static final ArchRule los_adaptadores_de_salida_solo_implementan_puertos_del_hexagono = classes()
            .that().resideInAPackage("..adapter.out..")
            .and().areNotInterfaces()
            .should(implementOnlyPortsOfTheHexagon());

    /**
     * #15: los adaptadores de salida conocen {@code application} solo por sus puertos (interfaces), nunca por el servicio
     * ni por sus vistas o commands.
     */
    @ArchTest
    static final ArchRule los_adaptadores_de_salida_solo_conocen_application_por_sus_puertos = noClasses()
            .that().resideInAPackage("..adapter.out..")
            .should().dependOnClassesThat(resideInAPackage("..application..").and(not(INTERFACES)));

    /** Los puertos de entrada ({@code *Commands}, {@code *Queries}) son interfaces de {@code application}. */
    @ArchTest
    static final ArchRule los_puertos_de_entrada_son_interfaces = classes()
            .that().resideInAPackage("..application..")
            .and().haveSimpleNameEndingWith("Commands")
            .or().resideInAPackage("..application..")
            .and().haveSimpleNameEndingWith("Queries")
            .should().beInterfaces();

    /**
     * #16 y #19: los adaptadores de entrada (REST, web y CLI) usan los puertos {@code CheckingAccountCommands} y
     * {@code CheckingAccountQueries}, nunca la clase que los implementa. Se nombra por clase para que un renombrado
     * rompa la compilación en lugar de dejar la regla vacía.
     */
    @ArchTest
    static final ArchRule los_adaptadores_de_entrada_solo_conocen_los_puertos_de_entrada = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().areAssignableTo(CheckingAccountService.class);

    private static ArchCondition<JavaClass> implementOnlyPortsOfTheHexagon() {
        return new ArchCondition<>("implement only ports of the hexagon") {
            @Override
            public void check(JavaClass adapter, ConditionEvents events) {
                adapter.getRawInterfaces().stream()
                        .filter(port -> !port.isInterface() || !PORT_PACKAGES.contains(port.getPackageName()))
                        .forEach(other -> events.add(SimpleConditionEvent.violated(adapter,
                                "%s implementa %s, que no es un puerto del hexágono".formatted(adapter.getName(), other.getName()))));
            }
        };
    }
}
