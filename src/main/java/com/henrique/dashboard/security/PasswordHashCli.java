package com.henrique.dashboard.security;

import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Local interactive helper: the password is never accepted as a process argument. */
public final class PasswordHashCli {
    private PasswordHashCli() { }
    public static void main(String[] args) {
        var console = System.console();
        if (console == null) throw new IllegalStateException("Run from an interactive terminal");
        char[] password = console.readPassword("Admin password: ");
        if (password == null) throw new IllegalArgumentException("Password is required");
        try {
            String value = new String(password);
            if (value.isBlank() || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("Use 1 to 72 UTF-8 bytes");
            }
            console.printf("%s%n", new BCryptPasswordEncoder(12).encode(value));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
