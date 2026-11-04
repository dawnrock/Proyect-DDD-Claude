package com.pronoide.ddd;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.context.event.EventListener;

import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.domain.model.DomainException;

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
            .ignoreDependency(resideInAnyPackage(ADAPTER_IN_SUPPORT, "com.pronoide.ddd.config.."), alwaysTrue());

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

    /**
     * Los puertos de entrada ({@code *Commands}, {@code *Queries}) son interfaces de {@code application}. El nombre va en
     * un solo predicado: ArchUnit encadena {@code .and()} y {@code .or()} de izquierda a derecha, sin precedencia.
     */
    @ArchTest
    static final ArchRule los_puertos_de_entrada_son_interfaces = classes()
            .that(simpleNameEndingWith("Commands").or(simpleNameEndingWith("Queries")))
            .and().resideInAPackage("..application..")
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

    /**
     * Solo los adaptadores y {@code config} dependen de Spring web, HTTP, MVC o datos: el hexágono no sabe por qué canal
     * le llegan las peticiones ni dónde se guardan las cuentas.
     */
    @ArchTest
    static final ArchRule solo_los_adaptadores_y_config_dependen_de_spring_web = noClasses()
            .that().resideOutsideOfPackages("..adapter..", "com.pronoide.ddd.config..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.springframework.http..",
                    "org.springframework.ui..", "org.springframework.data..");

    /**
     * #20: el núcleo no depende de Spring; lo construye {@code config.DomainConfiguration}. La única excepción
     * justificada es la anotación {@code @EventListener} en {@code application.listener}: los eventos de dominio llegan a
     * los consumidores por el mecanismo de eventos de Spring. Nada más de Spring, tampoco del paquete de eventos.
     */
    @ArchTest
    static final ArchRule el_nucleo_no_depende_de_spring = CompositeArchRule
            .of(noClasses()
                    .that().resideInAnyPackage("..domain..", "..application..")
                    .and().resideOutsideOfPackage("..application.listener..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework.."))
            .and(noClasses()
                    .that().resideInAPackage("..application.listener..")
                    .should().dependOnClassesThat(resideInAPackage("org.springframework..")
                            .and(not(equivalentTo(EventListener.class)))));

    /** D1: el dominio solo conoce el JDK (ni Spring, ni {@code jakarta}, ni {@code org.immutables}). */
    @ArchTest
    static final ArchRule el_dominio_solo_depende_del_jdk = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.pronoide.ddd.domain..");

    /** El dominio solo cambia de estado con métodos de negocio: no tiene {@code set*} públicos. */
    @ArchTest
    static final ArchRule el_dominio_no_tiene_setters_publicos = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .and().arePublic()
            .should().haveNameMatching("set[A-Z].*");

    /**
     * ADR 0008: la aplicación decide a qué cuentas accede cada usuario con un {@code Caller} sencillo, sin Spring
     * Security, para que la regla se pruebe sin Spring.
     */
    @ArchTest
    static final ArchRule la_aplicacion_no_depende_de_spring_security = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.security..");

    private static final String PRE_AUTHORIZE = "org.springframework.security.access.prepost.PreAuthorize";
    private static final String POST_AUTHORIZE = "org.springframework.security.access.prepost.PostAuthorize";
    private static final String SECURED = "org.springframework.security.access.annotation.Secured";

    /**
     * ADR 0008: nadie usa la seguridad por métodos. Llevaría la comprobación del propietario al framework y se saltaría
     * el {@code Caller}; Spring Security solo comprueba roles en las URL.
     */
    @ArchTest
    static final ArchRule nadie_usa_la_seguridad_por_metodos = CompositeArchRule
            .of(noMethods().should().beAnnotatedWith(PRE_AUTHORIZE)
                    .orShould().beAnnotatedWith(POST_AUTHORIZE)
                    .orShould().beAnnotatedWith(SECURED))
            .and(noClasses().should().beAnnotatedWith(PRE_AUTHORIZE)
                    .orShould().beAnnotatedWith(POST_AUTHORIZE)
                    .orShould().beAnnotatedWith(SECURED));

    /**
     * D4: los adaptadores de entrada trabajan con commands y vistas; del dominio solo conocen {@code DomainException} y
     * sus subclases, para traducirlas a HTTP o a mensajes. Incluye el soporte de seguridad de la raíz de
     * {@code adapter.in}.
     */
    @ArchTest
    static final ArchRule los_adaptadores_de_entrada_solo_conocen_las_excepciones_del_dominio = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat(resideInAPackage("..domain..").and(not(assignableTo(DomainException.class))));

    /**
     * #15, #17 y #20: los adaptadores de salida solo los conoce su propio paquete. Cubre lo que la cebolla no ve: el
     * soporte de seguridad, {@code config} (que los recibe por sus puertos) y la clase de arranque.
     */
    @ArchTest
    static final ArchRule solo_adapter_out_conoce_los_adaptadores_de_salida = noClasses()
            .that().resideOutsideOfPackage("..adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..");

    /**
     * Nadie fuera de {@code adapter.in} depende de los adaptadores de entrada ni de su soporte de seguridad: ni el
     * hexágono, ni los adaptadores de salida, ni {@code config}. Cubre lo que la cebolla no ve en su raíz.
     */
    @ArchTest
    static final ArchRule nadie_fuera_de_adapter_in_depende_de_adapter_in = noClasses()
            .that().resideOutsideOfPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.in..");

    /** El soporte de seguridad de la raíz de {@code adapter.in} es común: no conoce a ningún adaptador de entrada. */
    @ArchTest
    static final ArchRule el_soporte_de_adapter_in_no_conoce_a_los_adaptadores = noClasses()
            .that().resideInAPackage(ADAPTER_IN_SUPPORT)
            .should().dependOnClassesThat()
            .resideInAnyPackage("..adapter.in.cli..", "..adapter.in.rest..", "..adapter.in.web..");

    /** #20: {@code config} construye el núcleo y nadie depende de ella. */
    @ArchTest
    static final ArchRule nadie_depende_de_config = noClasses()
            .that().resideOutsideOfPackage("com.pronoide.ddd.config..")
            .should().dependOnClassesThat().resideInAPackage("com.pronoide.ddd.config..");

    /**
     * Toda clase pertenece al hexágono, a un adaptador o a {@code config}: un paquete nuevo fuera de ellos quedaría sin
     * vigilar. La única excepción es la clase de arranque.
     */
    @ArchTest
    static final ArchRule toda_clase_pertenece_al_hexagono_o_a_un_adaptador = classes()
            .that().resideOutsideOfPackages("..domain..", "..application..", "..adapter..", "com.pronoide.ddd.config..")
            .should().haveFullyQualifiedName(DddLabApplication.class.getName());

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
