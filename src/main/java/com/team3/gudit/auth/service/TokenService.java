package com.team3.gudit.auth.service;

import com.team3.gudit.auth.dto.RefreshTokenResponseDto;
import com.team3.gudit.auth.jwt.JwtProperties;
import com.team3.gudit.auth.jwt.TokenProvider;
import com.team3.gudit.auth.jwt.TokenStatus;
import com.team3.gudit.auth.jwt.TokenType;
import com.team3.gudit.user.domain.entity.User;
import com.team3.gudit.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;

    public record TokenPair(
            String accessToken,
            String refreshToken
    ) {}

    public TokenPair issueToken(User user) {

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

    @Transactional
    public TokenPair refreshToken(String refreshToken) {
        if (refreshToken == null) {
            throw new IllegalArgumentException(
                    "Refresh Token이 없습니다."
            );
        }

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

        Long userId =
                tokenProvider.getUserId(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "사용자를 찾을 수 없습니다."
                        )
                );

        return issueToken(user);
    }
}