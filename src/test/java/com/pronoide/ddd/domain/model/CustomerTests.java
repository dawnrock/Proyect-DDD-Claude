package com.pronoide.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerTests {

    private static final TaxId TAX_ID = new TaxId("12345678Z");
    private static final EmailAddress EMAIL = new EmailAddress("ada@example.com");

    @Test
    @DisplayName("Un cliente se registra con nombre, documento y correo, y recibe un id")
    void un_cliente_se_registra_con_nombre_documento_y_correo() {
        Customer customer = Customer.register("Ada Lovelace", TAX_ID, EMAIL);

        assertThat(customer.id()).isNotNull();
        assertThat(customer.fullName()).isEqualTo("Ada Lovelace");
        assertThat(customer.taxId()).isEqualTo(TAX_ID);
        assertThat(customer.email()).isEqualTo(EMAIL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("El nombre de un cliente no puede estar en blanco")
    void el_nombre_de_un_cliente_no_puede_estar_en_blanco(String fullName) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Customer.register(fullName, TAX_ID, EMAIL))
                .withMessage("El nombre del cliente no puede estar vacío");
    }

    @Test
    @DisplayName("El nombre, el documento y el correo de un cliente son obligatorios")
    void los_datos_de_un_cliente_son_obligatorios() {
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
    void dos_clientes_son_iguales_solo_si_tienen_el_mismo_id() {
        Customer ada = Customer.register("Ada Lovelace", TAX_ID, EMAIL);
        Customer sameData = Customer.register("Ada Lovelace", TAX_ID, EMAIL);

        assertThat(ada).isEqualTo(ada).hasSameHashCodeAs(ada).isNotEqualTo(sameData);
    }

    @Test
    @DisplayName("Un documento duplicado es una regla de negocio incumplida con el documento en el mensaje (R29)")
    void un_cliente_duplicado_es_una_regla_de_negocio_incumplida() {
        assertThat(new DuplicateCustomerException(TAX_ID))
                .isInstanceOf(DomainException.class)
                .hasMessageStartingWith("Ya eres cliente de Mon-A Bank")
                .hasMessageContaining("12345678Z");
    }
}
