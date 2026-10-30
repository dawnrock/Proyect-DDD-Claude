package com.pronoide.ddd.adapter.out.persistence;

import com.pronoide.ddd.application.AccountOwnershipRepository;
import com.pronoide.ddd.application.AccountOwnershipRepositoryContract;

class InMemoryAccountOwnershipRepositoryTests extends AccountOwnershipRepositoryContract {

    @Override
    protected AccountOwnershipRepository createRepository() {
        return new InMemoryAccountOwnershipRepository();
    }
}
