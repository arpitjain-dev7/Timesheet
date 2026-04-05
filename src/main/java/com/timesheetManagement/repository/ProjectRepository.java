package com.timesheetManagement.repository;

import com.timesheetManagement.entity.Project;
import com.timesheetManagement.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByCode(String code);

    boolean existsByCode(String code);

    /** Case-insensitive name existence check — used before querying users by project name. */
    boolean existsByNameIgnoreCase(String name);

    /** Used by users to see only projects they can log time against. */
    Page<Project> findAllByActiveTrue(Pageable pageable);

    /**
     * Before deleting a user: null-out the creator reference so the
     * project row is retained but no longer references the deleted user.
     */
    @Modifying
    @Query("UPDATE Project p SET p.createdBy = null WHERE p.createdBy = :user")
    void clearCreatedBy(@Param("user") User user);
}

