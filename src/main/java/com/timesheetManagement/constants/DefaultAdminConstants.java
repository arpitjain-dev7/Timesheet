package com.timesheetManagement.constants;

import com.timesheetManagement.entity.Gender;
import com.timesheetManagement.entity.RoleName;
import com.timesheetManagement.entity.TypeOfEmployment;

/**
 * Compile-time constants for the seeded default admin account.
 * Change these values here — they propagate everywhere automatically.
 */
public final class DefaultAdminConstants {

    // ── Prevent instantiation ──────────────────────────────────────────────
    private DefaultAdminConstants() {}

    // ── Credentials ────────────────────────────────────────────────────────
    public static final String    USERNAME          = "admin";
    public static final String    EMAIL             = "admin@gmail.com";
    public static final String    RAW_PASSWORD      = "admin@123";

    // ── Profile ────────────────────────────────────────────────────────────
    public static final String    FIRST_NAME        = "System";
    public static final String    LAST_NAME         = "Admin";
    public static final Gender    GENDER            = Gender.PREFER_NOT_TO_SAY;
    public static final String    DESIGNATION       = "System Administrator";
    public static final String    LOCATION          = "HQ";
    public static final TypeOfEmployment EMPLOYMENT = TypeOfEmployment.FULL_TIME;

    // ── Role ───────────────────────────────────────────────────────────────
    public static final RoleName  ROLE              = RoleName.ROLE_ADMIN;
}

