package com.dawnrock.ddd.adapter.out.persistence;

import com.dawnrock.ddd.application.AccountOwnershipRepository;
import com.dawnrock.ddd.application.AccountOwnershipRepositoryContract;

class InMemoryAccountOwnershipRepositoryTests extends AccountOwnershipRepositoryContract {

    @Override
    protected AccountOwnershipRepository createRepository() {
        return new InMemoryAccountOwnershipRepository();
    }
}
