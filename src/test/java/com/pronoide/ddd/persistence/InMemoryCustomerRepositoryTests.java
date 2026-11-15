package com.pronoide.ddd.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.domain.model.Customer;
import com.pronoide.ddd.domain.model.DuplicateCustomerException;
import com.pronoide.ddd.domain.model.EmailAddress;
import com.pronoide.ddd.domain.model.TaxId;

class InMemoryCustomerRepositoryTests {

    private final InMemoryCustomerRepository repository = new InMemoryCustomerRepository();

    private static Customer ada() {
        return Customer.register("Ada Lovelace", new TaxId("12345678Z"), new EmailAddress("ada@example.com"));
    }

    @Test
    @DisplayName("Un cliente guardado se encuentra por su documento")
    void un_cliente_guardado_se_encuentra_por_su_documento() {
        Customer ada = ada();
        repository.save(ada, "ada");

        assertThat(repository.findByTaxId(new TaxId(" 12345678 z "))).contains(ada);
    }

    @Test
    @DisplayName("Un cliente guardado se encuentra por su usuario")
    void un_cliente_guardado_se_encuentra_por_su_usuario() {
        Customer ada = ada();
        repository.save(ada, "ada");

        assertThat(repository.findByUsername("ada")).contains(ada);
    }

    @Test
    @DisplayName("El usuario de un cliente se encuentra por su documento")
    void el_usuario_de_un_cliente_se_encuentra_por_su_documento() {
        repository.save(ada(), "ada");

        assertThat(repository.usernameOf(new TaxId("12345678Z"))).contains("ada");
        assertThat(repository.usernameOf(new TaxId("87654321X"))).isEmpty();
    }

    @Test
    @DisplayName("Un documento o un usuario desconocidos no devuelven nada")
    void un_documento_o_un_usuario_desconocidos_no_devuelven_nada() {
        repository.save(ada(), "ada");

        assertThat(repository.findByTaxId(new TaxId("87654321X"))).isEmpty();
        assertThat(repository.findByUsername("grace")).isEmpty();
    }

    @Test
    @DisplayName("Guardar otro cliente con el mismo documento se rechaza y no pisa al primero (R29)")
    void guardar_otro_cliente_con_el_mismo_documento_se_rechaza() {
        Customer ada = ada();
        repository.save(ada, "ada");
        Customer other = Customer.register("Otra Ada", new TaxId("12345678Z"), new EmailAddress("otra@example.com"));

        assertThatExceptionOfType(DuplicateCustomerException.class)
                .isThrownBy(() -> repository.save(other, "otra"));
        assertThat(repository.findByTaxId(new TaxId("12345678Z"))).contains(ada);
        assertThat(repository.findByUsername("otra")).isEmpty();
    }

    @Test
    @DisplayName("Guardar de nuevo al mismo cliente no es un duplicado")
    void guardar_de_nuevo_al_mismo_cliente_no_es_un_duplicado() {
        Customer ada = ada();
        repository.save(ada, "ada");

        repository.save(ada, "ada");

        assertThat(repository.findByUsername("ada")).contains(ada);
    }
}
