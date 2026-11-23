package com.dawnrock.ddd.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.domain.model.Customer;
import com.dawnrock.ddd.domain.model.CustomerRepository;
import com.dawnrock.ddd.domain.model.CustomerRepositoryContract;
import com.dawnrock.ddd.domain.model.EmailAddress;
import com.dawnrock.ddd.domain.model.TaxId;

class JpaCustomerRepositoryTests extends CustomerRepositoryContract {

    /** El adaptador sobre la base del test, con cada llamada en su propia transacción. */
    @Override
    protected CustomerRepository createRepository() {
        JpaTestDatabase.clear();
        CustomerRepository adapter = new JpaCustomerRepository(JpaTestDatabase.bean(CustomerJpaRepository.class));
        return new CustomerRepository() {
            @Override
            public Customer save(Customer customer) {
                return JpaTestDatabase.inTransaction(() -> adapter.save(customer));
            }

            @Override
            public Optional<Customer> findByTaxId(TaxId taxId) {
                return JpaTestDatabase.inTransaction(() -> adapter.findByTaxId(taxId));
            }
        };
    }

    @Test
    @DisplayName("Un cliente se lee de la base con su identidad, su nombre y su correo")
    void un_cliente_se_lee_de_la_base_con_todos_sus_datos() {
        Customer ada = Customer.register("Ada Lovelace", new TaxId("12345678Z"), new EmailAddress("ada@example.com"));
        repository.save(ada);

        Customer leido = repository.findByTaxId(new TaxId("12345678Z")).orElseThrow();

        assertThat(leido).isNotSameAs(ada).isEqualTo(ada);
        assertThat(leido.fullName()).isEqualTo("Ada Lovelace");
        assertThat(leido.email()).isEqualTo(new EmailAddress("ada@example.com"));
    }
}
