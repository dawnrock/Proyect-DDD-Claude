package com.pronoide.ddd;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.pronoide.ddd", 
                importOptions = ImportOption.DoNotIncludeTests.class)
class DomainIsolationTests {

    @ArchTest
    static final ArchRule el_dominio_no_depende_del_framework = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..",
                                "jakarta..");
    
    @ArchTest
    static final ArchRule el_estado_del_dominio_esta_encapsulado = fields()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .and().areNotStatic()
            .should().bePrivate();
}
