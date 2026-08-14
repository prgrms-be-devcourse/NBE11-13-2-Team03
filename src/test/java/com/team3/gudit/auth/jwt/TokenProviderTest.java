package com.team3.gudit.auth.jwt;

import com.team3.gudit.auth.security.CustomUserDetails;
import com.team3.gudit.user.domain.entity.Role;
import com.team3.gudit.user.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;

import java.time.Duration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
public class TokenProviderTest {

    @Autowired
    private TokenProvider tokenProvider;
    private User user;

    @BeforeEach
    public void setUp() {
        user = User.builder()
                .id(1L)
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("정상적인 Access Token을 검증하면 VALID를 반환한다")
    void validateAccessToken_success() {

        // given
        String token = tokenProvider.generateToken(
                user,
                Duration.ofMinutes(30),
                TokenType.ACCESS
        );

        // when
        TokenStatus status = tokenProvider.validateToken(
                token,
                TokenType.ACCESS
        );

        // then
        assertThat(status)
                .isEqualTo(TokenStatus.VALID);
    }

    @Test
    @DisplayName("정상적인 Refresh Token은 VALID를 반환한다")
    void validateRefreshToken_success() {

        // given
        String refreshToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofDays(14),
                        TokenType.REFRESH
                );

        // when
        TokenStatus status =
                tokenProvider.validateToken(
                        refreshToken,
                        TokenType.REFRESH
                );

        // then
        assertThat(status)
                .isEqualTo(TokenStatus.VALID);
    }

    @Test
    @DisplayName("Access Token을 Refresh Token으로 검증하면 INVALID를 반환한다")
    void validateAccessToken_withRefreshType_fail() {

        // given
        String token = tokenProvider.generateToken(
                user,
                Duration.ofMinutes(30),
                TokenType.ACCESS
        );

        // when
        TokenStatus status = tokenProvider.validateToken(
                token,
                TokenType.REFRESH
        );

        // then
        assertThat(status)
                .isEqualTo(TokenStatus.INVALID);
    }

    @Test
    @DisplayName("Refresh Token을 Access Token으로 검증하면 INVALID를 반환한다")
    void validateRefreshToken_asAccessToken_fail() {

        // given
        String refreshToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofDays(14),
                        TokenType.REFRESH
                );

        // when
        TokenStatus status =
                tokenProvider.validateToken(
                        refreshToken,
                        TokenType.ACCESS
                );

        // then
        assertThat(status)
                .isEqualTo(TokenStatus.INVALID);
    }

    @Test
    @DisplayName("만료된 Token은 EXPIRED를 반환한다")
    void validateExpiredToken_fail() {

        // given
        String expiredToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofSeconds(-1),
                        TokenType.ACCESS
                );

        // when
        TokenStatus status =
                tokenProvider.validateToken(
                        expiredToken,
                        TokenType.ACCESS
                );

        // then
        assertThat(status)
                .isEqualTo(TokenStatus.EXPIRED);
    }

    @Test
    @DisplayName("변조된 Token은 INVALID를 반환한다")
    void validateTamperedToken_fail() {

        // given
        String accessToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofMinutes(30),
                        TokenType.ACCESS
                );

        String tamperedToken =
                accessToken.substring(
                        0,
                        accessToken.length() - 1
                ) + "invalid";

        // when
        TokenStatus status =
                tokenProvider.validateToken(
                        tamperedToken,
                        TokenType.ACCESS
                );

        // then
        assertThat(status)
                .isEqualTo(TokenStatus.INVALID);
    }

    @Test
    @DisplayName("Token에서 사용자 ID를 추출할 수 있다")
    void getUserId_success() {

        // given
        String accessToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofMinutes(30),
                        TokenType.ACCESS
                );

        // when
        Long userId =
                tokenProvider.getUserId(accessToken);

        // then
        assertThat(userId)
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Token에서 사용자 권한을 추출할 수 있다")
    void getRole_success() {

        // given
        String accessToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofMinutes(30),
                        TokenType.ACCESS
                );

        // when
        Role role =
                tokenProvider.getRole(accessToken);

        // then
        assertThat(role)
                .isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("Access Token으로 Authentication 객체를 생성할 수 있다")
    void getAuthentication_success() {

        // given
        String accessToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofMinutes(30),
                        TokenType.ACCESS
                );

        // when
        Authentication authentication =
                tokenProvider.getAuthentication(
                        accessToken
                );

        // then
        assertThat(authentication.isAuthenticated())
                .isTrue();

        assertThat(authentication.getPrincipal())
                .isInstanceOf(CustomUserDetails.class);

        CustomUserDetails principal =
                (CustomUserDetails)
                        authentication.getPrincipal();

        assertThat(principal.getUserId())
                .isEqualTo(1L);
    }
}
