package com.henrique.dashboard.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminLoginService {
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final JwtEncoder encoder;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public AdminLoginService(@Value("${dashboard.admin-email:}") String email,
                             @Value("${dashboard.admin-password-hash:}") String passwordHash,
                             @Value("${dashboard.admin-password:}") String plainPassword,
                             @Value("${dashboard.jwt-secret:}") String jwtSecret, JwtEncoder encoder) {
        this.email = email;
        // Sem hash, aceita a senha em texto (ex.: gerada pelo Railway) e gera o hash na subida.
        this.passwordHash = !passwordHash.isBlank() || plainPassword.isBlank() ? passwordHash : passwords.encode(plainPassword);
        this.enabled = !email.isBlank() && !this.passwordHash.isBlank() && !jwtSecret.isBlank();
        this.encoder = encoder;
    }

    public LoginResponse login(String submittedEmail, String password) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Admin login is not configured");
        if (submittedEmail == null || submittedEmail.length() > 254 || password == null
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw unauthorized();
        }
        boolean validPassword = passwords.matches(password, passwordHash);
        if (!validPassword || !email.equals(submittedEmail)) throw unauthorized();
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(JwtConfiguration.ISSUER).subject(email)
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("scope", "items:read items:delete collections:write").build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", 900);
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    public record LoginResponse(String accessToken, String tokenType, int expiresIn) { }
}
