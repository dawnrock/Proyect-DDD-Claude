package com.dawnrock.ddd.adapter.in;

import java.util.Optional;

/**
 * Identidades de un proveedor externo (Auth0, ADR 0024) vinculadas a un usuario local. Como los usuarios de Spring
 * Security, viven en el soporte de seguridad de {@code adapter.in}: la identidad nunca entra en {@code application}
 * (ADR 0008). Tabla {@code external_identity} (Flyway {@code V3}). Contrato: {@code docs/contracts/auth0-web-login.md}.
 */
public interface ExternalIdentities {

    /** Identificador del proveedor Auth0: el {@code registrationId} del cliente OAuth2 y el valor de {@code provider}. */
    String AUTH0 = "auth0";

    /** El usuario local vinculado a la identidad {@code subject} ({@code sub}) del proveedor, si lo hay. */
    Optional<String> usernameOf(String provider, String subject);

    /**
     * Vincula la identidad al usuario local. Vincular otra vez la misma pareja no hace nada.
     *
     * @throws IdentityAlreadyLinkedException si el usuario ya tiene otra identidad de ese proveedor, o la identidad ya
     *                                        está vinculada a otro usuario
     */
    void link(String provider, String subject, String username);
}
