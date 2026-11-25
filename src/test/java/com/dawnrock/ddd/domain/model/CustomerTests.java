package com.dawnrock.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerTests {

    private static final TaxId TAX_ID = new TaxId("12345678Z");
    private static final EmailAddress EMAIL = new EmailAddress("ada@example.com");

    @Test
    @DisplayName("Un cliente se registra con nombre, documento y correo, y recibe un id")
    void aCustomerRegistersWithNameDocumentAndEmail() {
        Customer customer = Customer.register("Ada Lovelace", TAX_ID, EMAIL);

        assertThat(customer.id()).isNotNull();
        assertThat(customer.fullName()).isEqualTo("Ada Lovelace");
        assertThat(customer.taxId()).isEqualTo(TAX_ID);
        assertThat(customer.email()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("El nombre del cliente se guarda sin espacios alrededor")
    void theCustomerNameIsStoredTrimmed() {
        assertThat(Customer.register("  Ada Lovelace ", TAX_ID, EMAIL).fullName()).isEqualTo("Ada Lovelace");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("El nombre de un cliente no puede estar en blanco")
    void aCustomerNameCannotBeBlank(String fullName) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Customer.register(fullName, TAX_ID, EMAIL))
                .withMessage("El nombre del cliente no puede estar vacío");
    }

    @Test
    @DisplayName("El nombre, el documento y el correo de un cliente son obligatorios")
    void theCustomerDataIsRequired() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Customer.register(null, TAX_ID, EMAIL))
                .withMessage("El nombre del cliente es obligatorio");
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Customer.register("Ada Lovelace", null, EMAIL))
                .withMessage("El documento del cliente es obligatorio");
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Customer.register("Ada Lovelace", TAX_ID, null))
                .withMessage("El correo del cliente es obligatorio");
    }

    @Test
    @DisplayName("Dos clientes son iguales solo si tienen el mismo id")
    void twoCustomersAreEqualOnlyWithTheSameId() {
        Customer ada = Customer.register("Ada Lovelace", TAX_ID, EMAIL);
        Customer sameData = Customer.register("Ada Lovelace", TAX_ID, EMAIL);

        assertThat(ada).isEqualTo(ada).hasSameHashCodeAs(ada).isNotEqualTo(sameData);
    }

    @Test
    @DisplayName("Un documento duplicado es una regla de negocio incumplida con el documento en el mensaje (R30)")
    void aDuplicateDocumentIsABrokenBusinessRule() {
        assertThat(new DuplicateCustomerException(TAX_ID))
                .isInstanceOf(DomainException.class)
                .hasMessageStartingWith("Ya eres cliente de monA Bank")
                .hasMessageContaining("12345678Z");
    }

    @Test
    @DisplayName("Un cliente reconstituido conserva su identidad, su nombre, su documento y su correo")
    void aReconstitutedCustomerKeepsAllItsData() {
        UUID id = UUID.randomUUID();

        Customer cliente = Customer.reconstitute(id, "Ada Lovelace", new TaxId("12345678Z"),
                new EmailAddress("ada@example.com"));

        assertThat(cliente.id()).isEqualTo(id);
        assertThat(cliente.fullName()).isEqualTo("Ada Lovelace");
        assertThat(cliente.taxId()).isEqualTo(new TaxId("12345678Z"));
        assertThat(cliente.email()).isEqualTo(new EmailAddress("ada@example.com"));
    }

    @Test
    @DisplayName("Un cliente no se reconstituye sin identidad ni con los datos que rechaza el alta")
    void aCustomerIsNotReconstitutedWithoutItsRequiredData() {
        TaxId taxId = new TaxId("12345678Z");
        EmailAddress email = new EmailAddress("ada@example.com");
        assertThatThrownBy(
                () -> Customer.reconstitute(null, "Ada Lovelace", taxId, email))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                () -> Customer.reconstitute(UUID.randomUUID(), " ", taxId, email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre del cliente no puede estar vacío");
        assertThatThrownBy(
                () -> Customer.reconstitute(UUID.randomUUID(), "Ada Lovelace", null, email))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                () -> Customer.reconstitute(UUID.randomUUID(), "Ada Lovelace", taxId, null))
                .isInstanceOf(NullPointerException.class);
    }
}
