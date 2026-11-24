package com.dawnrock.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato del puerto {@link CustomerRepository}: lo que cumple cualquier implementación. Cada adaptador lo hereda con
 * un test que crea un repositorio vacío.
 */
public abstract class CustomerRepositoryContract {

    protected CustomerRepository repository;

    /** Un repositorio vacío, nuevo en cada test. */
    protected abstract CustomerRepository createRepository();

    @BeforeEach
    void createEmptyRepository() {
        repository = createRepository();
    }

    private static Customer ada() {
        return Customer.register("Ada Lovelace", new TaxId("12345678Z"), new EmailAddress("ada@example.com"));
    }

    @Test
    @DisplayName("Un cliente guardado se encuentra por su documento, aunque se escriba con espacios o minúsculas")
    void aSavedCustomerIsFoundByItsDocument() {
        Customer ada = ada();
        repository.save(ada);

        assertThat(repository.findByTaxId(new TaxId(" 12345678 z "))).contains(ada);
    }

    @Test
    @DisplayName("Un documento desconocido no devuelve nada")
    void anUnknownDocumentReturnsNothing() {
        repository.save(ada());

        assertThat(repository.findByTaxId(new TaxId("87654321X"))).isEmpty();
    }

    @Test
    @DisplayName("Guardar otro cliente con el mismo documento se rechaza y no pisa al primero (R30)")
    void savingAnotherCustomerWithTheSameDocumentIsRejected() {
        Customer ada = ada();
        repository.save(ada);
        Customer other = Customer.register("Otra Ada", new TaxId("12345678Z"), new EmailAddress("otra@example.com"));

        assertThatExceptionOfType(DuplicateCustomerException.class).isThrownBy(() -> repository.save(other));
        assertThat(repository.findByTaxId(new TaxId("12345678Z"))).contains(ada);
    }

    @Test
    @DisplayName("Guardar de nuevo al mismo cliente no es un duplicado")
    void savingTheSameCustomerAgainIsNotADuplicate() {
        Customer ada = ada();
        repository.save(ada);

        assertThat(repository.save(ada)).isSameAs(ada);
        assertThat(repository.findByTaxId(new TaxId("12345678Z"))).contains(ada);
    }

    @Test
    @DisplayName("El cliente a guardar y el documento a buscar son obligatorios")
    void theCustomerAndTheDocumentAreRequired() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> repository.save(null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> repository.findByTaxId(null));
    }
}
