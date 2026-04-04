package com.timesheetManagement.exception;

import com.timesheetManagement.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ══════════════════════════════════════════════════════════════════════
    //  4xx  CLIENT ERRORS
    // ══════════════════════════════════════════════════════════════════════

    // ── 400 · Validation (@Valid on @RequestBody) ─────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(field, message);
        });

        log.warn("[VALIDATION] {} field(s) failed on {} {}: {}",
                fieldErrors.size(), req.getMethod(), req.getRequestURI(), fieldErrors);

        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, fieldErrors);
    }

    // ── 400 · Malformed JSON / unrecognised enum value ────────────────────
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest req) {

        String msg = "Malformed JSON or invalid value. " + rootMessage(ex);
        log.warn("[BAD_REQUEST] {} {} → {}", req.getMethod(), req.getRequestURI(), msg);
        return build(HttpStatus.BAD_REQUEST, msg, req, null);
    }

    // ── 400 · Wrong path-variable type (e.g. /api/user/abc instead of Long) ─
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {

        String msg = String.format("Parameter '%s' should be of type '%s'",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        log.warn("[TYPE_MISMATCH] {} {} → {}", req.getMethod(), req.getRequestURI(), msg);
        return build(HttpStatus.BAD_REQUEST, msg, req, null);
    }

    // ── 400 · Missing required query/path param ───────────────────────────
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {

        String msg = "Required parameter '" + ex.getParameterName() + "' is missing";
        log.warn("[MISSING_PARAM] {} {} → {}", req.getMethod(), req.getRequestURI(), msg);
        return build(HttpStatus.BAD_REQUEST, msg, req, null);
    }

    // ── 400 · File upload validation (size, type) ─────────────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {

        log.warn("[INVALID_ARGUMENT] {} {} → {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    // ── 401 · Bad credentials ─────────────────────────────────────────────
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest req) {

        log.warn("[AUTH_FAILED] Login attempt failed for request {} {}", req.getMethod(), req.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, "Invalid username/email or password", req, null);
    }

    // ── 401 · Account disabled ────────────────────────────────────────────
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(
            DisabledException ex, HttpServletRequest req) {

        log.warn("[ACCOUNT_DISABLED] {} {}", req.getMethod(), req.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, "Account is disabled. Contact support.", req, null);
    }

    // ── 401 · Account locked ──────────────────────────────────────────────
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(
            LockedException ex, HttpServletRequest req) {

        log.warn("[ACCOUNT_LOCKED] {} {}", req.getMethod(), req.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, "Account is locked. Contact support.", req, null);
    }

    // ── 403 · JWT invalid / expired ───────────────────────────────────────
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(
            InvalidTokenException ex, HttpServletRequest req) {

        log.warn("[INVALID_TOKEN] {} {} → {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), req, null);
    }

    // ── 403 · Insufficient role ───────────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {

        log.warn("[ACCESS_DENIED] {} {} — insufficient permissions", req.getMethod(), req.getRequestURI());
        return build(HttpStatus.FORBIDDEN, "Access denied: insufficient permissions", req, null);
    }

    // ── 404 · Resource not found ──────────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {

        log.warn("[NOT_FOUND] {} {} → {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    // ── 404 · No route found ──────────────────────────────────────────────
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(
            NoHandlerFoundException ex, HttpServletRequest req) {

        String msg = "No endpoint found for " + ex.getHttpMethod() + " " + ex.getRequestURL();
        log.warn("[NO_ROUTE] {}", msg);
        return build(HttpStatus.NOT_FOUND, msg, req, null);
    }

    // ── 404 · Static / uploaded file not found ────────────────────────────
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest req) {

        String resourcePath = ex.getResourcePath();
        String msg = resourcePath.contains("profile-photos")
                ? "Profile photo not found. The file may have been deleted or the URL is incorrect: /" + resourcePath
                : "The requested resource was not found: /" + resourcePath;

        log.warn("[RESOURCE_NOT_FOUND] {} {} → {}", req.getMethod(), req.getRequestURI(), msg);
        return build(HttpStatus.NOT_FOUND, msg, req, null);
    }

    // ── 405 · Wrong HTTP method ───────────────────────────────────────────
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {

        String msg = "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint";
        log.warn("[METHOD_NOT_ALLOWED] {} {}", req.getMethod(), req.getRequestURI());
        return build(HttpStatus.METHOD_NOT_ALLOWED, msg, req, null);
    }

    // ── 409 · Duplicate user ──────────────────────────────────────────────
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            UserAlreadyExistsException ex, HttpServletRequest req) {

        log.warn("[DUPLICATE_USER] {} {} → {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    // ── 409 · Duplicate timesheet (same user + week) ──────────────────────
    @ExceptionHandler(DuplicateTimesheetException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateTimesheet(
            DuplicateTimesheetException ex, HttpServletRequest req) {

        log.warn("[DUPLICATE_TIMESHEET] {} {} → {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    // ── 409 · Duplicate timesheet entry (same project + date in timesheet) ─
    @ExceptionHandler(DuplicateEntryException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEntry(
            DuplicateEntryException ex, HttpServletRequest req) {

        log.warn("[DUPLICATE_ENTRY] {} {} → {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    // ── 409 · Invalid timesheet state transition ───────────────────────────
    @ExceptionHandler(InvalidTimesheetStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTimesheetState(
            InvalidTimesheetStateException ex, HttpServletRequest req) {

        log.warn("[INVALID_TIMESHEET_STATE] {} {} → {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    // ── 403 · Business-level forbidden operation ───────────────────────────
    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenOperation(
            ForbiddenOperationException ex, HttpServletRequest req) {

        log.warn("[FORBIDDEN_OPERATION] {} {} → {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), req, null);
    }

    // ── 409 · DB unique constraint (fallback) ─────────────────────────────
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest req) {

        log.error("[DB_CONSTRAINT] {} {} → {}", req.getMethod(), req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT,
                "Data integrity violation — a record with the same unique value already exists", req, null);
    }

    // ── 413 · File too large ──────────────────────────────────────────────
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize(
            MaxUploadSizeExceededException ex, HttpServletRequest req) {

        log.warn("[FILE_TOO_LARGE] {} {} → maxSize={}", req.getMethod(), req.getRequestURI(), ex.getMaxUploadSize());
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "File size exceeds the maximum allowed limit", req, null);
    }

    // ── 400 · Generic multipart / file errors ─────────────────────────────
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(
            MultipartException ex, HttpServletRequest req) {

        log.warn("[MULTIPART_ERROR] {} {} → {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "File upload failed: " + rootMessage(ex), req, null);
    }

    // ── 500 · File upload I/O failure ─────────────────────────────────────
    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ErrorResponse> handleFileUpload(
            FileUploadException ex, HttpServletRequest req) {

        log.error("[FILE_UPLOAD_ERROR] {} {} → {}", req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), req, null);
    }

    // ── 500 · Role seeding / config error ─────────────────────────────────
    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoleNotFound(
            RoleNotFoundException ex, HttpServletRequest req) {

        log.error("[ROLE_ERROR] {} {} → {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), req, null);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DATABASE / JPA ERRORS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Handles SQL grammar errors — most commonly caused by PostgreSQL being
     * unable to infer parameter types in JPQL "? IS NULL OR col = ?" patterns.
     *
     * Root cause: PSQLException "could not determine data type of parameter $N"
     * Fix applied: TimesheetSpecification (Criteria API) removes null parameters
     * entirely. This handler is a safety net for any residual raw JPQL queries.
     */
    @ExceptionHandler(BadSqlGrammarException.class)
    public ResponseEntity<ErrorResponse> handleBadSqlGrammar(
            BadSqlGrammarException ex, HttpServletRequest req) {

        String errorId = UUID.randomUUID().toString();
        log.error("[SQL_GRAMMAR_ERROR] errorId={} | {} {} → SQL={} | cause={}",
                errorId, req.getMethod(), req.getRequestURI(),
                ex.getSql(), rootMessage(ex));

        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "A database query error occurred. "
                + "Please verify your filter parameters are valid. "
                + "Reference errorId: " + errorId,
                req, null);
    }

    /**
     * Handles invalid JPA/JPQL API usage — e.g. sorting on a non-existent field
     * name, or using an invalid Specification expression.
     */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDataAccessApiUsage(
            InvalidDataAccessApiUsageException ex, HttpServletRequest req) {

        log.warn("[INVALID_JPA_USAGE] {} {} → {}",
                req.getMethod(), req.getRequestURI(), rootMessage(ex));
        return build(HttpStatus.BAD_REQUEST,
                "Invalid query parameter. Please check your filter or sort fields and try again.",
                req, null);
    }

    /**
     * Handles low-level JPA/Hibernate system failures not covered by more
     * specific handlers above (e.g. connection timeout, dialect mismatch).
     */
    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<ErrorResponse> handleJpaSystem(
            JpaSystemException ex, HttpServletRequest req) {

        String errorId = UUID.randomUUID().toString();
        log.error("[JPA_SYSTEM_ERROR] errorId={} | {} {} → {}",
                errorId, req.getMethod(), req.getRequestURI(), rootMessage(ex), ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "A database system error occurred. Reference errorId: " + errorId,
                req, null);
    }

    /**
     * Broad catch-all for any remaining Spring DataAccessException subtypes
     * (e.g. QueryTimeoutException, TransientDataAccessException).
     * Must be declared AFTER all specific DataAccessException subclass handlers
     * (DataIntegrityViolationException, BadSqlGrammarException, etc.).
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(
            DataAccessException ex, HttpServletRequest req) {

        String errorId = UUID.randomUUID().toString();
        log.error("[DATA_ACCESS_ERROR] errorId={} | {} {} → {}",
                errorId, req.getMethod(), req.getRequestURI(), rootMessage(ex), ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "A database error occurred. Reference errorId: " + errorId,
                req, null);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  5xx  SERVER ERRORS  (catch-all — must be LAST)
    // ══════════════════════════════════════════════════════════════════════

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest req) {

        String errorId = UUID.randomUUID().toString();
        log.error("[UNHANDLED_ERROR] errorId={} | {} {} | exception={}",
                errorId, req.getMethod(), req.getRequestURI(), ex.getClass().getSimpleName(), ex);

        ErrorResponse body = ErrorResponse.builder()
                .errorId(errorId)
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("An unexpected error occurred. Reference errorId: " + errorId)
                .path(req.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Builder helper
    // ══════════════════════════════════════════════════════════════════════

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest req, Object details) {

        ErrorResponse body = ErrorResponse.builder()
                .errorId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(req.getRequestURI())
                .details(details)
                .build();

        return ResponseEntity.status(status).body(body);
    }

    private String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() != null ? root.getMessage() : ex.getMessage();
    }
}

