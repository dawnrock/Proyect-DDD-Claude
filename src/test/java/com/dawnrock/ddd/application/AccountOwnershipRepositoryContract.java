package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato del puerto {@link AccountOwnershipRepository}: lo que cumple cualquier implementación. Cada adaptador lo
 * hereda con un test que crea un repositorio vacío.
 */
public abstract class AccountOwnershipRepositoryContract {

    protected AccountOwnershipRepository repository;

    /** Un repositorio vacío, nuevo en cada test. */
    protected abstract AccountOwnershipRepository createRepository();

    @BeforeEach
    void createEmptyRepository() {
        repository = createRepository();
    }

    @Test
    @DisplayName("El propietario asignado a una cuenta se recupera por el id de la cuenta")
    void theOwnerAssignedToAnAccountIsFoundByTheAccountId() {
        UUID accountId = UUID.randomUUID();

        repository.assign(accountId, "ada");

        assertThat(repository.ownerOf(accountId)).contains("ada");
    }

    @Test
    @DisplayName("Una cuenta sin propietario asignado no tiene propietario")
    void anAccountWithoutAnAssignedOwnerHasNoOwner() {
        assertThat(repository.ownerOf(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Asignar otro propietario a una cuenta sustituye al anterior")
    void assigningAnotherOwnerToAnAccountReplacesThePreviousOne() {
        UUID accountId = UUID.randomUUID();
        repository.assign(accountId, "ada");

        repository.assign(accountId, "grace");

        assertThat(repository.ownerOf(accountId)).contains("grace");
        assertThat(repository.accountsOf("ada")).isEmpty();
    }

    @Test
    @DisplayName("Las cuentas de un usuario son solo las que tiene asignadas")
    void aUsersAccountsAreOnlyTheOnesAssignedToThem() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.assign(first, "ada");
        repository.assign(second, "ada");
        repository.assign(UUID.randomUUID(), "grace");

        assertThat(repository.accountsOf("ada")).containsExactlyInAnyOrder(first, second);
        assertThat(repository.accountsOf("nadie")).isEmpty();
    }
}
