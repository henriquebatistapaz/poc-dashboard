package com.henrique.dashboard.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.henrique.dashboard.domain.UserAccount;
import com.henrique.dashboard.domain.UserRole;
import com.henrique.dashboard.repository.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.web.server.ResponseStatusException;

class AdminLoginServiceUnitTest {

    @Test
    void rejectsWhenServiceDisabled() {
        JwtEncoder encoder = mock(JwtEncoder.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        UserManagementService passwords = mock(UserManagementService.class);

        AdminLoginService service = new AdminLoginService("", encoder, users, passwords);

        assertThatThrownBy(() -> service.login("admin@example.com", "secret123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Admin login is not configured");
    }

    @Test
    void rejectsWhenNoActiveUsersExist() {
        JwtEncoder encoder = mock(JwtEncoder.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        UserManagementService passwords = mock(UserManagementService.class);

        when(users.existsByEnabledTrue()).thenReturn(false);

        AdminLoginService service = new AdminLoginService("some-valid-secret-with-at-least-32-bytes-length", encoder, users, passwords);

        assertThatThrownBy(() -> service.login("admin@example.com", "secret123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Admin login is not configured");
    }

    @Test
    void rejectsWhenCredentialsMalformed() {
        JwtEncoder encoder = mock(JwtEncoder.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        UserManagementService passwords = mock(UserManagementService.class);

        when(users.existsByEnabledTrue()).thenReturn(true);

        AdminLoginService service = new AdminLoginService("some-valid-secret-with-at-least-32-bytes-length", encoder, users, passwords);

        // Null email
        assertThatThrownBy(() -> service.login(null, "secret123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid credentials");

        // Long email (>254)
        assertThatThrownBy(() -> service.login("a".repeat(255), "secret123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid credentials");

        // Null password
        assertThatThrownBy(() -> service.login("admin@example.com", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid credentials");

        // Long password (>72 bytes)
        assertThatThrownBy(() -> service.login("admin@example.com", "a".repeat(73)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void logsInOperatorSuccessfully() {
        JwtEncoder encoder = mock(JwtEncoder.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        UserManagementService passwords = mock(UserManagementService.class);

        when(users.existsByEnabledTrue()).thenReturn(true);
        UserAccount operator = new UserAccount("operator@example.com", "hashed", UserRole.OPERATOR);
        when(users.findByEmailIgnoreCaseAndEnabledTrue("operator@example.com")).thenReturn(Optional.of(operator));
        when(passwords.matches("operatorPass123", "hashed")).thenReturn(true);

        Jwt jwt = mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("mocked-jwt-token");
        when(encoder.encode(any())).thenReturn(jwt);

        AdminLoginService service = new AdminLoginService("some-valid-secret-with-at-least-32-bytes-length", encoder, users, passwords);

        var response = service.login("operator@example.com", "operatorPass123");
        assertThat(response.accessToken()).isEqualTo("mocked-jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }
}
