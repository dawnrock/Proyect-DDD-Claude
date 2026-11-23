package com.dawnrock.ddd.adapter.out.persistence;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.dawnrock.ddd.application.AccountOwnershipRepository;
import com.dawnrock.ddd.application.AccountOwnershipRepositoryContract;

class JpaAccountOwnershipRepositoryTests extends AccountOwnershipRepositoryContract {

    /** El adaptador sobre la base del test, con cada llamada en su propia transacción. */
    @Override
    protected AccountOwnershipRepository createRepository() {
        JpaTestDatabase.clear();
        AccountOwnershipRepository adapter =
                new JpaAccountOwnershipRepository(JpaTestDatabase.bean(AccountOwnerJpaRepository.class));
        return new AccountOwnershipRepository() {
            @Override
            public void assign(UUID accountId, String username) {
                JpaTestDatabase.inTransaction(() -> adapter.assign(accountId, username));
            }

            @Override
            public Optional<String> ownerOf(UUID accountId) {
                return JpaTestDatabase.inTransaction(() -> adapter.ownerOf(accountId));
            }

            @Override
            public Set<UUID> accountsOf(String username) {
                return JpaTestDatabase.inTransaction(() -> adapter.accountsOf(username));
            }
        };
    }
}
