package com.team3.gudit.auth.service;

import com.team3.gudit.auth.domain.entity.RefreshToken;
import com.team3.gudit.auth.domain.repository.RefreshTokenRepository;
import com.team3.gudit.auth.dto.RefreshTokenResponseDto;
import com.team3.gudit.auth.exception.AuthErrorCode;
import com.team3.gudit.auth.jwt.*;
import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.user.domain.entity.User;
import com.team3.gudit.user.domain.repository.UserRepository;
import com.team3.gudit.user.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenRepository refreshTokenRepository;

    public record TokenPair(
            String accessToken,
            String refreshToken
    ) {}

    @Transactional
    public TokenPair issueToken(User user) {
        TokenPair tokenPair = generateTokenPair(user);

        saveOrUpdateRefreshToken(
                user,
                tokenPair.refreshToken()
        );

        return tokenPair;
    }

    @Transactional
    public TokenPair refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(
                    AuthErrorCode.REFRESH_TOKEN_NOT_FOUND
            );
        }

        TokenStatus tokenStatus =
                tokenProvider.validateToken(
                        refreshToken,
                        TokenType.REFRESH
                );

        if (tokenStatus == TokenStatus.EXPIRED) {
            throw new BusinessException(
                    AuthErrorCode.EXPIRED_REFRESH_TOKEN
            );
        }

        if (tokenStatus != TokenStatus.VALID) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_REFRESH_TOKEN
            );
        }

        Long userId =
                tokenProvider.getUserId(refreshToken);

        RefreshToken storedToken =
                refreshTokenRepository.findByUserId(userId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        AuthErrorCode.REFRESH_TOKEN_NOT_FOUND
                                )
                        );

        if (tokenProvider.getTokenType(refreshToken) != TokenType.REFRESH) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_TOKEN_TYPE
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                UserErrorCode.USER_NOT_FOUND
                        )
                );

        TokenPair newTokenPair =
                generateTokenPair(user);


        storedToken.rotate(
                refreshTokenHasher.hash(
                        newTokenPair.refreshToken()
                ),
                LocalDateTime.now().plus(
                        jwtProperties.getRefreshTokenValidity()
                )
        );


        return issueToken(user);
    }

    public TokenPair generateTokenPair(User user) {
        String accessToken = tokenProvider.generateToken(
                user,
                jwtProperties.getAccessTokenValidity(),
                TokenType.ACCESS
        );
        String refreshToken = tokenProvider.generateToken(
                user,
                jwtProperties.getRefreshTokenValidity(),
                TokenType.REFRESH
        );
        return new TokenPair(
                accessToken,
                refreshToken
        );
    }

    private void saveOrUpdateRefreshToken(
            User user,
            String refreshToken
    ) {
        String tokenHash =
                refreshTokenHasher.hash(refreshToken);

        LocalDateTime expiresAt =
                LocalDateTime.now().plus(
                        jwtProperties.getRefreshTokenValidity()
                );

        RefreshToken storedToken =
                refreshTokenRepository.findByUserId(user.getId())
                        .orElseGet(() ->
                                RefreshToken.builder()
                                        .user(user)
                                        .tokenHash(tokenHash)
                                        .expiresAt(expiresAt)
                                        .build()
                        );

        if (storedToken.getId() != null) {
            storedToken.rotate(tokenHash, expiresAt);
        }

        refreshTokenRepository.save(storedToken);
    }
}