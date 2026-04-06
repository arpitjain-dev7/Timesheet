package com.timesheetManagement.repository;

import com.timesheetManagement.entity.RoleName;
import com.timesheetManagement.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Looks up a user by the SHA-256 hash of their password-reset token.
     * The raw token is never stored — only the hash is persisted.
     */
    Optional<User> findByResetToken(String resetTokenHash);

    /**
     * Returns a paginated list of users who do NOT hold any of the
     * specified roles (e.g. excludes ROLE_ADMIN and ROLE_MANAGER).
     * Used by the "Get All Users" API so managers/admins only see
     * regular USER-role accounts.
     */
    @Query("""
           SELECT u FROM User u
           WHERE NOT EXISTS (
               SELECT r FROM u.roles r
               WHERE r.name IN :excludedRoles
           )
           """)
    Page<User> findAllExcludingRoles(
            @Param("excludedRoles") Collection<RoleName> excludedRoles,
            Pageable pageable);

    /**
     * Returns every user who holds {@code ROLE_MANAGER}, ordered
     * alphabetically by first name then last name.
     *
     * <p>Used by {@code GET /api/users/managers} to populate UI dropdowns.
     * A single JPQL query — no extra round-trips.
     */
    @Query("""
           SELECT u FROM User u
           JOIN u.roles r
           WHERE r.name = :roleName
           ORDER BY u.firstName ASC, u.lastName ASC
           """)
    List<User> findAllByRoleName(@Param("roleName") RoleName roleName);
}

