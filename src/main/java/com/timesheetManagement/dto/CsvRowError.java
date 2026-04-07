package com.timesheetManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes a single failed row from a CSV bulk-import operation.
 *
 * <p>Returned as part of {@link CsvBulkImportResponse#getErrors()} so the
 * caller can identify exactly which rows failed and why.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvRowError {

    /** 1-based row number in the uploaded CSV (excluding the header row). */
    private int row;

    /** The value of the {@code username} column for this row (may be blank). */
    private String username;

    /** The value of the {@code email} column for this row (may be blank). */
    private String email;

    /** Human-readable reason the row was rejected. */
    private String reason;
}

