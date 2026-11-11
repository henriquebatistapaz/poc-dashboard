package com.henrique.dashboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ScheduleInputCipherTest {
    private final ScheduleInputCipher cipher = new ScheduleInputCipher(
            "0000000000000000000000000000000000000000000000000000000000000000");

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
        ScheduleInputCipher unavailable = new ScheduleInputCipher("");
        assertThrows(ResponseStatusException.class, () -> unavailable.encrypt("input"));
    }
}
