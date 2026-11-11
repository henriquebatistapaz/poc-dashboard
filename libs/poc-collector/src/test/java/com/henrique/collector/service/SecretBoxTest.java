package com.henrique.collector.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SecretBoxTest {
    @Test
    void encryptsAndAuthenticatesScheduledInput() {
        var box = new SecretBox(new byte[32]);
        String ciphertext = box.encrypt("https://example.test/feed?token=private");

        assertNotEquals("https://example.test/feed?token=private", ciphertext);
        assertEquals("https://example.test/feed?token=private", box.decrypt(ciphertext));
        byte[] wrongKey = new byte[32];
        wrongKey[0] = 1;
        assertThrows(IllegalStateException.class, () -> new SecretBox(wrongKey).decrypt(ciphertext));
    }

    @Test
    void rejectsInvalidEnvironmentKey() {
        assertThrows(IllegalArgumentException.class, () -> SecretBox.fromEnvironment("short"));
        var box = SecretBox.fromEnvironment("a".repeat(64));
        assertEquals("x", box.decrypt(box.encrypt("x")));
    }
}
