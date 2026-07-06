package com.omc.gateway.infrastructure.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

public class AesTicketUtil {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_LEN = 128;

    // Cipher.getInstance() is expensive (JCA provider lookup + allocation).
    // GCM requires init() per call due to per-request IV, but the Cipher instance itself is reusable.
    private static final ThreadLocal<Cipher> TL_CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(ALGO);
        } catch (Exception e) {
            throw new RuntimeException("AES/GCM cipher unavailable", e);
        }
    });

    // AES key is constant per deployment. Cache SecretKeySpec to avoid per-request allocation.
    // Volatile pair ordering guarantees: if cachedKeyArray write is visible, cachedKeySpec write is too.
    private static volatile byte[] cachedKeyArray;
    private static volatile SecretKeySpec cachedKeySpec;

    private static SecretKeySpec keySpec(byte[] key) {
        if (key != cachedKeyArray) {
            cachedKeySpec = new SecretKeySpec(key, "AES");
            cachedKeyArray = key;
        }
        return cachedKeySpec;
    }

    public static String encrypt(String plaintext, byte[] key) throws Exception {
        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = TL_CIPHER.get();
        cipher.init(Cipher.ENCRYPT_MODE, keySpec(key), new GCMParameterSpec(TAG_LEN, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] result = new byte[IV_LEN + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, IV_LEN);
        System.arraycopy(ciphertext, 0, result, IV_LEN, ciphertext.length);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
    }

    public static String decrypt(String ticket, byte[] key) throws Exception {
        byte[] raw = Base64.getUrlDecoder().decode(ticket);
        byte[] iv = Arrays.copyOfRange(raw, 0, IV_LEN);
        byte[] ciphertext = Arrays.copyOfRange(raw, IV_LEN, raw.length);

        Cipher cipher = TL_CIPHER.get();
        cipher.init(Cipher.DECRYPT_MODE, keySpec(key), new GCMParameterSpec(TAG_LEN, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    public static String extractUserId(String payload) {
        return payload.split(":", 2)[0];
    }

    public static boolean isExpired(String payload) {
        String[] parts = payload.split(":", 2);
        long expireAt = Long.parseLong(parts[1]);
        return Instant.now().getEpochSecond() > expireAt;
    }
}
