package com.timesheetManagement.repository;

import com.timesheetManagement.entity.TimesheetEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, Long> {

    List<TimesheetEntry> findAllByTimesheetId(Long timesheetId);

    /** Used to bulk-delete entries when a DRAFT timesheet is deleted. */
    void deleteAllByTimesheetId(Long timesheetId);

    /**
     * Sum of hours already logged by a user on a specific date across
     * all their DRAFT and SUBMITTED timesheets.
     * Used to enforce the 24 h/day cap before adding a new entry.
     */
    @Query("""
           SELECT COALESCE(SUM(e.hoursWorked), 0)
           FROM TimesheetEntry e
           WHERE e.timesheet.user.id = :userId
           AND   e.workDate           = :workDate
           AND   e.timesheet.status  IN ('DRAFT', 'SUBMITTED')
           """)
    BigDecimal sumHoursByUserAndDate(
            @Param("userId")   Long userId,
            @Param("workDate") LocalDate workDate);

    /** Check for duplicate (timesheet + project + date) without relying on the DB exception. */
    boolean existsByTimesheetIdAndProjectIdAndWorkDate(
            Long timesheetId, Long projectId, LocalDate workDate);
}
