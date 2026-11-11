package com.henrique.dashboard.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.henrique.dashboard.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;

class AdminBootstrapSecurityTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final UserManagementService service = new UserManagementService(users);

    @Test
    void rejectsWeakBootstrapPassword() {
        assertThatThrownBy(() -> service.bootstrapAdmin("admin@example.org", "", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12–72 bytes");
        verifyNoInteractions(users);
    }

    @Test
    void rejectsNonBcryptBootstrapHash() {
        assertThatThrownBy(() -> service.bootstrapAdmin("admin@example.org", "plaintext-secret", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BCrypt");
        verifyNoInteractions(users);
    }

    @Test
    void rejectsIncompleteAdminBootstrapConfiguration() {
        assertThatThrownBy(() -> service.bootstrapAdmin("admin@example.org", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one credential");
        verifyNoInteractions(users);
    }
}
