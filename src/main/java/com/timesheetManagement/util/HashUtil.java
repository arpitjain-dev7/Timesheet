package com.timesheetManagement.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for one-way hashing using SHA-256.
 *
 * <p>Used to hash OTPs and reset tokens before storing them in the database.
 * The raw value is never persisted — only the hex-encoded digest is stored.
 *
 * <p>SHA-256 is appropriate here because OTPs and UUIDs already have high
 * entropy, making brute-force attacks infeasible within the short validity window.
 */
public final class HashUtil {

    private HashUtil() {}

    /**
     * Computes the SHA-256 hex digest of the given plaintext.
     *
     * @param input plaintext value (OTP digit string or UUID token)
     * @return lowercase hex-encoded SHA-256 digest (64 characters)
     * @throws IllegalStateException if SHA-256 is somehow unavailable (never in practice)
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java spec — this path is unreachable
            throw new IllegalStateException("SHA-256 algorithm not available on this JVM", e);
        }
    }

    /**
     * Constant-time comparison of a plaintext input against a stored SHA-256 hash.
     * Uses {@link MessageDigest#isEqual} to prevent timing attacks.
     *
     * @param input  the raw value to verify
     * @param stored the previously stored SHA-256 hex hash
     * @return {@code true} if {@code sha256(input)} equals {@code stored}
     */
    public static boolean verify(String input, String stored) {
        if (input == null || stored == null) return false;
        byte[] computedBytes = sha256(input).getBytes(StandardCharsets.UTF_8);
        byte[] storedBytes   = stored.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(computedBytes, storedBytes);
    }
}

