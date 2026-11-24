package dev.henrique.dashboard.security;

import dev.henrique.dashboard.domain.UserAccount;
import dev.henrique.dashboard.domain.UserRole;
import dev.henrique.dashboard.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserManagementService {
    private final UserAccountRepository users;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public UserManagementService(UserAccountRepository users) { this.users = users; }

    @Transactional
    public void bootstrapAdmin(String email, String passwordHash, String plainPassword) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasHash = passwordHash != null && !passwordHash.isBlank();
        boolean hasPassword = plainPassword != null && !plainPassword.isBlank();
        if (!hasEmail && !hasHash && !hasPassword) return;
        if (!hasEmail || email.length() > 254 || hasHash == hasPassword) {
            throw new IllegalStateException("Admin bootstrap requires a valid email and exactly one credential");
        }
        if (hasPassword) {
            int length = plainPassword.getBytes(StandardCharsets.UTF_8).length;
            if (length < 12 || length > 72) {
                throw new IllegalStateException("Admin bootstrap password must contain 12–72 bytes");
            }
        }
        if (hasHash && !passwordHash.matches("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}")) {
            throw new IllegalStateException("Admin bootstrap password hash must be BCrypt");
        }
        if (users.findByEmailIgnoreCase(email).isPresent()) return;
        String hash = passwordHash == null || passwordHash.isBlank() ? passwords.encode(plainPassword) : passwordHash;
        users.save(new UserAccount(email.trim(), hash, UserRole.ADMIN));
    }

    @Transactional
    public void bootstrapDevLocalAdmin() {
        if (users.findByEmailIgnoreCase("local").isPresent()) return;
        users.save(new UserAccount("local", passwords.encode("local"), UserRole.ADMIN));
    }

    @Transactional
    public UserAccount create(String email, String password, UserRole role) {
        if (email == null || email.isBlank() || email.length() > 254 || !email.contains("@")
                || password == null || password.getBytes(StandardCharsets.UTF_8).length < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72 || role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid email, role and 12–72 byte password required");
        }
        if (users.findByEmailIgnoreCase(email.trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists");
        }
        return users.save(new UserAccount(email.trim(), passwords.encode(password), role));
    }

    @Transactional(readOnly = true)
    public List<UserAccount> list() { return users.findAllByOrderByEmailAsc(); }

    @Transactional
    public void deactivate(long id, String currentEmail) {
        UserAccount account = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (account.getEmail().equalsIgnoreCase(currentEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The active administrator cannot deactivate itself");
        }
        account.deactivate();
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        return rawPassword != null && rawPassword.getBytes(StandardCharsets.UTF_8).length <= 72
                && passwords.matches(rawPassword, encodedPassword);
    }
}
