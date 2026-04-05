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
 * Reusable email utility service.
 *
 * <p>Sends HTML emails via JavaMailSender. Email sending is performed
 * asynchronously so that a slow SMTP server never blocks an HTTP request.
 * SMTP credentials are externalised in {@code application.yaml} and should
 * be overridden via environment variables in production.
 *
 * <p>Templates are loaded from {@code src/main/resources/templates/} and
 * use simple {@code {{PLACEHOLDER}}} substitution — no template engine needed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    // ── Generic send ──────────────────────────────────────────────────────

    /**
     * Sends an HTML email asynchronously.
     *
     * @param to          recipient email address
     * @param subject     email subject line
     * @param htmlContent fully-rendered HTML body
     */
    @Async("emailTaskExecutor")
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[EMAIL] ✅ '{}' sent to '{}'", subject, maskEmail(to));
        } catch (MessagingException e) {
            // Log and swallow — email failure must not break the API response.
            // In production, push to a retry queue (SQS, Kafka, etc.).
            log.error("[EMAIL] ❌ Failed to send '{}' to '{}': {}",
                    subject, maskEmail(to), e.getMessage(), e);
        }
    }

    // ── Domain-specific senders ───────────────────────────────────────────

    /**
     * Sends the OTP email for the forgot-password flow.
     *
     * @param to              recipient email
     * @param firstName       used to personalise the greeting
     * @param otp             plaintext 6-digit OTP (hashed before this call)
     * @param expiryMinutes   validity window shown in the email
     */
    public void sendOtpEmail(String to, String firstName, String otp, int expiryMinutes) {
        String template = loadTemplate("forgot-password-otp.html");
        String html = template
                .replace("{{FIRST_NAME}}", firstName != null ? firstName : "User")
                .replace("{{OTP}}", otp)
                .replace("{{EXPIRY_MINUTES}}", String.valueOf(expiryMinutes));

        sendHtmlEmail(to, "Your Password Reset OTP — Timesheet Management", html);
    }

    // ── Template loader ───────────────────────────────────────────────────

    /**
     * Loads an HTML template from {@code src/main/resources/templates/}.
     *
     * @param templateName filename (e.g. {@code "forgot-password-otp.html"})
     * @return raw HTML string with placeholders intact
     * @throws IllegalStateException if the template file cannot be found
     */
    private String loadTemplate(String templateName) {
        String path = "templates/" + templateName;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Email template not found on classpath: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read email template: " + path, e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        return (local.length() > 2 ? local.substring(0, 2) + "***" : "***")
                + "@" + parts[1];
    }
}


