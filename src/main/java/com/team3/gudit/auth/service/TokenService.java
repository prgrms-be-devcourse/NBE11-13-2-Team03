package com.team3.gudit.auth.service;

import com.team3.gudit.auth.dto.RefreshTokenResponseDto;
import com.team3.gudit.auth.jwt.JwtProperties;
import com.team3.gudit.auth.jwt.TokenProvider;
import com.team3.gudit.auth.jwt.TokenStatus;
import com.team3.gudit.user.domain.entity.User;
import com.team3.gudit.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
                jwtProperties.getAccessTokenValidity()
        );

        String refreshToken = tokenProvider.generateToken(
                user,
                jwtProperties.getRefreshTokenValidity()
        );

        return new TokenPair(
                accessToken,
                refreshToken
        );
    }

    public TokenPair refreshToken(String refreshToken) {

        if (refreshToken == null) {
            throw new IllegalArgumentException(
                    "Refresh Token이 없습니다."
            );
        }

        if (tokenProvider.validateToken(refreshToken)
                != TokenStatus.VALID) {

            throw new IllegalArgumentException(
                    "유효하지 않은 Refresh Token입니다."
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