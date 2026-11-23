package dev.henrique.dashboard.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminUserBootstrap implements ApplicationRunner {
    private final UserManagementService users;
    private final String email;
    private final String passwordHash;
    private final String password;

    public AdminUserBootstrap(UserManagementService users,
            @Value("${dashboard.admin-email:}") String email,
            @Value("${dashboard.admin-password-hash:}") String passwordHash,
            @Value("${dashboard.admin-password:}") String password) {
        this.users = users;
        this.email = email;
        this.passwordHash = passwordHash;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) { users.bootstrapAdmin(email, passwordHash, password); }
}
