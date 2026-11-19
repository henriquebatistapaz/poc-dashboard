package com.henrique.dashboard.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.henrique.dashboard.repository.UserAccountRepository;
import com.henrique.dashboard.domain.UserAccount;
import com.henrique.dashboard.domain.UserRole;
import java.util.Optional;
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

    @Test
    void bootstrapsWeakLocalCredentialsOnlyThroughDevSpecificMethod() {
        when(users.findByEmailIgnoreCase("local")).thenReturn(Optional.empty());

        service.bootstrapDevLocalAdmin();

        var saved = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("local");
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(service.matches("local", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void devBootstrapDoesNotReplaceExistingLocalUser() {
        when(users.findByEmailIgnoreCase("local")).thenReturn(Optional.of(
                new UserAccount("local", "existing-hash", UserRole.ADMIN)));

        service.bootstrapDevLocalAdmin();

        verify(users, never()).save(any());
    }
}
