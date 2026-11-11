package com.henrique.dashboard.service;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ScheduleInputCipher {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public ScheduleInputCipher(@Value("${dashboard.schedule-key:}") String keyHex) {
        try {
            this.key = keyHex.isBlank() ? new byte[0] : HexFormat.of().parseHex(keyHex);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("DASHBOARD_SCHEDULE_KEY must be 64 hexadecimal characters", exception);
        }
        if (key.length != 0 && key.length != 32) {
            throw new IllegalArgumentException("DASHBOARD_SCHEDULE_KEY must be 64 hexadecimal characters");
        }
    }

    public boolean isConfigured() { return key.length == 32; }

    public String encrypt(String plainText) {
        requireConfigured();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce).put(encrypted).array());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not protect scheduled collection input", exception);
        }
    }

    public String decrypt(String value) {
        requireConfigured();
        try {
            byte[] packed = java.util.Base64.getDecoder().decode(value);
            if (packed.length <= NONCE_BYTES) throw new IllegalArgumentException("Invalid encrypted input");
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read scheduled collection input", exception);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Scheduling is not configured");
    }
}
