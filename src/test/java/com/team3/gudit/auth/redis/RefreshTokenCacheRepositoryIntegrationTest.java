package com.team3.gudit.auth.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest
class RefreshTokenCacheRepositoryIntegrationTest {

    @Autowired
    private RefreshTokenCacheRepository refreshTokenCacheRepository;

    private static final Long USER_ID = 1L;

    @AfterEach
    void tearDown() {
        refreshTokenCacheRepository.delete(USER_ID);
    }

    @Test
    @DisplayName("Refresh Token hash를 Redis에 저장하고 조회할 수 있다")
    void saveAndFind() {
        // given
        String tokenHash = "test-hash";

        // when
        refreshTokenCacheRepository.save(
                USER_ID,
                tokenHash,
                Duration.ofMinutes(10)
        );

        // then
        Optional<String> result =
                refreshTokenCacheRepository.findByUserId(USER_ID);

        assertThat(result)
                .contains(tokenHash);
    }

    @Test
    @DisplayName("같은 userId로 저장하면 기존 캐시를 덮어쓴다")
    void overwrite() {
        // given
        refreshTokenCacheRepository.save(
                USER_ID,
                "old-hash",
                Duration.ofMinutes(10)
        );

        // when
        refreshTokenCacheRepository.save(
                USER_ID,
                "new-hash",
                Duration.ofMinutes(10)
        );

        // then
        Optional<String> result =
                refreshTokenCacheRepository.findByUserId(USER_ID);

        assertThat(result)
                .contains("new-hash");
    }

    @Test
    @DisplayName("Refresh Token 캐시를 삭제하면 조회되지 않는다")
    void delete() {
        // given
        refreshTokenCacheRepository.save(
                USER_ID,
                "test-hash",
                Duration.ofMinutes(10)
        );

        // when
        refreshTokenCacheRepository.delete(USER_ID);

        // then
        assertThat(
                refreshTokenCacheRepository.findByUserId(USER_ID)
        ).isEmpty();
    }

    @Test
    @DisplayName("TTL이 만료되면 Refresh Token 캐시가 조회되지 않는다")
    void expire() throws InterruptedException {
        // given
        refreshTokenCacheRepository.save(
                USER_ID,
                "test-hash",
                Duration.ofSeconds(1)
        );

        // when
        Thread.sleep(1500);

        // then
        assertThat(
                refreshTokenCacheRepository.findByUserId(USER_ID)
        ).isEmpty();
    }
}
