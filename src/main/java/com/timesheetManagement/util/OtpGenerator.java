package com.timesheetManagement.util;

import java.security.SecureRandom;

/**
 * Generates cryptographically secure one-time passwords.
 *
 * <p>Uses {@link SecureRandom} (not {@code Math.random()}) to ensure
 * the OTP is unpredictable. The single instance is thread-safe.
 */
public final class OtpGenerator {

    /** Thread-safe, cryptographically strong random number generator. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private OtpGenerator() {}

    /**
     * Generates a 6-digit numeric OTP in the range [100000, 999999].
     *
     * @return zero-padded 6-digit string, e.g. {@code "045823"}
     */
    public static String generateSixDigitOtp() {
        // nextInt(900000) → [0, 899999] → + 100000 → [100000, 999999]
        int otp = 100_000 + SECURE_RANDOM.nextInt(900_000);
        return String.valueOf(otp);
    }
}

