package com.team3.gudit.auth.service;

import com.team3.gudit.auth.domain.entity.RefreshToken;
import com.team3.gudit.auth.domain.repository.RefreshTokenRepository;
import com.team3.gudit.auth.exception.AuthErrorCode;
import com.team3.gudit.auth.jwt.JwtProperties;
import com.team3.gudit.auth.jwt.RefreshTokenHasher;
import com.team3.gudit.auth.jwt.TokenProvider;
import com.team3.gudit.auth.jwt.TokenStatus;
import com.team3.gudit.auth.jwt.TokenType;
import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.user.domain.entity.Role;
import com.team3.gudit.user.domain.entity.User;
import com.team3.gudit.user.domain.repository.UserRepository;
import com.team3.gudit.user.exception.UserErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private TokenService tokenService;

    private User user;

    private static final Long USER_ID = 1L;

    private static final String ACCESS_TOKEN =
            "access-token";

    private static final String REFRESH_TOKEN =
            "refresh-token";

    private static final String NEW_ACCESS_TOKEN =
            "new-access-token";

    private static final String NEW_REFRESH_TOKEN =
            "new-refresh-token";

    private static final String STORED_HASH =
            "stored-refresh-token-hash";

    private static final String NEW_HASH =
            "new-refresh-token-hash";

    private static final Duration ACCESS_VALIDITY =
            Duration.ofMinutes(30);

    private static final Duration REFRESH_VALIDITY =
            Duration.ofDays(14);


    @BeforeEach
    void setUp() {

        user = User.builder()
                .nickname("testUser")
                .role(Role.USER)
                .build();

        ReflectionTestUtils.setField(
                user,
                "id",
                USER_ID
        );
    }


    // =========================================================
    // issueToken
    // =========================================================

    @Test
    @DisplayName(
            "토큰 발급 시 Access Token과 Refresh Token을 발급하고 Refresh Token을 저장한다"
    )
    void issueToken_success_newRefreshToken() {

        // given
        when(jwtProperties.getAccessTokenValidity())
                .thenReturn(ACCESS_VALIDITY);

        when(jwtProperties.getRefreshTokenValidity())
                .thenReturn(REFRESH_VALIDITY);

        when(tokenProvider.generateToken(
                user,
                ACCESS_VALIDITY,
                TokenType.ACCESS
        )).thenReturn(ACCESS_TOKEN);

        when(tokenProvider.generateToken(
                user,
                REFRESH_VALIDITY,
                TokenType.REFRESH
        )).thenReturn(REFRESH_TOKEN);

        when(refreshTokenHasher.hash(REFRESH_TOKEN))
                .thenReturn(STORED_HASH);

        when(refreshTokenRepository.findByUserId(USER_ID))
                .thenReturn(Optional.empty());


        // when
        TokenService.TokenPair result =
                tokenService.issueToken(user);


        // then
        assertThat(result.accessToken())
                .isEqualTo(ACCESS_TOKEN);

        assertThat(result.refreshToken())
                .isEqualTo(REFRESH_TOKEN);


        ArgumentCaptor<RefreshToken> captor =
                ArgumentCaptor.forClass(
                        RefreshToken.class
                );

        verify(refreshTokenRepository)
                .save(captor.capture());

        RefreshToken savedToken =
                captor.getValue();

        assertThat(savedToken.getUser())
                .isEqualTo(user);

        assertThat(savedToken.getTokenHash())
                .isEqualTo(STORED_HASH);

        assertThat(savedToken.getExpiresAt())
                .isNotNull();
    }


    @Test
    @DisplayName(
            "기존 Refresh Token이 있으면 새로운 Refresh Token으로 갱신한다"
    )
    void issueToken_success_existingRefreshToken() {

        // given
        RefreshToken storedToken =
                RefreshToken.builder()
                        .user(user)
                        .tokenHash("old-hash")
                        .expiresAt(
                                LocalDateTime.now()
                                        .plusDays(1)
                        )
                        .build();

        ReflectionTestUtils.setField(
                storedToken,
                "id",
                1L
        );


        when(jwtProperties.getAccessTokenValidity())
                .thenReturn(ACCESS_VALIDITY);

        when(jwtProperties.getRefreshTokenValidity())
                .thenReturn(REFRESH_VALIDITY);

        when(tokenProvider.generateToken(
                user,
                ACCESS_VALIDITY,
                TokenType.ACCESS
        )).thenReturn(ACCESS_TOKEN);

        when(tokenProvider.generateToken(
                user,
                REFRESH_VALIDITY,
                TokenType.REFRESH
        )).thenReturn(REFRESH_TOKEN);

        when(refreshTokenHasher.hash(REFRESH_TOKEN))
                .thenReturn(STORED_HASH);

        when(refreshTokenRepository.findByUserId(USER_ID))
                .thenReturn(
                        Optional.of(storedToken)
                );


        // when
        TokenService.TokenPair result =
                tokenService.issueToken(user);


        // then
        assertThat(result.accessToken())
                .isEqualTo(ACCESS_TOKEN);

        assertThat(result.refreshToken())
                .isEqualTo(REFRESH_TOKEN);

        assertThat(storedToken.getTokenHash())
                .isEqualTo(STORED_HASH);

        verify(refreshTokenRepository)
                .save(storedToken);
    }


    // =========================================================
    // refreshToken - 성공
    // =========================================================

    @Test
    @DisplayName(
            "유효한 Refresh Token이면 새로운 Access Token과 Refresh Token을 발급한다"
    )
    void refreshToken_success() {

        // given
        RefreshToken storedToken =
                RefreshToken.builder()
                        .user(user)
                        .tokenHash(STORED_HASH)
                        .expiresAt(
                                LocalDateTime.now()
                                        .plusDays(14)
                        )
                        .build();


        when(tokenProvider.validateToken(
                REFRESH_TOKEN,
                TokenType.REFRESH
        )).thenReturn(TokenStatus.VALID);

        when(tokenProvider.getUserId(REFRESH_TOKEN))
                .thenReturn(USER_ID);

        when(refreshTokenRepository.findByUserId(USER_ID))
                .thenReturn(
                        Optional.of(storedToken)
                );

        when(refreshTokenHasher.matches(
                STORED_HASH,
                REFRESH_TOKEN
        )).thenReturn(true);

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));


        when(jwtProperties.getAccessTokenValidity())
                .thenReturn(ACCESS_VALIDITY);

        when(jwtProperties.getRefreshTokenValidity())
                .thenReturn(REFRESH_VALIDITY);


        when(tokenProvider.generateToken(
                user,
                ACCESS_VALIDITY,
                TokenType.ACCESS
        )).thenReturn(NEW_ACCESS_TOKEN);

        when(tokenProvider.generateToken(
                user,
                REFRESH_VALIDITY,
                TokenType.REFRESH
        )).thenReturn(NEW_REFRESH_TOKEN);

        when(refreshTokenHasher.hash(
                NEW_REFRESH_TOKEN
        )).thenReturn(NEW_HASH);


        // when
        TokenService.TokenPair result =
                tokenService.refreshToken(
                        REFRESH_TOKEN
                );


        // then
        assertThat(result.accessToken())
                .isEqualTo(NEW_ACCESS_TOKEN);

        assertThat(result.refreshToken())
                .isEqualTo(NEW_REFRESH_TOKEN);

        assertThat(storedToken.getTokenHash())
                .isEqualTo(NEW_HASH);


        verify(tokenProvider, times(1))
                .generateToken(
                        user,
                        ACCESS_VALIDITY,
                        TokenType.ACCESS
                );

        verify(tokenProvider, times(1))
                .generateToken(
                        user,
                        REFRESH_VALIDITY,
                        TokenType.REFRESH
                );
    }


    // =========================================================
    // refreshToken - 예외
    // =========================================================

    @Test
    @DisplayName(
            "Refresh Token이 null이면 REFRESH_TOKEN_NOT_FOUND 예외가 발생한다"
    )
    void refreshToken_fail_nullToken() {

        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                tokenService.refreshToken(
                                        null
                                )
                );


        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AuthErrorCode
                                .REFRESH_TOKEN_NOT_FOUND
                );

        verifyNoInteractions(
                tokenProvider,
                refreshTokenRepository,
                userRepository
        );
    }


    @Test
    @DisplayName(
            "Refresh Token이 빈 문자열이면 REFRESH_TOKEN_NOT_FOUND 예외가 발생한다"
    )
    void refreshToken_fail_blankToken() {

        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                tokenService.refreshToken(
                                        " "
                                )
                );


        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AuthErrorCode
                                .REFRESH_TOKEN_NOT_FOUND
                );

        verifyNoInteractions(
                tokenProvider,
                refreshTokenRepository,
                userRepository
        );
    }


    @Test
    @DisplayName(
            "만료된 Refresh Token이면 EXPIRED_REFRESH_TOKEN 예외가 발생한다"
    )
    void refreshToken_fail_expiredToken() {

        // given
        when(tokenProvider.validateToken(
                REFRESH_TOKEN,
                TokenType.REFRESH
        )).thenReturn(
                TokenStatus.EXPIRED
        );


        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                tokenService.refreshToken(
                                        REFRESH_TOKEN
                                )
                );


        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AuthErrorCode
                                .EXPIRED_REFRESH_TOKEN
                );

        verify(tokenProvider, never())
                .getUserId(anyString());

        verifyNoInteractions(
                refreshTokenRepository,
                userRepository
        );
    }


    @Test
    @DisplayName(
            "유효하지 않은 Refresh Token이면 INVALID_REFRESH_TOKEN 예외가 발생한다"
    )
    void refreshToken_fail_invalidToken() {

        // given
        when(tokenProvider.validateToken(
                REFRESH_TOKEN,
                TokenType.REFRESH
        )).thenReturn(
                TokenStatus.INVALID
        );


        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                tokenService.refreshToken(
                                        REFRESH_TOKEN
                                )
                );


        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AuthErrorCode
                                .INVALID_REFRESH_TOKEN
                );

        verify(tokenProvider, never())
                .getUserId(anyString());

        verifyNoInteractions(
                refreshTokenRepository,
                userRepository
        );
    }


    @Test
    @DisplayName(
            "DB에 Refresh Token이 없으면 REFRESH_TOKEN_NOT_FOUND 예외가 발생한다"
    )
    void refreshToken_fail_storedTokenNotFound() {

        // given
        when(tokenProvider.validateToken(
                REFRESH_TOKEN,
                TokenType.REFRESH
        )).thenReturn(
                TokenStatus.VALID
        );

        when(tokenProvider.getUserId(
                REFRESH_TOKEN
        )).thenReturn(USER_ID);

        when(refreshTokenRepository
                .findByUserId(USER_ID))
                .thenReturn(
                        Optional.empty()
                );


        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                tokenService.refreshToken(
                                        REFRESH_TOKEN
                                )
                );


        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AuthErrorCode
                                .REFRESH_TOKEN_NOT_FOUND
                );

        verifyNoInteractions(
                userRepository
        );
    }


    @Test
    @DisplayName(
            "요청 Refresh Token과 DB의 Refresh Token이 일치하지 않으면 REFRESH_TOKEN_MISMATCH 예외가 발생한다"
    )
    void refreshToken_fail_tokenMismatch() {

        // given
        RefreshToken storedToken =
                RefreshToken.builder()
                        .user(user)
                        .tokenHash(STORED_HASH)
                        .expiresAt(
                                LocalDateTime.now()
                                        .plusDays(14)
                        )
                        .build();


        when(tokenProvider.validateToken(
                REFRESH_TOKEN,
                TokenType.REFRESH
        )).thenReturn(
                TokenStatus.VALID
        );

        when(tokenProvider.getUserId(
                REFRESH_TOKEN
        )).thenReturn(USER_ID);

        when(refreshTokenRepository
                .findByUserId(USER_ID))
                .thenReturn(
                        Optional.of(storedToken)
                );

        when(refreshTokenHasher.matches(
                STORED_HASH,
                REFRESH_TOKEN
        )).thenReturn(false);


        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                tokenService.refreshToken(
                                        REFRESH_TOKEN
                                )
                );


        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AuthErrorCode
                                .REFRESH_TOKEN_MISMATCH
                );

        verifyNoInteractions(
                userRepository
        );
    }


    @Test
    @DisplayName(
            "Refresh Token의 사용자가 존재하지 않으면 USER_NOT_FOUND 예외가 발생한다"
    )
    void refreshToken_fail_userNotFound() {

        // given
        RefreshToken storedToken =
                RefreshToken.builder()
                        .user(user)
                        .tokenHash(STORED_HASH)
                        .expiresAt(
                                LocalDateTime.now()
                                        .plusDays(14)
                        )
                        .build();


        when(tokenProvider.validateToken(
                REFRESH_TOKEN,
                TokenType.REFRESH
        )).thenReturn(
                TokenStatus.VALID
        );

        when(tokenProvider.getUserId(
                REFRESH_TOKEN
        )).thenReturn(USER_ID);

        when(refreshTokenRepository
                .findByUserId(USER_ID))
                .thenReturn(
                        Optional.of(storedToken)
                );

        when(refreshTokenHasher.matches(
                STORED_HASH,
                REFRESH_TOKEN
        )).thenReturn(true);

        when(userRepository.findById(USER_ID))
                .thenReturn(
                        Optional.empty()
                );


        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                tokenService.refreshToken(
                                        REFRESH_TOKEN
                                )
                );


        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        UserErrorCode.USER_NOT_FOUND
                );

        verify(tokenProvider, never())
                .generateToken(
                        any(),
                        any(),
                        any()
                );
    }
}