package com.timesheetManagement.service;

import jakarta.annotation.PostConstruct;
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

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @Value("${app.frontend.login-url:http://localhost:5173/login}")
    private String loginUrl;

    // ══════════════════════════════════════════════════════════════════════
    //  STARTUP VALIDATION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Validates the email configuration and templates at application startup.
     * Any error here surfaces immediately in the console — before the first
     * user creation is attempted.
     */
    @PostConstruct
    public void validateEmailSetup() {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║            EMAIL SERVICE — STARTUP CHECK                ║");
        log.info("╠══════════════════════════════════════════════════════════╣");
        log.info("║  SMTP Host   : {}:{}", mailHost, mailPort);
        log.info("║  From        : {}", fromAddress);
        log.info("║  Login URL   : {}", loginUrl);
        log.info("╚══════════════════════════════════════════════════════════╝");

        // Verify both templates are loadable from classpath
        checkTemplate("welcome-email.html");
        checkTemplate("forgot-password-otp.html");
        checkTemplate("timesheet-status.html");
    }

    private void checkTemplate(String name) {
        try {
            String content = loadTemplate(name);
            log.info("[EMAIL_SETUP] ✅ Template '{}' loaded OK ({} bytes)", name, content.length());
        } catch (Exception e) {
            log.error("[EMAIL_SETUP] ❌ Template '{}' FAILED to load — rebuild the project! Error: {}",
                    name, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  WELCOME EMAIL — sent after every new user creation
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Sends a branded welcome email with the new user's login credentials.
     * Runs asynchronously on the emailTaskExecutor thread pool.
     *
     * @param to        recipient email address
     * @param fullName  user's first + last name
     * @param username  assigned login username
     * @param password  plaintext temporary password (before BCrypt encoding)
     * @param role      display-friendly role (e.g. "User", "Manager")
     */
    @Async
    public void sendWelcomeEmail(String to,
                                  String fullName,
                                  String username,
                                  String password,
                                  String role) {

        log.info("[WELCOME_EMAIL] ▶ START — thread='{}', username='{}', to='{}'",
                Thread.currentThread().getName(), username, maskEmail(to));
        try {
            // ── 1. Load & render template ─────────────────────────────────
            log.info("[WELCOME_EMAIL] Step 1/3 — Loading template...");
            String html = loadTemplate("welcome-email.html")
                    .replace("{{FULL_NAME}}", fullName  != null ? fullName  : username)
                    .replace("{{EMAIL}}",     to)
                    .replace("{{USERNAME}}",  username)
                    .replace("{{PASSWORD}}",  password  != null ? password  : "")
                    .replace("{{ROLE}}",      role      != null ? role      : "User")
                    .replace("{{LOGIN_URL}}", loginUrl);
            log.info("[WELCOME_EMAIL] Step 1/3 — Template rendered ({} chars) ✓", html.length());

            // ── 2. Build MIME message ──────────────────────────────────────
            log.info("[WELCOME_EMAIL] Step 2/3 — Building MIME message...");
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("Welcome to Timesheet Management — Your Account Details");
            helper.setText(html, true);
            log.info("[WELCOME_EMAIL] Step 2/3 — MIME message built (from={}, to={}) ✓",
                    fromAddress, maskEmail(to));

            // ── 3. Send via SMTP ───────────────────────────────────────────
            log.info("[WELCOME_EMAIL] Step 3/3 — Connecting to SMTP {}:{}...", mailHost, mailPort);
            mailSender.send(message);
            log.info("[WELCOME_EMAIL] ✅ SUCCESS — Welcome email delivered to '{}'", maskEmail(to));

        } catch (MessagingException e) {
            log.error("[WELCOME_EMAIL] ❌ SMTP ERROR — Could not send to '{}'. " +
                      "Check SMTP host/port/credentials in application.yaml. Error: {}",
                    maskEmail(to), e.getMessage(), e);
        } catch (IllegalStateException e) {
            log.error("[WELCOME_EMAIL] ❌ TEMPLATE ERROR — '{}'. " +
                      "Rebuild the project: mvnw compile. Error: {}",
                    maskEmail(to), e.getMessage(), e);
        } catch (Exception e) {
            log.error("[WELCOME_EMAIL] ❌ UNEXPECTED ERROR — to='{}', type={}, message={}",
                    maskEmail(to), e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  OTP EMAIL — sent during forgot-password flow
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Sends a 6-digit OTP to the user's email for the forgot-password flow.
     */
    @Async
    public void sendOtpEmail(String to, String firstName, String otp, int expiryMinutes) {

        log.info("[OTP_EMAIL] ▶ START — thread='{}', to='{}'",
                Thread.currentThread().getName(), maskEmail(to));
        try {
            log.info("[OTP_EMAIL] Step 1/3 — Loading template...");
            String html = loadTemplate("forgot-password-otp.html")
                    .replace("{{FIRST_NAME}}",     firstName != null ? firstName : "User")
                    .replace("{{OTP}}",             otp)
                    .replace("{{EXPIRY_MINUTES}}", String.valueOf(expiryMinutes));
            log.info("[OTP_EMAIL] Step 1/3 — Template rendered ({} chars) ✓", html.length());

            log.info("[OTP_EMAIL] Step 2/3 — Building MIME message...");
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("Your Password Reset OTP — Timesheet Management");
            helper.setText(html, true);
            log.info("[OTP_EMAIL] Step 2/3 — MIME message built ✓");

            log.info("[OTP_EMAIL] Step 3/3 — Sending via SMTP {}:{}...", mailHost, mailPort);
            mailSender.send(message);
            log.info("[OTP_EMAIL] ✅ SUCCESS — OTP email delivered to '{}'", maskEmail(to));

        } catch (MessagingException e) {
            log.error("[OTP_EMAIL] ❌ SMTP ERROR — to='{}': {}", maskEmail(to), e.getMessage(), e);
        } catch (IllegalStateException e) {
            log.error("[OTP_EMAIL] ❌ TEMPLATE ERROR — to='{}': {}", maskEmail(to), e.getMessage(), e);
        } catch (Exception e) {
            log.error("[OTP_EMAIL] ❌ UNEXPECTED ERROR — to='{}', {}: {}",
                    maskEmail(to), e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TIMESHEET STATUS EMAIL — sent after approve / reject
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Sends a styled HTML notification to the timesheet owner after a manager
     * approves or rejects their submission.
     *
     * <p>Runs asynchronously on the emailTaskExecutor thread pool so the
     * manager's HTTP response is never delayed by SMTP I/O.
     *
     * @param to                 owner's email address
     * @param firstName          owner's first name (for the greeting)
     * @param timesheetTitle     title / display name of the timesheet
     * @param status             {@code "APPROVED"} or {@code "REJECTED"}
     * @param periodRange        human-readable period string, e.g. "Apr 1 – Apr 7, 2026"
     * @param reviewerName       full name of the reviewing manager
     * @param reviewerComment    manager's rejection reason — {@code null} for approvals
     * @param reviewedAt         formatted date-time of the review action
     * @param totalHours         total hours logged in the timesheet (plain string)
     * @param entryRowsHtml      pre-built {@code <tr>} HTML rows for the entries table
     */
    @Async
    public void sendTimesheetStatusEmail(String to,
                                         String firstName,
                                         String timesheetTitle,
                                         String status,
                                         String periodRange,
                                         String reviewerName,
                                         String reviewerComment,
                                         String reviewedAt,
                                         String totalHours,
                                         String entryRowsHtml) {

        log.info("[TS_STATUS_EMAIL] ▶ START — thread='{}', to='{}', status='{}'",
                Thread.currentThread().getName(), maskEmail(to), status);

        boolean isApproved = "APPROVED".equalsIgnoreCase(status);

        // ── Colour scheme based on decision ──────────────────────────────
        String statusColor     = isApproved ? "#16a34a" : "#dc2626";
        String statusBg        = isApproved ? "#f0fdf4" : "#fef2f2";
        String statusBorder    = isApproved ? "#bbf7d0" : "#fecaca";
        String statusHeaderBg  = isApproved ? "#16a34a" : "#dc2626";
        String statusIcon      = isApproved ? "&#10003;" : "&#10007;";
        String statusLower     = isApproved ? "approved" : "rejected";
        String subject         = isApproved
                ? "\u2705 Your Timesheet Has Been Approved \u2014 Timesheet Management"
                : "\u274C Your Timesheet Has Been Rejected \u2014 Timesheet Management";

        // ── Reviewer comment block (rejection only) ───────────────────────
        String commentSection = "";
        if (!isApproved && reviewerComment != null && !reviewerComment.isBlank()) {
            commentSection =
                "<div style=\"background:#fef2f2;border-left:4px solid #dc2626;"
                + "border-radius:0 10px 10px 0;padding:18px 22px;margin-bottom:28px;\">"
                + "<p style=\"margin:0 0 8px;color:#991b1b;font-size:11px;font-weight:800;"
                + "text-transform:uppercase;letter-spacing:1px;\">&#128172;&nbsp; Manager's Comment</p>"
                + "<p style=\"margin:0;color:#374151;font-size:14px;line-height:1.7;\">"
                + reviewerComment
                + "</p></div>";
        }

        // ── No entries fallback ───────────────────────────────────────────
        String rowsHtml = (entryRowsHtml != null && !entryRowsHtml.isBlank())
                ? entryRowsHtml
                : "<tr><td colspan=\"4\" style=\"padding:18px;text-align:center;"
                  + "color:#64748b;font-style:italic;font-size:13px;\">No entries recorded.</td></tr>";

        try {
            log.info("[TS_STATUS_EMAIL] Step 1/3 — Loading template...");
            String html = loadTemplate("timesheet-status.html")
                    .replace("{{FIRST_NAME}}",              firstName    != null ? firstName    : "User")
                    .replace("{{STATUS}}",                   status.toUpperCase())
                    .replace("{{STATUS_LOWER}}",             statusLower)
                    .replace("{{STATUS_COLOR}}",             statusColor)
                    .replace("{{STATUS_BG}}",                statusBg)
                    .replace("{{STATUS_BORDER}}",            statusBorder)
                    .replace("{{STATUS_HEADER_BG}}",         statusHeaderBg)
                    .replace("{{STATUS_ICON}}",              statusIcon)
                    .replace("{{TIMESHEET_TITLE}}",          timesheetTitle != null ? timesheetTitle : "—")
                    .replace("{{PERIOD_RANGE}}",             periodRange    != null ? periodRange    : "—")
                    .replace("{{TOTAL_HOURS}}",              totalHours     != null ? totalHours     : "0")
                    .replace("{{REVIEWER_NAME}}",            reviewerName   != null ? reviewerName   : "—")
                    .replace("{{REVIEWED_AT}}",              reviewedAt     != null ? reviewedAt     : "—")
                    .replace("{{REVIEWER_COMMENT_SECTION}}", commentSection)
                    .replace("{{ENTRIES_TABLE_ROWS}}",       rowsHtml)
                    .replace("{{LOGIN_URL}}",                loginUrl);

            log.info("[TS_STATUS_EMAIL] Step 1/3 — Template rendered ({} chars) ✓", html.length());

            log.info("[TS_STATUS_EMAIL] Step 2/3 — Building MIME message...");
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            log.info("[TS_STATUS_EMAIL] Step 2/3 — MIME message built ✓");

            log.info("[TS_STATUS_EMAIL] Step 3/3 — Sending via SMTP {}:{}...", mailHost, mailPort);
            mailSender.send(message);
            log.info("[TS_STATUS_EMAIL] ✅ SUCCESS — '{}' notification delivered to '{}'",
                    status, maskEmail(to));

        } catch (MessagingException e) {
            log.error("[TS_STATUS_EMAIL] ❌ SMTP ERROR — to='{}': {}", maskEmail(to), e.getMessage(), e);
        } catch (IllegalStateException e) {
            log.error("[TS_STATUS_EMAIL] ❌ TEMPLATE ERROR — to='{}': {}", maskEmail(to), e.getMessage(), e);
        } catch (Exception e) {
            log.error("[TS_STATUS_EMAIL] ❌ UNEXPECTED ERROR — to='{}', {}: {}",
                    maskEmail(to), e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GENERIC HELPER
    // ══════════════════════════════════════════════════════════════════════

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

    private String loadTemplate(String templateName) {
        String path = "templates/" + templateName;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Template not found on classpath: " + path
                        + " — rebuild with: mvnw compile");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read template: " + path, e);
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        return (local.length() > 2 ? local.substring(0, 2) + "***" : "***")
                + "@" + parts[1];
    }
}
