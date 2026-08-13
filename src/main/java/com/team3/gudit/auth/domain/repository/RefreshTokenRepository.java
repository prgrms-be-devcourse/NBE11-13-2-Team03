package com.team3.gudit.auth.domain.repository;

import com.team3.gudit.auth.domain.entity.RefreshToken;
import com.team3.gudit.user.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    Long user(User user);
}
