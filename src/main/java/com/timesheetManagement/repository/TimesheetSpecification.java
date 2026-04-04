package com.timesheetManagement.repository;

import com.timesheetManagement.entity.Timesheet;
import com.timesheetManagement.entity.TimesheetEntry;
import com.timesheetManagement.entity.TimesheetStatus;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification for filtering {@link Timesheet} records.
 *
 * <p>Replaces the JPQL "(:param IS NULL OR col = :param)" pattern which causes
 * a PostgreSQL "could not determine data type of parameter $N" error when null
 * values are passed, because PostgreSQL cannot infer the type of an untyped NULL
 * in a prepared statement.
 *
 * <p>With Specifications, predicates are only added when the value is non-null,
 * so PostgreSQL never receives an untyped null parameter.
 */
public class TimesheetSpecification {

    private TimesheetSpecification() {}

    /**
     * Build a dynamic filter Specification. Any null argument is simply ignored
     * (no predicate added), so all parameters are truly optional.
     *
     * @param userId    filter by timesheet owner ID
     * @param status    filter by timesheet status
     * @param projectId filter by project ID on any entry
     * @param startDate entries on or after this date
     * @param endDate   entries on or before this date
     */
    public static Specification<Timesheet> filter(
            Long userId,
            TimesheetStatus status,
            Long projectId,
            LocalDate startDate,
            LocalDate endDate) {

        return (Root<Timesheet> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            // ── Owner filter ──────────────────────────────────────────────
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }

            // ── Status filter ─────────────────────────────────────────────
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // ── Entry-level filters (project / date range) ────────────────
            // Only join timesheet_entries when at least one entry-level filter
            // is present — avoids an unnecessary JOIN that can widen result rows.
            if (projectId != null || startDate != null || endDate != null) {
                Join<Timesheet, TimesheetEntry> entries =
                        root.join("entries", JoinType.LEFT);

                if (projectId != null) {
                    predicates.add(cb.equal(entries.get("project").get("id"), projectId));
                }
                if (startDate != null) {
                    // startDate is a real LocalDate — PostgreSQL knows the type
                    predicates.add(cb.greaterThanOrEqualTo(entries.get("workDate"), startDate));
                }
                if (endDate != null) {
                    predicates.add(cb.lessThanOrEqualTo(entries.get("workDate"), endDate));
                }
            }

            // Deduplicate rows caused by the JOIN (same as DISTINCT in JPQL)
            query.distinct(true);

            // Default sort: newest first (override-able via Pageable Sort)
            if (query.getOrderList().isEmpty()) {
                query.orderBy(cb.desc(root.get("createdAt")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

