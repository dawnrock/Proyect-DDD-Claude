package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato de {@link ExternalIdentities}: lo que cumple cualquier implementación (la de JDBC y el doble en memoria de
 * los tests). Cada una lo hereda con un test que crea un almacén vacío.
 */
abstract class ExternalIdentitiesContract {

    private static final String AUTH0 = ExternalIdentities.AUTH0;

    protected ExternalIdentities identities;

    /** Un almacén vacío, nuevo en cada test, sin ningún usuario local. */
    protected abstract ExternalIdentities createIdentities();

    /** Crea el usuario local (en la implementación de JDBC, la fila de {@code users} a la que apunta la clave foránea). */
    protected abstract void createUser(String username);

    @BeforeEach
    void createEmptyStoreWithTwoUsers() {
        identities = createIdentities();
        createUser("ada");
        createUser("grace");
    }

    @Test
    @DisplayName("Una identidad desconocida no devuelve ningún usuario")
    void anUnknownIdentityHasNoUser() {
        assertThat(identities.usernameOf(AUTH0, "auth0|1")).isEmpty();
    }

    @Test
    @DisplayName("Una identidad vinculada devuelve el usuario local")
    void aLinkedIdentityGivesTheLocalUser() {
        identities.link(AUTH0, "auth0|1", "ada");

        assertThat(identities.usernameOf(AUTH0, "auth0|1")).contains("ada");
        assertThat(identities.usernameOf(AUTH0, "auth0|2")).isEmpty();
    }

    @Test
    @DisplayName("Vincular otra vez la misma pareja no hace nada")
    void linkingTheSamePairTwiceDoesNothing() {
        identities.link(AUTH0, "auth0|1", "ada");

        identities.link(AUTH0, "auth0|1", "ada");

        assertThat(identities.usernameOf(AUTH0, "auth0|1")).contains("ada");
    }

    @Test
    @DisplayName("Un usuario no puede vincular una segunda identidad del mismo proveedor")
    void aUserCannotLinkASecondIdentityOfTheSameProvider() {
        identities.link(AUTH0, "auth0|1", "ada");

        assertThatExceptionOfType(IdentityAlreadyLinkedException.class)
                .isThrownBy(() -> identities.link(AUTH0, "auth0|2", "ada"));
        assertThat(identities.usernameOf(AUTH0, "auth0|2")).isEmpty();
    }

    @Test
    @DisplayName("Una identidad no puede apuntar a dos usuarios")
    void anIdentityCannotPointToTwoUsers() {
        identities.link(AUTH0, "auth0|1", "ada");

        assertThatExceptionOfType(IdentityAlreadyLinkedException.class)
                .isThrownBy(() -> identities.link(AUTH0, "auth0|1", "grace"));
        assertThat(identities.usernameOf(AUTH0, "auth0|1")).contains("ada");
    }

    @Test
    @DisplayName("Un usuario puede tener una identidad de cada proveedor")
    void aUserCanHaveOneIdentityPerProvider() {
        identities.link(AUTH0, "auth0|1", "ada");
        identities.link("otro", "otro|1", "ada");

        assertThat(identities.usernameOf("otro", "otro|1")).contains("ada");
        assertThat(identities.usernameOf(AUTH0, "otro|1")).isEmpty();
    }

    @Test
    @DisplayName("No se vincula una identidad a un usuario local que no existe")
    void anIdentityCannotBeLinkedToAMissingUser() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> identities.link(AUTH0, "auth0|1", "nadie"));
        assertThat(identities.usernameOf(AUTH0, "auth0|1")).isEmpty();
    }
}
