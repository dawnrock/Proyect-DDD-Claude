package com.pronoide.ddd.application;

import java.util.Optional;

import com.pronoide.ddd.domain.model.TaxId;

/**
 * Puerto de salida de la relación entre clientes y usuarios: con qué usuario entra cada cliente. Un cliente tiene un
 * único usuario y un usuario es de un único cliente (R30). Vive en {@code application} y no en {@code domain.model},
 * como {@link AccountOwnershipRepository}, porque la identidad del usuario no llega al dominio (ADR 0008). Lo
 * implementa un adaptador de {@code adapter.out.persistence}. Contrato: {@code docs/contracts/customer-sign-up.md}.
 */
public interface CustomerOwnershipRepository {

    /**
     * Asigna el usuario al cliente. Asignar de nuevo el mismo par no cambia nada; asignar otro usuario al mismo
     * cliente sustituye al anterior (los casos de uso solo asignan un usuario a un cliente nuevo).
     *
     * @throws IllegalArgumentException si el usuario ya es de otro cliente (R30)
     * @throws NullPointerException si el documento o el usuario son nulos
     */
    void assign(TaxId customer, String username);

    /** @throws NullPointerException si el documento es nulo */
    Optional<String> usernameOf(TaxId customer);

    /** @throws NullPointerException si el usuario es nulo */
    Optional<TaxId> customerOf(String username);
}
