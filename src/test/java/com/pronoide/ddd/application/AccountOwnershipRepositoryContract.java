package com.pronoide.ddd.application;

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
    void el_propietario_asignado_a_una_cuenta_se_recupera_por_el_id_de_la_cuenta() {
        UUID accountId = UUID.randomUUID();

        repository.assign(accountId, "ada");

        assertThat(repository.ownerOf(accountId)).contains("ada");
    }

    @Test
    @DisplayName("Una cuenta sin propietario asignado no tiene propietario")
    void una_cuenta_sin_propietario_asignado_no_tiene_propietario() {
        assertThat(repository.ownerOf(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Asignar otro propietario a una cuenta sustituye al anterior")
    void asignar_otro_propietario_a_una_cuenta_sustituye_al_anterior() {
        UUID accountId = UUID.randomUUID();
        repository.assign(accountId, "ada");

        repository.assign(accountId, "grace");

        assertThat(repository.ownerOf(accountId)).contains("grace");
        assertThat(repository.accountsOf("ada")).isEmpty();
    }

    @Test
    @DisplayName("Las cuentas de un usuario son solo las que tiene asignadas")
    void las_cuentas_de_un_usuario_son_solo_las_que_tiene_asignadas() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.assign(first, "ada");
        repository.assign(second, "ada");
        repository.assign(UUID.randomUUID(), "grace");

        assertThat(repository.accountsOf("ada")).containsExactlyInAnyOrder(first, second);
        assertThat(repository.accountsOf("nadie")).isEmpty();
    }
}
