package com.pronoide.ddd.application;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de salida de la propiedad de las cuentas: qué usuario es dueño de cada cuenta. Una cuenta tiene un único
 * propietario. Vive en {@code application} y no en {@code domain.model} porque la identidad del usuario no llega al
 * dominio: quién accede a qué cuenta lo decide la aplicación con {@link Caller} (ADR 0008). Lo implementa un adaptador
 * de {@code adapter.out.persistence}.
 *
 * <p>Contrato común a cualquier implementación: {@code AccountOwnershipRepositoryContract} (tests).
 */
public interface AccountOwnershipRepository {

    /**
     * Asigna la cuenta a un usuario, sustituyendo al propietario anterior si lo hubiera.
     *
     * @throws NullPointerException si el id de la cuenta o el usuario son nulos
     */
    void assign(UUID accountId, String username);

    /** @throws NullPointerException si el id de la cuenta es nulo */
    Optional<String> ownerOf(UUID accountId);

    /**
     * Ids de las cuentas de un usuario.
     *
     * @throws NullPointerException si el usuario es nulo
     */
    Set<UUID> accountsOf(String username);
}
