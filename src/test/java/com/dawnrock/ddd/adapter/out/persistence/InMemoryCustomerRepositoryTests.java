package com.dawnrock.ddd.adapter.out.persistence;

import com.dawnrock.ddd.domain.model.CustomerRepository;
import com.dawnrock.ddd.domain.model.CustomerRepositoryContract;

class InMemoryCustomerRepositoryTests extends CustomerRepositoryContract {

    @Override
    protected CustomerRepository createRepository() {
        return new InMemoryCustomerRepository();
    }
}
