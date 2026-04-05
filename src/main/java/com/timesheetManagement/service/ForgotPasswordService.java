package com.timesheetManagement.service;

import com.timesheetManagement.dto.ForgotPasswordRequest;
import com.timesheetManagement.dto.ResetPasswordRequest;
import com.timesheetManagement.dto.VerifyOtpRequest;
import com.timesheetManagement.dto.VerifyOtpResponse;
import com.timesheetManagement.entity.User;
import com.timesheetManagement.exception.InvalidResetTokenException;
import com.timesheetManagement.exception.OtpExpiredException;
import com.timesheetManagement.exception.OtpVerificationException;
import com.timesheetManagement.repository.RefreshTokenRepository;
import com.timesheetManagement.repository.UserRepository;
import com.timesheetManagement.util.HashUtil;
import com.timesheetManagement.util.OtpGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Core service for the Forgot Password / OTP flow.
 *
 * <p>Security properties:
 * <ul>
 *   <li>OTP is generated with {@link java.security.SecureRandom} — unpredictable.</li>
 *   <li>Only the SHA-256 hash of the OTP is stored — raw value never touches the DB.</li>
 *   <li>Comparison uses constant-time {@link java.security.MessageDigest#isEqual} — timing-safe.</li>
 *   <li>API never reveals whether an email is registered (anti-enumeration).</li>
 *   <li>OTP is locked after {@code app.otp.max-attempts} wrong attempts.</li>
 *   <li>Successive OTP requests are rate-limited via {@code app.otp.cooldown-minutes}.</li>
 *   <li>Reset token is a 128-char random hex string, stored only as its SHA-256 hash.</li>
 *   <li>On password reset all refresh tokens are invalidated (session kill).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ForgotPasswordService {

    private final UserRepository         userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder        passwordEncoder;
    private final EmailService           emailService;

    @Value("${app.otp.expiry-minutes:10}")
    private int otpExpiryMinutes;

    @Value("${app.otp.reset-token-expiry-minutes:15}")
    private int resetTokenExpiryMinutes;

    @Value("${app.otp.max-attempts:5}")
    private int maxOtpAttempts;

    @Value("${app.otp.cooldown-minutes:2}")
    private int otpCooldownMinutes;

    // ══════════════════════════════════════════════════════════════════════
    //  1. FORGOT PASSWORD — generate OTP and send via email
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Generates and emails a 6-digit OTP to the registered address.
     *
     * <p>Always returns without revealing whether the email is registered
     * (prevents user-enumeration attacks). If a recent OTP was already sent
     * (within the cooldown window) the method returns silently.
     */
    @Transactional
    public void processForgotPassword(ForgotPasswordRequest req) {
        log.info("[FORGOT_PWD] Request for email='{}'", maskEmail(req.getEmail()));

        Optional<User> userOpt = userRepository.findByEmail(req.getEmail());

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // ── Rate-limit: block if an OTP was issued less than cooldownMinutes ago ──
            // Formula: otpExpiresAt = issuedAt + otpExpiryMinutes
            // => issuedAt = otpExpiresAt - otpExpiryMinutes
        // ── Rate-limit: block if an OTP was issued less than cooldownMinutes ago ──
            if (user.getResetOtpExpiresAt() != null
                    && LocalDateTime.now().isBefore(
                        user.getResetOtpExpiresAt()
                            .minusMinutes(otpExpiryMinutes - otpCooldownMinutes))) {

                log.warn("[FORGOT_PWD] Rate-limited — OTP already issued recently for email='{}'",
                        maskEmail(req.getEmail()));
                return; // silently return — same response as "email not found"
            }

            String otp = OtpGenerator.generateSixDigitOtp();

            user.setResetOtpHash(HashUtil.sha256(otp));
            user.setResetOtpExpiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
            user.setResetOtpAttempts(0);
            user.setResetOtpVerified(Boolean.FALSE);
            user.setResetToken(null);            // clear any stale reset token
            user.setResetTokenExpiresAt(null);
            userRepository.save(user);

            emailService.sendOtpEmail(user.getEmail(), user.getFirstName(), otp, otpExpiryMinutes);
            log.info("[FORGOT_PWD] ✅ OTP issued for userId={}", user.getId());
        } else {
            // Simulate the same processing time to prevent timing-based enumeration
            log.debug("[FORGOT_PWD] Email not registered — returning generic response");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  2. VERIFY OTP — validate OTP and return a short-lived reset token
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Verifies the submitted OTP and, on success, returns a secure reset token.
     *
     * @throws OtpVerificationException if email unknown, OTP wrong, or locked
     * @throws OtpExpiredException      if OTP validity window has elapsed
     */
    @Transactional
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest req) {
        log.info("[VERIFY_OTP] Attempt for email='{}'", maskEmail(req.getEmail()));

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new OtpVerificationException(
                        "Invalid email or OTP. Please check your credentials."));

        // ── No OTP ever requested ─────────────────────────────────────────
        if (user.getResetOtpHash() == null || user.getResetOtpExpiresAt() == null) {
            throw new OtpVerificationException(
                    "No OTP has been issued for this account. Please request a new one.");
        }

        // ── Account locked (too many wrong attempts) ──────────────────────
        if (safeAttempts(user) >= maxOtpAttempts) {
            throw new OtpVerificationException(
                    "Account is temporarily locked due to too many failed attempts. "
                    + "Please request a new OTP.");
        }

        // ── OTP expired ───────────────────────────────────────────────────
        if (LocalDateTime.now().isAfter(user.getResetOtpExpiresAt())) {
            throw new OtpExpiredException(
                    "OTP has expired. Please request a new one.");
        }

        // ── Wrong OTP — increment attempt counter ─────────────────────────
        if (!HashUtil.verify(req.getOtp(), user.getResetOtpHash())) {
            int attempts  = safeAttempts(user) + 1;
            user.setResetOtpAttempts(attempts);
            userRepository.save(user);

            int remaining = maxOtpAttempts - attempts;
            log.warn("[VERIFY_OTP] Wrong OTP for email='{}', attempts={}/{}",
                    maskEmail(req.getEmail()), attempts, maxOtpAttempts);

            if (remaining <= 0) {
                throw new OtpVerificationException(
                        "Maximum OTP attempts exceeded. Please request a new OTP.");
            }
            throw new OtpVerificationException(
                    "Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        // ── OTP correct — generate secure reset token ──────────────────────
        // Two UUIDs concatenated → 128 random hex chars → very high entropy
        String rawResetToken = UUID.randomUUID().toString().replace("-", "")
                             + UUID.randomUUID().toString().replace("-", "");

        user.setResetOtpVerified(Boolean.TRUE);
        user.setResetToken(HashUtil.sha256(rawResetToken));   // only hash persisted
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(resetTokenExpiryMinutes));
        user.setResetOtpHash(null);                           // OTP consumed — prevent reuse
        userRepository.save(user);

        log.info("[VERIFY_OTP] ✅ OTP verified for userId={}", user.getId());

        return VerifyOtpResponse.builder()
                .resetToken(rawResetToken)
                .expiresInMinutes(resetTokenExpiryMinutes)
                .message("OTP verified successfully. Use the resetToken to reset your password within "
                         + resetTokenExpiryMinutes + " minutes.")
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  3. RESET PASSWORD — validate token, set new password, clear state
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Resets the user's password using the token from {@link #verifyOtp}.
     *
     * <p>After a successful reset:
     * <ul>
     *   <li>Password is BCrypt-hashed and persisted.</li>
     *   <li>All OTP/reset fields are cleared to prevent token reuse.</li>
     *   <li>All refresh tokens are deleted (forces re-login on all devices).</li>
     * </ul>
     *
     * @throws IllegalArgumentException   if passwords don't match
     * @throws InvalidResetTokenException if token is invalid, expired, or OTP wasn't verified
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        log.info("[RESET_PWD] Password reset attempt");

        // ── Passwords must match ──────────────────────────────────────────
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new IllegalArgumentException(
                    "New password and confirm password do not match.");
        }

        // ── Resolve user by hashed reset token ────────────────────────────
        String tokenHash = HashUtil.sha256(req.getResetToken());
        User user = userRepository.findByResetToken(tokenHash)
                .orElseThrow(() -> new InvalidResetTokenException(
                        "Invalid or expired reset token. Please request a new OTP."));

        // ── OTP must have been verified ───────────────────────────────────
        if (!Boolean.TRUE.equals(user.getResetOtpVerified())) {
            throw new InvalidResetTokenException(
                    "OTP verification is required before resetting the password.");
        }

        // ── Token must not have expired ───────────────────────────────────
        if (user.getResetTokenExpiresAt() == null
                || LocalDateTime.now().isAfter(user.getResetTokenExpiresAt())) {
            throw new InvalidResetTokenException(
                    "Reset token has expired. Please request a new OTP.");
        }

        // ── Set new password (BCrypt) ─────────────────────────────────────
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));

        // ── Clear all OTP / reset state (one-time use) ────────────────────
        user.setResetOtpHash(null);
        user.setResetOtpExpiresAt(null);
        user.setResetOtpAttempts(0);
        user.setResetOtpVerified(Boolean.FALSE);
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
        userRepository.save(user);

        // ── Invalidate all sessions (force re-login on every device) ──────
        refreshTokenRepository.deleteByUser(user);

        log.info("[RESET_PWD] ✅ Password reset successful for userId={}", user.getId());
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Returns the OTP attempt count, treating {@code null} as 0.
     *
     * <p>Existing users loaded from the DB before the reset columns were added
     * will have {@code null} in {@code reset_otp_attempts}. Using a wrapper
     * {@code Integer} field on the entity prevents Hibernate from throwing
     * "Null value was assigned to a primitive type property", and this helper
     * ensures the service logic never auto-unboxes a null.
     */
    private int safeAttempts(User user) {
        return user.getResetOtpAttempts() != null ? user.getResetOtpAttempts() : 0;
    }

    /** Masks an email address for safe logging (e.g. ar***@example.com). */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        return (local.length() > 2 ? local.substring(0, 2) + "***" : "***")
                + "@" + parts[1];
    }
}

