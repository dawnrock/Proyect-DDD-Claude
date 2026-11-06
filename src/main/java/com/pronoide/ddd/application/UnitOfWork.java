package com.pronoide.ddd.application;

import java.util.function.Supplier;

/**
 * Puerto de salida: ejecuta un caso de uso (cargar, operar, guardar) como una unidad, para que ningún otro caso de uso
 * vea sus cambios a medias ni los pise (#78). Lo implementa un adaptador de {@code adapter.out}: en memoria, con un
 * cerrojo; con una base de datos real sería una transacción. Vive en {@code application} porque el límite de un caso
 * de uso es cosa de la aplicación, no del dominio.
 *
 * <p>Lo que garantiza cualquier implementación (contrato {@code UnitOfWorkContract}, tests): el trabajo se ejecuta una
 * vez, se devuelve su resultado, su excepción se propaga sin perder la unidad para los siguientes, y dos unidades no se
 * solapan sobre los mismos datos.
 */
public interface UnitOfWork {

    <T> T execute(Supplier<T> work);
}
