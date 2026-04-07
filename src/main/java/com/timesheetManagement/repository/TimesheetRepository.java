package com.timesheetManagement.repository;

import com.timesheetManagement.entity.Timesheet;
import com.timesheetManagement.entity.TimesheetStatus;
import com.timesheetManagement.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TimesheetRepository
        extends JpaRepository<Timesheet, Long>, JpaSpecificationExecutor<Timesheet> {

    /** All timesheets for a given user — for "my timesheets" listing. */
    Page<Timesheet> findAllByUser(User user, Pageable pageable);

    /** Admin/manager list — optionally filtered by status. */
    Page<Timesheet> findAllByStatus(TimesheetStatus status, Pageable pageable);

    /**
     * Eagerly loads the timesheet with its owner, the owner's manager, and all
     * time entries (+ their projects) in a single query.
     *
     * <p>The manager JOIN FETCH is required so the scope guard in
     * {@code TimesheetReviewService} can call {@code ts.getUser().getManager()}
     * without triggering additional lazy-load round-trips.
     */
    @Query("""
           SELECT DISTINCT t FROM Timesheet t
           LEFT JOIN FETCH t.user u
           LEFT JOIN FETCH u.manager
           LEFT JOIN FETCH t.entries e
           LEFT JOIN FETCH e.project
           WHERE t.id = :id
           """)
    Optional<Timesheet> findByIdWithEntries(@Param("id") Long id);

    /*
     * filterTimesheets() has been removed.
     *
     * The previous JPQL query used "(:startDate IS NULL OR e.workDate >= :startDate)"
     * which causes a PostgreSQL error: "could not determine data type of parameter $N"
     * because PostgreSQL cannot infer the type of a NULL literal in a prepared statement.
     *
     * Replaced by TimesheetSpecification (JPA Criteria API) used via the inherited
     * JpaSpecificationExecutor.findAll(Specification, Pageable) method.
     * Criteria predicates are only added for non-null values, so no untyped NULLs
     * are ever sent to PostgreSQL.
     */

    /**
     * Before deleting a user: delete all timesheets owned by the user.
     * Cascade ALL on entries means timesheet_entries are also deleted automatically.
     */
    void deleteAllByUser(User user);

    /**
     * Before deleting a user: null-out the reviewer reference so approved/rejected
     * timesheets are retained but no longer reference the deleted user.
     */
    @Modifying
    @Query("UPDATE Timesheet t SET t.reviewedBy = null WHERE t.reviewedBy = :user")
    void clearReviewedBy(@Param("user") User user);
}
