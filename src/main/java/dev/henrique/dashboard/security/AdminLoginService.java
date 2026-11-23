package dev.henrique.dashboard.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import dev.henrique.dashboard.domain.UserAccount;
import dev.henrique.dashboard.domain.UserRole;
import dev.henrique.dashboard.repository.UserAccountRepository;

@Service
public class AdminLoginService {
    private final boolean enabled;
    private final JwtEncoder encoder;
    private final UserAccountRepository users;
    private final UserManagementService passwords;

    public AdminLoginService(@org.springframework.beans.factory.annotation.Value("${dashboard.jwt-secret:}") String jwtSecret,
                             JwtEncoder encoder, UserAccountRepository users, UserManagementService passwords) {
        this.enabled = !jwtSecret.isBlank();
        this.encoder = encoder;
        this.users = users;
        this.passwords = passwords;
    }

    public LoginResponse login(String submittedEmail, String password) {
        if (!enabled || !users.existsByEnabledTrue()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Admin login is not configured");
        }
        if (submittedEmail == null || submittedEmail.length() > 254 || password == null
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw unauthorized();
        }
        UserAccount user = users.findByEmailIgnoreCaseAndEnabledTrue(submittedEmail).orElseThrow(this::unauthorized);
        if (!passwords.matches(password, user.getPasswordHash())) throw unauthorized();
        Instant now = Instant.now();
        String scopes = user.getRole() == UserRole.ADMIN
                ? "items:read items:delete collections:write admin:users"
                : "items:read collections:write";
        var claims = JwtClaimsSet.builder().issuer(JwtConfiguration.ISSUER).subject(user.getEmail())
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("scope", scopes).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", 900);
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    public record LoginResponse(String accessToken, String tokenType, int expiresIn) { }
}
