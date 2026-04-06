package com.timesheetManagement.exception;

/**
 * Thrown when a {@code managerId} supplied in a request does not match
 * any existing user record in the database.
 *
 * <p>Mapped to HTTP 404 Not Found by {@link GlobalExceptionHandler}.
 */
public class ManagerNotFoundException extends RuntimeException {

    public ManagerNotFoundException(Long managerId) {
        super("Manager with id " + managerId + " not found");
    }
}

