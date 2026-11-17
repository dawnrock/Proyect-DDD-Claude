package com.pronoide.ddd.adapter.out.persistence;

import com.pronoide.ddd.domain.model.CustomerRepository;
import com.pronoide.ddd.domain.model.CustomerRepositoryContract;

class InMemoryCustomerRepositoryTests extends CustomerRepositoryContract {

    @Override
    protected CustomerRepository createRepository() {
        return new InMemoryCustomerRepository();
    }
}
