package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.domain.model.TaxId;

/**
 * Contrato del puerto {@link CustomerOwnershipRepository}: lo que cumple cualquier implementación. Cada adaptador lo
 * hereda con un test que crea un repositorio vacío.
 */
public abstract class CustomerOwnershipRepositoryContract {

    private static final TaxId ADA = new TaxId("12345678Z");
    private static final TaxId ALAN = new TaxId("11111111H");

    protected CustomerOwnershipRepository repository;

    /** Un repositorio vacío, nuevo en cada test. */
    protected abstract CustomerOwnershipRepository createRepository();

    @BeforeEach
    void createEmptyRepository() {
        repository = createRepository();
    }

    @Test
    @DisplayName("El usuario asignado a un cliente se recupera por su documento y el cliente por su usuario")
    void el_usuario_asignado_se_recupera_en_los_dos_sentidos() {
        repository.assign(ADA, "ada");

        assertThat(repository.usernameOf(ADA)).contains("ada");
        assertThat(repository.customerOf("ada")).contains(ADA);
    }

    @Test
    @DisplayName("Un cliente o un usuario sin asignar no devuelven nada")
    void un_cliente_o_un_usuario_sin_asignar_no_devuelven_nada() {
        repository.assign(ADA, "ada");

        assertThat(repository.usernameOf(ALAN)).isEmpty();
        assertThat(repository.customerOf("alan")).isEmpty();
    }

    @Test
    @DisplayName("Un usuario que es de otro cliente se rechaza y no pisa la asignación (R30)")
    void un_usuario_de_otro_cliente_se_rechaza() {
        repository.assign(ADA, "ada");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> repository.assign(ALAN, "ada"))
                .withMessage("El usuario ada ya es de otro cliente");
        assertThat(repository.customerOf("ada")).contains(ADA);
        assertThat(repository.usernameOf(ALAN)).isEmpty();
    }

    @Test
    @DisplayName("Asignar de nuevo el mismo par no cambia nada")
    void asignar_de_nuevo_el_mismo_par_no_cambia_nada() {
        repository.assign(ADA, "ada");

        repository.assign(ADA, "ada");

        assertThat(repository.usernameOf(ADA)).contains("ada");
        assertThat(repository.customerOf("ada")).contains(ADA);
    }

    @Test
    @DisplayName("Asignar otro usuario a un cliente sustituye al anterior y libera el antiguo")
    void asignar_otro_usuario_a_un_cliente_sustituye_al_anterior() {
        repository.assign(ADA, "ada");

        repository.assign(ADA, "lovelace");

        assertThat(repository.usernameOf(ADA)).contains("lovelace");
        assertThat(repository.customerOf("ada")).isEmpty();
        assertThat(repository.customerOf("lovelace")).contains(ADA);
    }

    @Test
    @DisplayName("El documento y el usuario son obligatorios")
    void el_documento_y_el_usuario_son_obligatorios() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> repository.assign(null, "ada"));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> repository.assign(ADA, null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> repository.usernameOf(null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> repository.customerOf(null));
    }
}
