package com.dawnrock.ddd.adapter.out.persistence;

import com.dawnrock.ddd.application.CustomerOwnershipRepository;
import com.dawnrock.ddd.application.CustomerOwnershipRepositoryContract;

class InMemoryCustomerOwnershipRepositoryTests extends CustomerOwnershipRepositoryContract {

    @Override
    protected CustomerOwnershipRepository createRepository() {
        return new InMemoryCustomerOwnershipRepository();
    }
}
