package com.pronoide.ddd;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import org.springframework.stereotype.Controller;

import com.pronoide.ddd.domain.model.DomainException;

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
            .layer("Persistence").definedBy("..adapter.out.persistence..")
            .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation")
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Application")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Persistence", "Presentation");

    /** D1: el dominio solo conoce el JDK (ni Spring, ni {@code jakarta}, ni {@code org.immutables}). */
    @ArchTest
    static final ArchRule el_dominio_solo_depende_del_jdk = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.pronoide.ddd.domain..");

    /**
     * D4: la presentación trabaja con commands y vistas; del dominio solo conoce {@code DomainException} y sus
     * subclases, para traducirlas a HTTP o a mensajes. Tampoco conoce {@code domain.service}.
     */
    @ArchTest
    static final ArchRule la_presentacion_solo_conoce_las_excepciones_del_dominio = noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat(resideInAPackage("..domain..").and(not(assignableTo(DomainException.class))));

    /** D5: la web y la API REST son dos canales independientes; ninguno conoce al otro. */
    @ArchTest
    static final ArchRule la_web_y_rest_no_se_conocen = slices()
            .matching("..presentation.(*)..")
            .should().notDependOnEachOther();

    /** El dominio solo cambia de estado con métodos de negocio: no tiene {@code set*} públicos. */
    @ArchTest
    static final ArchRule el_dominio_no_tiene_setters_publicos = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .and().arePublic()
            .should().haveNameMatching("set[A-Z].*");

    /** Los controladores (web y REST) usan los casos de uso, nunca el repositorio. */
    @ArchTest
    static final ArchRule los_controladores_no_acceden_al_repositorio = noClasses()
            .that().areMetaAnnotatedWith(Controller.class)
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.persistence..");

    /**
     * ADR 0008: la aplicación decide a qué cuentas accede cada usuario con un {@code Caller} sencillo, sin Spring
     * Security, para que la regla se pruebe sin Spring y pase tal cual a la fase 2.
     */
    @ArchTest
    static final ArchRule la_aplicacion_no_depende_de_spring_security = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.security..");

    private static final String PRE_AUTHORIZE = "org.springframework.security.access.prepost.PreAuthorize";
    private static final String POST_AUTHORIZE = "org.springframework.security.access.prepost.PostAuthorize";
    private static final String SECURED = "org.springframework.security.access.annotation.Secured";

    /**
     * ADR 0008: nadie usa la seguridad por métodos, en ninguna capa. Llevaría la comprobación del propietario al
     * framework y se saltaría el {@code Caller}; Spring Security solo comprueba roles en las URL.
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
     * ADR 0004: en la fase 1 la aplicación usa clases concretas de persistencia, sin interfaces de puerto. Es lo que
     * diferencia esta fotografía de la hexagonal, donde el puerto se declara en {@code domain.model}.
     */
    @ArchTest
    static final ArchRule la_persistencia_no_tiene_interfaces = noClasses()
            .that().resideInAPackage("..adapter.out.persistence..")
            .should().beInterfaces();

    /**
     * Toda clase pertenece a una capa: la regla de capas solo mira dependencias entre capas, así que un paquete nuevo
     * fuera de ellas (por ejemplo {@code config}) quedaría sin vigilar. La única excepción es la clase de arranque.
     */
    @ArchTest
    static final ArchRule toda_clase_pertenece_a_una_capa = classes()
            .that().resideOutsideOfPackages("..presentation..", "..application..", "..domain..", "..adapter.out.persistence..")
            .should().haveFullyQualifiedName(DddLabApplication.class.getName());
}
