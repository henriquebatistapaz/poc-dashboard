package com.henrique.dashboard.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtConfiguration {
    public static final String ISSUER = "poc-dashboard";

    @Bean
    SecretKey jwtKey(@Value("${dashboard.jwt-secret:}") String configured) {
        byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            // Login is disabled without configuration; an ephemeral verification key keeps read-only mode usable.
            bytes = new byte[32];
            new java.security.SecureRandom().nextBytes(bytes);
        } else if (bytes.length < 32) {
            throw new IllegalArgumentException("DASHBOARD_JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }
}
