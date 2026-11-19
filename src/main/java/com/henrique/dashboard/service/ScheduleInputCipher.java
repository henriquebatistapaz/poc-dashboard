package com.henrique.dashboard.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private static final String HISTORY_PREFIX = "audit:v1:";
    private final byte[] scheduleKey;
    private final byte[] historyKey;
    private final SecureRandom random = new SecureRandom();

    public ScheduleInputCipher(@Value("${dashboard.schedule-key:}") String keyHex,
                               @Value("${dashboard.jwt-secret:}") String jwtSecret) {
        try {
            this.scheduleKey = keyHex.isBlank() ? new byte[0] : HexFormat.of().parseHex(keyHex);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("DASHBOARD_SCHEDULE_KEY must be 64 hexadecimal characters", exception);
        }
        if (scheduleKey.length != 0 && scheduleKey.length != 32) {
            throw new IllegalArgumentException("DASHBOARD_SCHEDULE_KEY must be 64 hexadecimal characters");
        }
        try {
            byte[] material = ("poc-dashboard:collection-input:v1\0" + jwtSecret).getBytes(StandardCharsets.UTF_8);
            this.historyKey = MessageDigest.getInstance("SHA-256").digest(material);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not configure collection input protection", exception);
        }
    }

    public boolean isConfigured() { return scheduleKey.length == 32; }

    public String encrypt(String plainText) {
        requireScheduleKey();
        return encrypt(plainText, scheduleKey);
    }

    public String decrypt(String value) {
        requireScheduleKey();
        return decrypt(value, scheduleKey);
    }

    public String encryptForHistory(String plainText) {
        return HISTORY_PREFIX + encrypt(plainText, historyKey);
    }

    public String decryptForHistory(String value) {
        if (value == null) return null;
        if (value.startsWith(HISTORY_PREFIX)) {
            return decrypt(value.substring(HISTORY_PREFIX.length()), historyKey);
        }
        return isConfigured() ? decrypt(value, scheduleKey) : null;
    }

    private String encrypt(String plainText, byte[] encryptionKey) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce).put(encrypted).array());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not protect collection input", exception);
        }
    }

    private String decrypt(String value, byte[] decryptionKey) {
        try {
            byte[] packed = java.util.Base64.getDecoder().decode(value);
            if (packed.length <= NONCE_BYTES) throw new IllegalArgumentException("Invalid encrypted input");
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decryptionKey, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read protected collection input", exception);
        }
    }

    private void requireScheduleKey() {
        if (!isConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Scheduling is not configured");
    }
}
