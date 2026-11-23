package dev.henrique.dashboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ScheduleInputCipherTest {
    private final ScheduleInputCipher cipher = new ScheduleInputCipher(
            "0000000000000000000000000000000000000000000000000000000000000000",
            "integration-test-secret-at-least-32-bytes");

    @Test
    void encryptsInputsAndAuthenticatesCiphertext() {
        String input = "https://feed.example/?token=synthetic-secret";
        String encrypted = cipher.encrypt(input);
        assertNotEquals(input, encrypted);
        assertEquals(input, cipher.decrypt(encrypted));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(encrypted.substring(0, encrypted.length() - 4) + "AAAA"));
    }

    @Test
    void refusesSchedulingWhenKeyIsNotConfigured() {
        ScheduleInputCipher unavailable = new ScheduleInputCipher("", "integration-test-secret-at-least-32-bytes");
        assertThrows(ResponseStatusException.class, () -> unavailable.encrypt("input"));
    }

    @Test
    void protectsCollectionHistoryWithoutEnablingScheduling() {
        String input = "https://feed.example/?token=synthetic-secret";
        String encrypted = cipher.encryptForHistory(input);
        assertNotEquals(input, encrypted);
        assertEquals(input, cipher.decryptForHistory(encrypted));

        ScheduleInputCipher withoutScheduleKey = new ScheduleInputCipher("", "integration-test-secret-at-least-32-bytes");
        String historyValue = withoutScheduleKey.encryptForHistory(input);
        assertEquals(input, withoutScheduleKey.decryptForHistory(historyValue));
    }
}
