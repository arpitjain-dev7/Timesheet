package com.timesheetManagement.repository;

import com.timesheetManagement.entity.Project;
import com.timesheetManagement.entity.ProjectAssignment;
import com.timesheetManagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {

    /** All projects assigned to a specific user (active only). */
    @Query("""
           SELECT pa.project FROM ProjectAssignment pa
           WHERE pa.user = :user
           AND pa.project.active = true
           ORDER BY pa.project.name ASC
           """)
    List<Project> findActiveProjectsByUser(@Param("user") User user);

    /** Check if a specific project-user pair already exists. */
    boolean existsByProjectAndUser(Project project, User user);

    /** All assignments for a project (useful for unassigning). */
    List<ProjectAssignment> findAllByProject(Project project);

    /** All assignments for a user (e.g. to validate logging rights). */
    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    /**
     * Before deleting a user: remove every assignment where the user
     * is the assignee (user_id column).
     */
    void deleteAllByUser(User user);

    /**
     * Before deleting a user: null-out the assigner reference so the
     * assignment row is retained but no longer references the deleted user.
     */
    @Modifying
    @Query("UPDATE ProjectAssignment pa SET pa.assignedBy = null WHERE pa.assignedBy = :user")
    void clearAssignedBy(@Param("user") User user);
}

