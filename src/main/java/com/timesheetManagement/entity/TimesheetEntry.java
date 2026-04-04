package com.timesheetManagement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single daily time log within a {@link Timesheet}.
 *
 * <p>Unique constraint prevents logging the same project on the same date
 * twice within the same timesheet.
 */
@Entity
@Table(
    name = "timesheet_entries",
    uniqueConstraints = @UniqueConstraint(
        name        = "uq_ts_project_date",
        columnNames = {"timesheet_id", "project_id", "work_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimesheetEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Parent timesheet ───────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timesheet_id", nullable = false)
    private Timesheet timesheet;

    // ── Project ────────────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** The date on which the work was performed. */
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /**
     * Total hours worked (decimal). E.g. 2.5 = 2 h 30 min.
     * Min 0.5 h, max 24 h — validated in the service layer.
     */
    @Column(name = "hours_worked", nullable = false, precision = 4, scale = 2)
    private BigDecimal hoursWorked;

    @Column(length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
