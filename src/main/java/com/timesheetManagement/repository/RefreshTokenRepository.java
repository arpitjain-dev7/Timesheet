package com.timesheetManagement.repository;

import com.timesheetManagement.entity.RefreshToken;
import com.timesheetManagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUser_Username(String username);

    @Modifying
    @Transactional
    int deleteByUser(User user);
}


