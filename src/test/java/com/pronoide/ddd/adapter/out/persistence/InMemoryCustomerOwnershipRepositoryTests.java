package com.pronoide.ddd.adapter.out.persistence;

import com.pronoide.ddd.application.CustomerOwnershipRepository;
import com.pronoide.ddd.application.CustomerOwnershipRepositoryContract;

class InMemoryCustomerOwnershipRepositoryTests extends CustomerOwnershipRepositoryContract {

    @Override
    protected CustomerOwnershipRepository createRepository() {
        return new InMemoryCustomerOwnershipRepository();
    }
}
