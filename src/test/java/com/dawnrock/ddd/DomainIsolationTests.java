package com.dawnrock.ddd;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

@AnalyzeClasses(packages = "com.dawnrock.ddd", 
                importOptions = ImportOption.DoNotIncludeTests.class)
class DomainIsolationTests {

    @ArchTest
    static void theDomainDoesNotDependOnTheFramework(JavaClasses classes) {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..",
                                    "jakarta..")
                .check(classes);
    }
    
    @ArchTest
    static void theDomainStateIsEncapsulated(JavaClasses classes) {
        fields()
                .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
                .and().areNotStatic()
                .should().bePrivate()
                .check(classes);
    }
}
