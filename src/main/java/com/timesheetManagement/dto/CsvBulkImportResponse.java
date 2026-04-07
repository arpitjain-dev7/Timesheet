package com.timesheetManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Summary response returned by {@code POST /api/users/import/csv}.
 *
 * <p>Always returns HTTP 200 even when some rows fail, so the caller can
 * inspect the full breakdown.  Individual row errors are listed in
 * {@link #getErrors()}; successfully created users are in
 * {@link #getCreatedUsers()}.
 *
 * <p>Example:
 * <pre>{@code
 * {
 *   "totalRows"    : 5,
 *   "successCount" : 4,
 *   "failureCount" : 1,
 *   "createdUsers" : [ {...}, {...} ],
 *   "errors": [
 *     { "row": 3, "username": "bob", "email": "bob@x.com",
 *       "reason": "Email 'bob@x.com' is already registered" }
 *   ]
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvBulkImportResponse {

    /** Total data rows parsed (excludes the header row). */
    private int totalRows;

    /** Number of rows that resulted in a successfully created user. */
    private int successCount;

    /** Number of rows that were skipped due to validation or business-rule errors. */
    private int failureCount;

    /**
     * Details of every user that was successfully created.
     * A welcome email is dispatched asynchronously for each entry.
     */
    private List<UserResponseDTO> createdUsers;

    /**
     * Per-row error details for every row that could not be processed.
     * Empty when all rows succeed.
     */
    private List<CsvRowError> errors;
}

