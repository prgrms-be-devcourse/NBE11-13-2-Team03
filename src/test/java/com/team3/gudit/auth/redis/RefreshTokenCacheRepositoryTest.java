package com.team3.gudit.auth.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
class RefreshTokenCacheRepositoryTest {

    @Autowired
    private RefreshTokenCacheRepository refreshTokenCacheRepository;

    @Test
    @DisplayName("Refresh Token hash를 Redis에 저장하고 조회할 수 있다.")
    void saveAndFind() {
        // given
        Long userId = 1L;
        String tokenHash = "hash-value";

        // when
        refreshTokenCacheRepository.save(
                userId,
                tokenHash,
                Duration.ofMinutes(10)
        );

        // then
        String result = refreshTokenCacheRepository
                .findByUserId(userId)
                .orElseThrow();

        assertThat(result).isEqualTo(tokenHash);
    }

    @Test
    @DisplayName("같은 userId로 저장하면 기존 Refresh Token hash를 덮어쓴다.")
    void overwrite() {
        // given
        Long userId = 1L;

        refreshTokenCacheRepository.save(
                userId,
                "old-hash",
                Duration.ofMinutes(10)
        );

        // when
        refreshTokenCacheRepository.save(
                userId,
                "new-hash",
                Duration.ofMinutes(10)
        );

        // then
        String result = refreshTokenCacheRepository
                .findByUserId(userId)
                .orElseThrow();

        assertThat(result).isEqualTo("new-hash");
    }

    @Test
    @DisplayName("Refresh Token 캐시를 삭제하면 더 이상 조회되지 않는다.")
    void delete() {
        // given
        Long userId = 1L;

        refreshTokenCacheRepository.save(
                userId,
                "hash-value",
                Duration.ofMinutes(10)
        );

        // when
        refreshTokenCacheRepository.delete(userId);

        // then
        assertThat(
                refreshTokenCacheRepository.findByUserId(userId)
        ).isEmpty();
    }
}