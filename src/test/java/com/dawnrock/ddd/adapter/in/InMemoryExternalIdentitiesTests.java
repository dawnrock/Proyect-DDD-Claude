package com.dawnrock.ddd.adapter.in;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

class InMemoryExternalIdentitiesTests extends ExternalIdentitiesContract {

    private InMemoryUserDetailsManager users;

    @Override
    protected ExternalIdentities createIdentities() {
        users = new InMemoryUserDetailsManager();
        return new InMemoryExternalIdentities(users::userExists);
    }

    @Override
    protected void createUser(String username) {
        users.createUser(User.withUsername(username).password("{noop}x").roles("USER").build());
    }
}
