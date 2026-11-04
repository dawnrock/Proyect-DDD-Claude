package com.pronoide.ddd;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Controller;

import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.domain.model.DomainException;

/**
 * Arquitectura en capas de la fase 1: cada regla es una fila de la tabla de dependencias (D1-D5) de
 * {@code docs/arquitectura/fase-1-capas.md}, más las que pide la #12. Provisional en la fase 2: se ajusta a los puertos
 * a medida que llegan (#15, #16) hasta que la sustituya {@code HexagonalArchitectureTests} (#22).
 */
@AnalyzeClasses(packages = "com.pronoide.ddd",
                importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTests {

    /**
     * D2 y D3: nadie depende de {@code adapter.in}, ni de {@code application} desde el dominio. Desde la #15 nadie
     * depende de los adaptadores de salida (se usan por sus puertos), y la persistencia sí conoce {@code application}:
     * implementa su puerto de propiedad de las cuentas (ADR 0008). Desde la #17 la capa de adaptadores de salida
     * incluye {@code adapter.out.event}. Desde la #20 {@code config} construye el núcleo: puede usar el dominio y
     * {@code application}, y nadie la usa.
     */
    @ArchTest
    static final ArchRule las_capas_solo_dependen_hacia_abajo = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Presentation").definedBy("..adapter.in..")
            .layer("Application").definedBy("..application..")
            .layer("Domain").definedBy("..domain..")
            .layer("OutputAdapters").definedBy("..adapter.out..")
            .layer("Config").definedBy("..config..")
            .whereLayer("Config").mayNotBeAccessedByAnyLayer()
            .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation", "OutputAdapters", "Config")
            .whereLayer("OutputAdapters").mayNotBeAccessedByAnyLayer()
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "OutputAdapters", "Presentation", "Config");

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
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat(resideInAPackage("..domain..").and(not(assignableTo(DomainException.class))));

    /** D5: la web y la API REST son dos canales independientes; ninguno conoce al otro. */
    @ArchTest
    static final ArchRule la_web_y_rest_no_se_conocen = slices()
            .matching("..adapter.in.(*)..")
            .should().notDependOnEachOther();

    /**
     * #16 y #19: los adaptadores de entrada (REST, web y CLI) usan los puertos {@code CheckingAccountCommands} y
     * {@code CheckingAccountQueries}, nunca la clase que los implementa. Se nombra por clase para que un renombrado
     * rompa la compilación en lugar de dejar la regla vacía.
     */
    @ArchTest
    static final ArchRule los_adaptadores_de_entrada_solo_conocen_los_puertos_de_entrada = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().areAssignableTo(CheckingAccountService.class);

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
     * #15, #17 y #20: los adaptadores de salida solo los conoce su propio paquete. El resto, también {@code config}, usa
     * sus puertos ({@code CheckingAccountRepository}, {@code AccountOwnershipRepository}, {@code DomainEventPublisher},
     * {@code AccountNumberSequence}, {@code IncomeVerifier}); Spring los registra porque son {@code @Component}.
     */
    @ArchTest
    static final ArchRule solo_adapter_out_conoce_los_adaptadores_de_salida = noClasses()
            .that().resideOutsideOfPackage("..adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..");

    /** #17: los adaptadores de salida no se conocen entre sí; cada uno implementa su puerto. */
    @ArchTest
    static final ArchRule los_adaptadores_de_salida_no_se_conocen = slices()
            .matching("..adapter.out.(*)..")
            .should().notDependOnEachOther();

    /**
     * ADR 0004: la persistencia no declara interfaces. En la fase 1 no había puertos; en la fase 2 (#15) los puertos se
     * declaran en {@code domain.model} y {@code application}, y la persistencia solo los implementa.
     */
    @ArchTest
    static final ArchRule la_persistencia_no_tiene_interfaces = noClasses()
            .that().resideInAPackage("..adapter.out.persistence..")
            .should().beInterfaces();

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

    /**
     * Toda clase pertenece a una capa: la regla de capas solo mira dependencias entre capas, así que un paquete nuevo
     * fuera de ellas quedaría sin vigilar. Desde la #20, {@code config} es una capa más. La única excepción es la clase
     * de arranque.
     */
    @ArchTest
    static final ArchRule toda_clase_pertenece_a_una_capa = classes()
            .that().resideOutsideOfPackages("..adapter.in..", "..application..", "..domain..", "..adapter.out..", "..config..")
            .should().haveFullyQualifiedName(DddLabApplication.class.getName());
}
