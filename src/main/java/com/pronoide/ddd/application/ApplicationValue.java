package com.pronoide.ddd.application;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.immutables.value.Value;

/**
 * Estilo común de Immutables para los commands, queries y vistas de la capa de aplicación.
 *
 * <p>El tipo abstracto lleva el sufijo {@code Def} y el generado toma el nombre sin él:
 * {@code OpenAccountCommandDef} genera {@code OpenAccountCommand}, con su builder.
 *
 * <p>No se usa en {@code domain..}: el código generado deja dependencias de {@code org.immutables}
 * en el bytecode (ver {@code docs/adr/0003-lombok-immutables.md}).
 */
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
@Value.Style(
        typeAbstract = "*Def",
        typeImmutable = "*",
        visibility = Value.Style.ImplementationVisibility.PUBLIC,
        defaults = @Value.Immutable(copy = false))
public @interface ApplicationValue {
}
