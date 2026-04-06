package com.timesheetManagement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "username"),
           @UniqueConstraint(columnNames = "email")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Core identity ──────────────────────────────────────────────────────
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    // ── Profile info ───────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Column(length = 100)
    private String location;

    @Column(length = 100)
    private String designation;

    @Column(name = "manager_email", length = 100)
    private String managerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_of_employment", length = 20)
    private TypeOfEmployment typeOfEmployment;

    @Column(name = "photo_path")
    private String photoPath;

    // ── Manager relationship (self-referencing FK) ─────────────────────────
    /**
     * The manager of this user — always a user whose role is ROLE_MANAGER.
     * Persisted as {@code manager_id} FK in the {@code users} table.
     * LAZY-loaded so we never pay an extra join unless explicitly needed.
     *
     * <p>Intentionally kept alongside the legacy {@code manager_email} text
     * column so older code that still reads {@code managerEmail} continues to
     * work without a migration.  New code should always go through this field.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    // ── Roles ──────────────────────────────────────────────────────────────
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // ── Password reset / OTP fields ────────────────────────────────────────

    /** SHA-256 hash of the 6-digit OTP — never store the raw OTP. */
    @Column(name = "reset_otp_hash")
    private String resetOtpHash;

    /** When the OTP stops being valid (issued_at + 10 min). */
    @Column(name = "reset_otp_expires_at")
    private LocalDateTime resetOtpExpiresAt;

    /**
     * Incremented on every wrong OTP attempt; locked at 5.
     * Nullable wrapper type so Hibernate can load NULL from existing rows
     * that were created before these columns were added.
     */
    @Column(name = "reset_otp_attempts", columnDefinition = "INT DEFAULT 0")
    @Builder.Default
    private Integer resetOtpAttempts = 0;

    /**
     * Flipped to true only after a correct OTP is provided.
     * Nullable wrapper type so Hibernate can load NULL from existing rows.
     */
    @Column(name = "reset_otp_verified", columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean resetOtpVerified = Boolean.FALSE;

    /**
     * SHA-256 hash of the short-lived reset token returned after OTP verification.
     * The raw token is sent to the client only once and never persisted.
     */
    @Column(name = "reset_token", length = 128)
    private String resetToken;

    /** When the reset token expires (verified_at + 15 min). */
    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;

    // ── Audit ──────────────────────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
