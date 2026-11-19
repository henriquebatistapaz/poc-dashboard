package com.henrique.dashboard.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class DevLocalAccountRunner implements ApplicationRunner {
    private final UserManagementService users;

    public DevLocalAccountRunner(UserManagementService users) {
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        users.bootstrapDevLocalAdmin();
    }
}
