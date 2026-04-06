package com.timesheetManagement.constants;

import com.timesheetManagement.entity.Gender;
import com.timesheetManagement.entity.RoleName;
import com.timesheetManagement.entity.TypeOfEmployment;

/**
 * Compile-time constants for the seeded default manager account.
 * Change values here — they propagate to {@code DataInitializer} automatically.
 */
public final class DefaultManagerConstants {

    private DefaultManagerConstants() {}

    // ── Credentials ────────────────────────────────────────────────────────
    public static final String           USERNAME     = "manager";
    public static final String           EMAIL        = "manager@gmail.com";
    public static final String           RAW_PASSWORD = "Manager@123";

    // ── Profile ────────────────────────────────────────────────────────────
    public static final String           FIRST_NAME   = "Default";
    public static final String           LAST_NAME    = "Manager";
    public static final Gender           GENDER       = Gender.PREFER_NOT_TO_SAY;
    public static final String           DESIGNATION  = "Team Manager";
    public static final String           LOCATION     = "HQ";
    public static final TypeOfEmployment EMPLOYMENT   = TypeOfEmployment.FULL_TIME;

    // ── Role ───────────────────────────────────────────────────────────────
    public static final RoleName         ROLE         = RoleName.ROLE_MANAGER;
}

