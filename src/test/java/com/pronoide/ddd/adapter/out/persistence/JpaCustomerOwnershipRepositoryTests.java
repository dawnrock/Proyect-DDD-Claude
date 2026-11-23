package com.pronoide.ddd.adapter.out.persistence;

import java.util.Optional;

import com.pronoide.ddd.application.CustomerOwnershipRepository;
import com.pronoide.ddd.application.CustomerOwnershipRepositoryContract;
import com.pronoide.ddd.domain.model.TaxId;

class JpaCustomerOwnershipRepositoryTests extends CustomerOwnershipRepositoryContract {

    /** El adaptador sobre la base del test, con cada llamada en su propia transacción. */
    @Override
    protected CustomerOwnershipRepository createRepository() {
        JpaTestDatabase.clear();
        CustomerOwnershipRepository adapter =
                new JpaCustomerOwnershipRepository(JpaTestDatabase.bean(CustomerUsernameJpaRepository.class));
        return new CustomerOwnershipRepository() {
            @Override
            public void assign(TaxId customer, String username) {
                JpaTestDatabase.inTransaction(() -> adapter.assign(customer, username));
            }

            @Override
            public Optional<String> usernameOf(TaxId customer) {
                return JpaTestDatabase.inTransaction(() -> adapter.usernameOf(customer));
            }

            @Override
            public Optional<TaxId> customerOf(String username) {
                return JpaTestDatabase.inTransaction(() -> adapter.customerOf(username));
            }
        };
    }
}
