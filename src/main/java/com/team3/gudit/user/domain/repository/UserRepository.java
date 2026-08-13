package com.team3.gudit.user.domain.repository;

import com.team3.gudit.auth.oauth2.AuthProvider;
import com.team3.gudit.user.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByKakaoIdAndProvider(Long kakaoId, AuthProvider provider);
}
