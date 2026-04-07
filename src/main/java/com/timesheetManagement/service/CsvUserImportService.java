package com.timesheetManagement.service;

import com.timesheetManagement.dto.CreateUserRequest;
import com.timesheetManagement.dto.CsvBulkImportResponse;
import com.timesheetManagement.dto.CsvRowError;
import com.timesheetManagement.dto.UserResponseDTO;
import com.timesheetManagement.entity.Gender;
import com.timesheetManagement.entity.RoleName;
import com.timesheetManagement.entity.TypeOfEmployment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Parses an uploaded CSV file and creates one user account per data row,
 * delegating to {@link UserService#createUserWithManager(CreateUserRequest)}
 * for all validation, persistence, and welcome-email dispatch.
 *
 * <h3>Expected CSV headers (case-insensitive, order does not matter):</h3>
 * <pre>
 * firstName | lastName | username | email | password | gender | location
 *           | designation | typeOfEmployment | role | managerId
 * </pre>
 *
 * <h3>Design decisions:</h3>
 * <ul>
 *   <li>Each row is processed in its own transaction (no cross-row rollback).</li>
 *   <li>A welcome email is fired asynchronously for every successful row
 *       (handled inside {@code UserService.createUserWithManager}).</li>
 *   <li>Failures on individual rows are captured in {@link CsvRowError} objects
 *       and returned in the summary — they do not abort the rest of the import.</li>
 *   <li>The entire file is rejected early if it is empty, exceeds 500 rows, or
 *       is missing any required header column.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvUserImportService {

    /** Maximum number of data rows permitted in a single upload. */
    private static final int MAX_ROWS = 500;

    /** Required column headers (case-insensitive). */
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "firstname", "lastname", "username", "email", "password"
    );

    private final UserService userService;

    // ══════════════════════════════════════════════════════════════════════
    //  PUBLIC ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Processes the uploaded CSV file and returns a bulk-import summary.
     *
     * <p>HTTP 200 is always returned (the controller does not throw for
     * partial failures); check {@code failureCount} and {@code errors} in
     * the response body to inspect individual row problems.
     *
     * @param file the multipart CSV upload (max {@value MAX_ROWS} data rows)
     * @return a {@link CsvBulkImportResponse} with counts and per-row details
     * @throws IllegalArgumentException if the file is null, empty, not a CSV,
     *                                  or missing required header columns
     */
    public CsvBulkImportResponse importUsers(MultipartFile file) {

        log.info("[CSV_IMPORT] ▶ START — file='{}', size={} bytes",
                file.getOriginalFilename(), file.getSize());

        // ── Pre-flight validation ─────────────────────────────────────────
        validateFile(file);

        List<UserResponseDTO> created = new ArrayList<>();
        List<CsvRowError>     errors  = new ArrayList<>();

        // ── Parse and process ─────────────────────────────────────────────
        try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .setIgnoreEmptyLines(true)
                     .build()
                     .parse(reader)) {

            // ── Validate required headers are present ─────────────────────
            validateHeaders(parser.getHeaderMap().keySet()
                    .stream()
                    .map(String::toLowerCase)
                    .collect(java.util.stream.Collectors.toSet()));

            List<CSVRecord> records = parser.getRecords();
            int totalRows = records.size();

            log.info("[CSV_IMPORT] Parsed {} data row(s) from '{}'",
                    totalRows, file.getOriginalFilename());

            if (totalRows == 0) {
                log.warn("[CSV_IMPORT] File '{}' contains no data rows", file.getOriginalFilename());
                return CsvBulkImportResponse.builder()
                        .totalRows(0)
                        .successCount(0)
                        .failureCount(0)
                        .createdUsers(List.of())
                        .errors(List.of())
                        .build();
            }

            if (totalRows > MAX_ROWS) {
                log.warn("[CSV_IMPORT] File exceeds max row limit: {} > {}", totalRows, MAX_ROWS);
                throw new IllegalArgumentException(
                        "CSV file contains " + totalRows + " rows. Maximum allowed is " + MAX_ROWS + ".");
            }

            // ── Row-by-row processing ─────────────────────────────────────
            for (int i = 0; i < records.size(); i++) {
                CSVRecord record   = records.get(i);
                int       rowNum   = i + 1;   // 1-based, excludes header

                String username = safeGet(record, "username");
                String email    = safeGet(record, "email");

                try {
                    CreateUserRequest req = mapRowToRequest(record, rowNum);
                    UserResponseDTO   dto = userService.createUserWithManager(req);
                    created.add(dto);

                    log.info("[CSV_IMPORT] ✅ Row {} — user created: id={}, username='{}', email='{}'",
                            rowNum, dto.getId(), dto.getUsername(), maskEmail(dto.getEmail()));

                } catch (Exception ex) {
                    // Capture per-row failures — never abort the whole import
                    String reason = friendlyMessage(ex);
                    log.warn("[CSV_IMPORT] ❌ Row {} — skipped (username='{}', email='{}'): {}",
                            rowNum, username, email, reason);
                    errors.add(CsvRowError.builder()
                            .row(rowNum)
                            .username(username)
                            .email(email)
                            .reason(reason)
                            .build());
                }
            }

        } catch (IllegalArgumentException ex) {
            // Re-throw pre-flight / header validation errors as-is
            throw ex;
        } catch (Exception ex) {
            log.error("[CSV_IMPORT] ❌ Failed to parse CSV file '{}': {}",
                    file.getOriginalFilename(), ex.getMessage(), ex);
            throw new IllegalArgumentException(
                    "Failed to parse CSV file: " + ex.getMessage(), ex);
        }

        log.info("[CSV_IMPORT] ✅ DONE — total={}, success={}, failed={}",
                created.size() + errors.size(), created.size(), errors.size());

        return CsvBulkImportResponse.builder()
                .totalRows(created.size() + errors.size())
                .successCount(created.size())
                .failureCount(errors.size())
                .createdUsers(created)
                .errors(errors)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Private helpers
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Maps a single {@link CSVRecord} to a {@link CreateUserRequest}, applying
     * lightweight type coercion.  Blank optional fields are left {@code null}.
     *
     * @throws IllegalArgumentException if a required field is blank or an
     *                                  enum value is unrecognised
     */
    private CreateUserRequest mapRowToRequest(CSVRecord record, int rowNum) {

        // ── Required fields ───────────────────────────────────────────────
        String firstName = requireNonBlank(record, "firstName", rowNum);
        String lastName  = requireNonBlank(record, "lastName",  rowNum);
        String username  = requireNonBlank(record, "username",  rowNum);
        String email     = requireNonBlank(record, "email",     rowNum);
        String password  = requireNonBlank(record, "password",  rowNum);

        // ── Optional fields ───────────────────────────────────────────────
        String location        = blankToNull(safeGet(record, "location"));
        String designation     = blankToNull(safeGet(record, "designation"));
        String genderStr       = blankToNull(safeGet(record, "gender"));
        String employmentStr   = blankToNull(safeGet(record, "typeOfEmployment"));
        String roleStr         = blankToNull(safeGet(record, "role"));
        String managerIdStr    = blankToNull(safeGet(record, "managerId"));

        // ── Enum coercion ─────────────────────────────────────────────────
        Gender          gender     = parseEnum(Gender.class,         genderStr,     rowNum, "gender");
        TypeOfEmployment employment = parseEnum(TypeOfEmployment.class, employmentStr, rowNum, "typeOfEmployment");
        RoleName         role       = parseEnum(RoleName.class,        roleStr,       rowNum, "role");

        // ── Manager ID ────────────────────────────────────────────────────
        Long managerId = null;
        if (managerIdStr != null) {
            try {
                managerId = Long.parseLong(managerIdStr.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Row " + rowNum + ": 'managerId' must be a numeric value, got: '" + managerIdStr + "'");
            }
        }

        CreateUserRequest req = new CreateUserRequest();
        req.setFirstName(firstName);
        req.setLastName(lastName);
        req.setUsername(username);
        req.setEmail(email);
        req.setPassword(password);
        req.setGender(gender);
        req.setLocation(location);
        req.setDesignation(designation);
        req.setTypeOfEmployment(employment);
        req.setRole(role);
        req.setManagerId(managerId);
        return req;
    }

    /** Reads a column value; returns empty string when the column does not exist. */
    private String safeGet(CSVRecord record, String column) {
        try {
            return record.isMapped(column) ? record.get(column) : "";
        } catch (Exception ex) {
            return "";
        }
    }

    /** Returns {@code null} when the trimmed value is blank. */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Returns the trimmed value or throws {@link IllegalArgumentException}
     * when blank.
     */
    private String requireNonBlank(CSVRecord record, String column, int rowNum) {
        String value = safeGet(record, column).trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Row " + rowNum + ": Required field '" + column + "' is missing or blank.");
        }
        return value;
    }

    /**
     * Parses an enum value case-insensitively.  Returns {@code null} when
     * the raw value is {@code null} (optional field not provided).
     *
     * @throws IllegalArgumentException on unrecognised value
     */
    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, int rowNum, String column) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Row " + rowNum + ": Invalid value '" + value + "' for field '" + column
                    + "'. Allowed: " + Arrays.toString(type.getEnumConstants()));
        }
    }

    /** Validates the uploaded file before any parsing starts. */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file must not be empty.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException(
                    "Invalid file type. Only '.csv' files are accepted. Got: '"
                    + filename + "'");
        }

        // Reject suspiciously small files (less than 10 bytes = header only or garbage)
        if (file.getSize() < 10) {
            throw new IllegalArgumentException(
                    "CSV file is too small (" + file.getSize() + " bytes). It must contain at least a header row.");
        }

        log.debug("[CSV_IMPORT] File validation passed: name='{}', size={} bytes", filename, file.getSize());
    }

    /** Ensures all required header columns are present in the parsed CSV. */
    private void validateHeaders(Set<String> actualHeaders) {
        List<String> missing = REQUIRED_HEADERS.stream()
                .filter(h -> !actualHeaders.contains(h))
                .sorted()
                .toList();

        if (!missing.isEmpty()) {
            log.warn("[CSV_IMPORT] Missing required headers: {}", missing);
            throw new IllegalArgumentException(
                    "CSV is missing required column(s): " + missing
                    + ". Required headers: firstName, lastName, username, email, password");
        }
    }

    /**
     * Extracts a human-readable message from any exception that escapes
     * row processing, suppressing implementation details for business errors.
     */
    private String friendlyMessage(Exception ex) {
        // Business exceptions already have clean messages; use them directly
        if (ex instanceof com.timesheetManagement.exception.UserAlreadyExistsException
                || ex instanceof com.timesheetManagement.exception.InvalidManagerAssignmentException
                || ex instanceof com.timesheetManagement.exception.ManagerNotFoundException
                || ex instanceof com.timesheetManagement.exception.RoleNotFoundException
                || ex instanceof IllegalArgumentException) {
            return ex.getMessage();
        }
        // For unexpected technical exceptions, log the full stack trace (already done
        // at WARN level by the caller) and return a safe generic message
        return "Unexpected error: " + ex.getClass().getSimpleName() + " — " + ex.getMessage();
    }

    /** Masks an email address for safe logging. */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        return (local.length() > 2 ? local.substring(0, 2) + "***" : "***") + "@" + parts[1];
    }
}

