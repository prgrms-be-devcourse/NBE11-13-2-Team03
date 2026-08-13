package com.team3.gudit.auth.service;

import com.team3.gudit.auth.domain.entity.RefreshToken;
import com.team3.gudit.auth.domain.repository.RefreshTokenRepository;
import com.team3.gudit.auth.dto.RefreshTokenResponseDto;
import com.team3.gudit.auth.jwt.*;
import com.team3.gudit.user.domain.entity.User;
import com.team3.gudit.user.domain.repository.UserRepository;
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
        Long userId =
                tokenProvider.getUserId(refreshToken);


        if (refreshToken == null) {
            throw new IllegalArgumentException(
                    "Refresh Token이 없습니다."
            );
        }

        RefreshToken storedToken =
                refreshTokenRepository.findByUserId(userId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "저장된 Refresh Token이 없습니다."
                                )
                        );

        if (tokenProvider.validateToken(
                refreshToken,
                TokenType.REFRESH
        ) != TokenStatus.VALID) {
            throw new IllegalArgumentException(
                    "Refresh Token이 없거나 유효하지 않습니다."
            );
        }

        if (tokenProvider.getTokenType(refreshToken) != TokenType.REFRESH) {
            throw new IllegalArgumentException(
                    "Refresh Token이 아닙니다."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "사용자를 찾을 수 없습니다."
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