package com.timesheetManagement.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Centralised email service.
 *
 * <p><strong>Important — self-invocation rule:</strong> every public send method
 * must be annotated {@code @Async} directly and must build + dispatch the
 * {@link MimeMessage} itself (not delegate to another method in this class).
 * Spring's AOP proxy is bypassed on internal self-calls, so delegating to
 * {@code this.sendHtmlEmail()} inside an already-executing bean method would
 * silently skip the async executor and block the caller's thread.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.frontend.login-url:http://localhost:5173/login}")
    private String loginUrl;

    // ══════════════════════════════════════════════════════════════════════
    //  WELCOME EMAIL — sent after every new user creation
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Sends a branded welcome email with the new user's login credentials.
     *
     * <p>Runs on the {@code emailTaskExecutor} thread pool so it never blocks
     * the HTTP response. All exceptions are caught and logged — email failure
     * must not roll back the user-creation transaction.
     *
     * @param to        recipient email address
     * @param fullName  user's first + last name for personalisation
     * @param username  assigned login username
     * @param password  <em>plaintext</em> temporary password (captured by the
     *                  caller before {@code BCryptPasswordEncoder.encode()})
     * @param role      display-friendly role string (e.g. "User", "Manager")
     */
    @Async("emailTaskExecutor")
    public void sendWelcomeEmail(String to,
                                  String fullName,
                                  String username,
                                  String password,
                                  String role) {

        log.info("[WELCOME_EMAIL] ▶ Preparing welcome email — username='{}', to='{}'",
                username, maskEmail(to));
        try {
            // ── 1. Load & populate template ───────────────────────────────
            log.debug("[WELCOME_EMAIL] Loading template 'welcome-email.html'");
            String html = loadTemplate("welcome-email.html")
                    .replace("{{FULL_NAME}}", fullName  != null ? fullName  : username)
                    .replace("{{EMAIL}}",     to)
                    .replace("{{USERNAME}}",  username)
                    .replace("{{PASSWORD}}",  password  != null ? password  : "")
                    .replace("{{ROLE}}",      role      != null ? role      : "User")
                    .replace("{{LOGIN_URL}}", loginUrl);
            log.debug("[WELCOME_EMAIL] Template rendered successfully ({} chars)", html.length());

            // ── 2. Build MIME message ─────────────────────────────────────
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("Welcome to Timesheet Management — Your Account Details");
            helper.setText(html, true);
            log.debug("[WELCOME_EMAIL] MIME message built — from='{}', to='{}'",
                    fromAddress, maskEmail(to));

            // ── 3. Send ───────────────────────────────────────────────────
            log.info("[WELCOME_EMAIL] Sending via SMTP to '{}'...", maskEmail(to));
            mailSender.send(message);
            log.info("[WELCOME_EMAIL] ✅ Welcome email sent successfully to '{}'", maskEmail(to));

        } catch (MessagingException e) {
            log.error("[WELCOME_EMAIL] ❌ SMTP/messaging error sending to '{}': {}",
                    maskEmail(to), e.getMessage(), e);
        } catch (IllegalStateException e) {
            log.error("[WELCOME_EMAIL] ❌ Template error for '{}': {}",
                    maskEmail(to), e.getMessage(), e);
        } catch (Exception e) {
            log.error("[WELCOME_EMAIL] ❌ Unexpected error sending welcome email to '{}': {}",
                    maskEmail(to), e.getClass().getSimpleName() + " — " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  OTP EMAIL — sent during forgot-password flow
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Sends a 6-digit OTP to the user's email for the forgot-password flow.
     *
     * @param to            recipient email
     * @param firstName     used to personalise the greeting line
     * @param otp           plaintext OTP (already hashed + stored before this call)
     * @param expiryMinutes validity window shown in the email body
     */
    @Async("emailTaskExecutor")
    public void sendOtpEmail(String to, String firstName, String otp, int expiryMinutes) {

        log.info("[OTP_EMAIL] ▶ Preparing OTP email for '{}'", maskEmail(to));
        try {
            // ── 1. Load & populate template ───────────────────────────────
            log.debug("[OTP_EMAIL] Loading template 'forgot-password-otp.html'");
            String html = loadTemplate("forgot-password-otp.html")
                    .replace("{{FIRST_NAME}}",     firstName != null ? firstName : "User")
                    .replace("{{OTP}}",             otp)
                    .replace("{{EXPIRY_MINUTES}}", String.valueOf(expiryMinutes));
            log.debug("[OTP_EMAIL] Template rendered ({} chars)", html.length());

            // ── 2. Build MIME message ─────────────────────────────────────
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("Your Password Reset OTP — Timesheet Management");
            helper.setText(html, true);

            // ── 3. Send ───────────────────────────────────────────────────
            log.info("[OTP_EMAIL] Sending OTP via SMTP to '{}'...", maskEmail(to));
            mailSender.send(message);
            log.info("[OTP_EMAIL] ✅ OTP email sent successfully to '{}'", maskEmail(to));

        } catch (MessagingException e) {
            log.error("[OTP_EMAIL] ❌ SMTP/messaging error sending to '{}': {}",
                    maskEmail(to), e.getMessage(), e);
        } catch (IllegalStateException e) {
            log.error("[OTP_EMAIL] ❌ Template error for '{}': {}",
                    maskEmail(to), e.getMessage(), e);
        } catch (Exception e) {
            log.error("[OTP_EMAIL] ❌ Unexpected error sending OTP email to '{}': {}",
                    maskEmail(to), e.getClass().getSimpleName() + " — " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GENERIC HELPER — for ad-hoc one-off emails (future use)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Sends a plain HTML email synchronously (caller manages async if needed).
     * Prefer the domain-specific methods above for standard flows.
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        log.info("[EMAIL] Sending '{}' to '{}'", subject, maskEmail(to));
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[EMAIL] ✅ '{}' sent successfully to '{}'", subject, maskEmail(to));
        } catch (MessagingException e) {
            log.error("[EMAIL] ❌ Failed to send '{}' to '{}': {}",
                    subject, maskEmail(to), e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Loads an HTML template from {@code src/main/resources/templates/}.
     *
     * @param templateName file name, e.g. {@code "welcome-email.html"}
     * @return raw HTML string with {@code {{PLACEHOLDER}}} tokens intact
     * @throws IllegalStateException if the file is missing or cannot be read
     */
    private String loadTemplate(String templateName) {
        String path = "templates/" + templateName;
        log.debug("[EMAIL_TEMPLATE] Loading classpath resource: {}", path);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Email template not found on classpath: " + path
                        + " — ensure the file exists in src/main/resources/templates/ "
                        + "and the application has been rebuilt/restarted.");
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("[EMAIL_TEMPLATE] Loaded '{}' ({} bytes)", templateName, content.length());
            return content;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read email template: " + path, e);
        }
    }

    /** Masks an email for safe logging: {@code arpit@example.com} → {@code ar***@example.com} */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        return (local.length() > 2 ? local.substring(0, 2) + "***" : "***")
                + "@" + parts[1];
    }
}
