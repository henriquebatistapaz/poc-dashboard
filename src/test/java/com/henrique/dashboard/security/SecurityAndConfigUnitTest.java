package com.henrique.dashboard.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class SecurityAndConfigUnitTest {

    @Test
    void jwtConfigurationRejectsShortSecret() {
        JwtConfiguration config = new JwtConfiguration();
        assertThatThrownBy(() -> config.jwtKey("too-short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DASHBOARD_JWT_SECRET must contain at least 32 UTF-8 bytes");
    }

    @Test
    void jwtConfigurationAcceptsEmptyAndGeneratesEphemeralKey() {
        JwtConfiguration config = new JwtConfiguration();
        SecretKey key = config.jwtKey("");
        assertThat(key).isNotNull();
        assertThat(key.getEncoded()).hasSize(32);

        assertThat(config.jwtEncoder(key)).isNotNull();
        assertThat(config.jwtDecoder(key)).isNotNull();
    }

    @Test
    void devLocalAccountRunnerExecutesBootstrap() {
        UserManagementService users = mock(UserManagementService.class);
        DevLocalAccountRunner runner = new DevLocalAccountRunner(users);
        runner.run(new DefaultApplicationArguments(new String[0]));
        verify(users).bootstrapDevLocalAdmin();
    }
}
