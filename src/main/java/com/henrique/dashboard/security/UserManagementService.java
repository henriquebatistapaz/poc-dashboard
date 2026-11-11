package com.henrique.dashboard.security;

import com.henrique.dashboard.domain.UserAccount;
import com.henrique.dashboard.domain.UserRole;
import com.henrique.dashboard.repository.UserAccountRepository;
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
        if (email == null || email.isBlank() || (passwordHash == null || passwordHash.isBlank())
                && (plainPassword == null || plainPassword.isBlank())) return;
        if (users.findByEmailIgnoreCase(email).isPresent()) return;
        String hash = passwordHash == null || passwordHash.isBlank() ? passwords.encode(plainPassword) : passwordHash;
        users.save(new UserAccount(email.trim(), hash, UserRole.ADMIN));
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
