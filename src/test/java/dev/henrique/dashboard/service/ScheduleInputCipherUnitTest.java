package dev.henrique.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ScheduleInputCipherUnitTest {

    @Test
    void rejectsInvalidHexKey() {
        assertThatThrownBy(() -> new ScheduleInputCipher("invalid-hex!", "jwt-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64 hexadecimal characters");
    }

    @Test
    void rejectsHexKeyWithWrongLength() {
        // 16 bytes = 32 hex chars, not 64 hex chars
        assertThatThrownBy(() -> new ScheduleInputCipher("0123456789abcdef0123456789abcdef", "jwt-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64 hexadecimal characters");
    }

    @Test
    void decryptForHistoryHandlesNullAndNonConfigured() {
        ScheduleInputCipher cipher = new ScheduleInputCipher("", "jwt-secret-at-least-32-chars-long-here");
        assertThat(cipher.isConfigured()).isFalse();
        assertThat(cipher.decryptForHistory(null)).isNull();
        // non-history encrypted value without schedule key returns null
        assertThat(cipher.decryptForHistory("someEncryptedValueWithoutPrefix")).isNull();
    }

    @Test
    void decryptRejectsInvalidPayloads() {
        String keyHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        ScheduleInputCipher cipher = new ScheduleInputCipher(keyHex, "jwt-secret-at-least-32-chars-long-here");

        // Payload with <= NONCE_BYTES (12 bytes)
        // Base64 of 8 bytes
        String shortBase64 = java.util.Base64.getEncoder().encodeToString(new byte[8]);
        assertThatThrownBy(() -> cipher.decrypt(shortBase64))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);

        // Tampered payload causing tag mismatch
        byte[] valid = java.util.Base64.getDecoder().decode(cipher.encrypt("hello"));
        valid[valid.length - 1] ^= 0x01;
        String tampered = java.util.Base64.getEncoder().encodeToString(valid);
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);

        // Encrypt with invalid AES key length (not 16/24/32 bytes) through reflection to trigger GeneralSecurityException
        try {
            var method = ScheduleInputCipher.class.getDeclaredMethod("encrypt", String.class, byte[].class);
            method.setAccessible(true);
            assertThatThrownBy(() -> {
                try {
                    method.invoke(cipher, "plain", new byte[10]);
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    throw ite.getCause();
                }
            }).isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("Could not protect collection input");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        // Test NoSuchAlgorithmException catch via custom Security Provider
        java.security.Provider mockProvider = new java.security.Provider("MockSHA256", "1.0", "Mock") {};
        mockProvider.put("MessageDigest.NON_EXISTENT_ALGO", "non.existent.Class");
        java.security.Security.insertProviderAt(mockProvider, 1);
        try {
            var c = new ScheduleInputCipher("", "jwt");
            assertThatThrownBy(() -> c.digest("NON_EXISTENT_ALGO", new byte[0]))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Algorithm is not available");
        } finally {
            java.security.Security.removeProvider("MockSHA256");
        }
    }
}
