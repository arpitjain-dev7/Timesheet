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

import java.time.LocalDate;
import java.util.Optional;

public interface TimesheetRepository
        extends JpaRepository<Timesheet, Long>, JpaSpecificationExecutor<Timesheet> {

    /** All timesheets for a given user — for "my timesheets" listing. */
    Page<Timesheet> findAllByUser(User user, Pageable pageable);

    /** Admin/manager list — optionally filtered by status. */
    Page<Timesheet> findAllByStatus(TimesheetStatus status, Pageable pageable);

    /** Eagerly load entries + project to avoid N+1 on detail view. */
    @Query("""
           SELECT DISTINCT t FROM Timesheet t
           LEFT JOIN FETCH t.entries e
           LEFT JOIN FETCH e.project
           WHERE t.id = :id
           """)
    Optional<Timesheet> findByIdWithEntries(@Param("id") Long id);

    /**
     * Manager filter: supports optional project, user, date-range, and status filters.
     * Uses subqueries on entries so that date/project filters are entry-level.
     */
    @Query("""
           SELECT DISTINCT t FROM Timesheet t
           LEFT JOIN t.entries e
           WHERE (:userId   IS NULL OR t.user.id  = :userId)
           AND   (:status   IS NULL OR t.status   = :status)
           AND   (:projectId IS NULL OR e.project.id = :projectId)
           AND   (:startDate IS NULL OR e.workDate >= :startDate)
           AND   (:endDate   IS NULL OR e.workDate <= :endDate)
           """)
    Page<Timesheet> filterTimesheets(
            @Param("userId")    Long userId,
            @Param("status")    TimesheetStatus status,
            @Param("projectId") Long projectId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate,
            Pageable pageable);

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
