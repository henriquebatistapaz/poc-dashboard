package dev.henrique.dashboard.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.henrique.dashboard.domain.UserAccount;
import dev.henrique.dashboard.domain.UserRole;
import dev.henrique.dashboard.repository.UserAccountRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class UserManagementServiceUnitTest {

    private UserAccountRepository users;
    private UserManagementService service;

    @BeforeEach
    void setUp() {
        users = mock(UserAccountRepository.class);
        service = new UserManagementService(users);
    }

    @Test
    void bootstrapAdminReturnsSilentlyWhenAllParametersBlankOrNull() {
        service.bootstrapAdmin(null, null, null);
        service.bootstrapAdmin("", "", "");
        service.bootstrapAdmin("   ", "   ", "   ");
        verifyNoInteractions(users);
    }

    @Test
    void bootstrapAdminCombinationsForFullBranchCoverage() {
        // hasEmail true, but neither hash nor password
        assertThatThrownBy(() -> service.bootstrapAdmin("admin@example.com", null, null))
                .isInstanceOf(IllegalStateException.class);

        // hasEmail false, but hasHash true
        assertThatThrownBy(() -> service.bootstrapAdmin(null, "$2a$12$e80MvXzE7vjYwZ9yN2aC1.oU1jT5pM.yK.2vG4Q8kR5sT7uV9wX1y", null))
                .isInstanceOf(IllegalStateException.class);

        // hasEmail false, but hasPassword true
        assertThatThrownBy(() -> service.bootstrapAdmin(null, null, "validPassword123"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bootstrapAdminThrowsWhenEmailInvalid() {
        assertThatThrownBy(() -> service.bootstrapAdmin("invalid-email-no-at", null, "validPassword123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid email");

        String longEmail = "a".repeat(250) + "@b.com";
        assertThatThrownBy(() -> service.bootstrapAdmin(longEmail, null, "validPassword123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid email");
    }

    @Test
    void bootstrapAdminThrowsWhenBothOrNeitherCredentialProvided() {
        assertThatThrownBy(() -> service.bootstrapAdmin("admin@example.com", null, null))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> service.bootstrapAdmin("admin@example.com", "$2a$12$e80MvXzE7vjYwZ9yN2aC1.oU1jT5pM.yK.2vG4Q8kR5sT7uV9wX1y", "validPassword123"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bootstrapAdminThrowsWhenPasswordLengthOutOfBounds() {
        assertThatThrownBy(() -> service.bootstrapAdmin("admin@example.com", null, "short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12–72 bytes");

        String longPass = "a".repeat(73);
        assertThatThrownBy(() -> service.bootstrapAdmin("admin@example.com", null, longPass))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12–72 bytes");
    }

    @Test
    void bootstrapAdminThrowsWhenPasswordHashInvalidFormat() {
        assertThatThrownBy(() -> service.bootstrapAdmin("admin@example.com", "not-a-bcrypt-hash", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BCrypt");
    }

    @Test
    void bootstrapAdminSkipsIfUserAlreadyExists() {
        when(users.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(new UserAccount("admin@example.com", "hash", UserRole.ADMIN)));

        service.bootstrapAdmin("admin@example.com", null, "validPassword123");

        verify(users, never()).save(any());
    }

    @Test
    void bootstrapAdminSavesWithPlainPassword() {
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.empty());

        service.bootstrapAdmin("admin@example.com", null, "validPassword123");

        verify(users).save(any(UserAccount.class));
    }

    @Test
    void bootstrapAdminSavesWithProvidedBcryptHash() {
        String validHash = "$2a$12$e80MvXzE7vjYwZ9yN2aC1.oU1jT5pM.yK.2vG4Q8kR5sT7uV9wX1y";
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.empty());

        service.bootstrapAdmin("admin@example.com", validHash, null);

        verify(users).save(any(UserAccount.class));
    }

    @Test
    void createRejectsInvalidInputs() {
        // null or blank email
        assertThatThrownBy(() -> service.create(null, "validPassword123", UserRole.OPERATOR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.create("", "validPassword123", UserRole.OPERATOR))
                .isInstanceOf(ResponseStatusException.class);

        // email without @
        assertThatThrownBy(() -> service.create("invalid-email", "validPassword123", UserRole.OPERATOR))
                .isInstanceOf(ResponseStatusException.class);

        // email too long
        assertThatThrownBy(() -> service.create("a".repeat(250) + "@b.com", "validPassword123", UserRole.OPERATOR))
                .isInstanceOf(ResponseStatusException.class);

        // null password
        assertThatThrownBy(() -> service.create("user@example.com", null, UserRole.OPERATOR))
                .isInstanceOf(ResponseStatusException.class);

        // password < 12 bytes
        assertThatThrownBy(() -> service.create("user@example.com", "short", UserRole.OPERATOR))
                .isInstanceOf(ResponseStatusException.class);

        // password > 72 bytes
        assertThatThrownBy(() -> service.create("user@example.com", "a".repeat(73), UserRole.OPERATOR))
                .isInstanceOf(ResponseStatusException.class);

        // null role
        assertThatThrownBy(() -> service.create("user@example.com", "validPassword123", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(users.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(new UserAccount("user@example.com", "hash", UserRole.OPERATOR)));

        assertThatThrownBy(() -> service.create("user@example.com", "validPassword123", UserRole.OPERATOR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void createSavesAndReturnsNewUser() {
        when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(users.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount created = service.create("user@example.com", "validPassword123", UserRole.OPERATOR);

        assertThat(created).isNotNull();
        assertThat(created.getEmail()).isEqualTo("user@example.com");
        assertThat(created.getRole()).isEqualTo(UserRole.OPERATOR);
    }

    @Test
    void listReturnsAllUsers() {
        when(users.findAllByOrderByEmailAsc()).thenReturn(List.of(
                new UserAccount("a@example.com", "h1", UserRole.ADMIN),
                new UserAccount("b@example.com", "h2", UserRole.OPERATOR)
        ));

        List<UserAccount> result = service.list();
        assertThat(result).hasSize(2);
    }

    @Test
    void deactivateThrowsNotFoundWhenUserMissing() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(99L, "admin@example.com"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deactivateThrowsConflictWhenAdminDeactivatesSelf() {
        UserAccount currentAdmin = new UserAccount("admin@example.com", "hash", UserRole.ADMIN);
        when(users.findById(1L)).thenReturn(Optional.of(currentAdmin));

        assertThatThrownBy(() -> service.deactivate(1L, "admin@example.com"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void deactivateDisablesUserAccount() {
        UserAccount operator = new UserAccount("operator@example.com", "hash", UserRole.OPERATOR);
        when(users.findById(2L)).thenReturn(Optional.of(operator));

        service.deactivate(2L, "admin@example.com");

        assertThat(operator.isEnabled()).isFalse();
    }

    @Test
    void matchesHandlesNullOrExceedingLength() {
        assertThat(service.matches(null, "somehash")).isFalse();
        assertThat(service.matches("a".repeat(73), "somehash")).isFalse();
    }
}
