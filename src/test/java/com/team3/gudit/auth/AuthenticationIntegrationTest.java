package com.team3.gudit.auth;

import com.team3.gudit.auth.jwt.TokenProvider;
import com.team3.gudit.auth.jwt.TokenType;
import com.team3.gudit.user.domain.entity.Role;
import com.team3.gudit.user.domain.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.team3.gudit.user.domain.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationIntegrationTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenProvider tokenProvider;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .kakaoId(12345L)
                .role(Role.USER)
                .nickname("testUser")
                .email("test@example.com")
                .build();

        user = userRepository.saveAndFlush(user);
    }


    @Test
    @DisplayName("정상 Access Token으로 보호 API에 접근하면 성공한다")
    void authenticated_success() throws Exception {

        // given
        String accessToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofMinutes(30),
                        TokenType.ACCESS
                );

        // when & then
        mockMvc.perform(
                        get("/api/users/me")
                                .cookie(
                                        new Cookie(
                                                "access_token",
                                                accessToken
                                        )
                                )
                )
                .andExpect(status().isOk());
    }


    @Test
    @DisplayName("Access Token 없이 보호 API에 접근하면 401을 반환한다")
    void unauthenticated_fail() throws Exception {

        // when & then
        mockMvc.perform(
                        get("/api/users/me")
                )
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("만료된 Access Token으로 보호 API에 접근하면 401을 반환한다")
    void expiredAccessToken_fail() throws Exception {

        // given
        String expiredToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofSeconds(-1),
                        TokenType.ACCESS
                );

        // when & then
        mockMvc.perform(
                        get("/api/users/me")
                                .cookie(
                                        new Cookie(
                                                "access_token",
                                                expiredToken
                                        )
                                )
                )
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("유효하지 않은 Access Token으로 보호 API에 접근하면 401을 반환한다")
    void invalidAccessToken_fail() throws Exception {

        // when & then
        mockMvc.perform(
                        get("/api/users/me")
                                .cookie(
                                        new Cookie(
                                                "access_token",
                                                "invalid-token"
                                        )
                                )
                )
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("Refresh Token으로 보호 API에 접근하면 401을 반환한다")
    void refreshTokenAsAccessToken_fail() throws Exception {

        // given
        String refreshToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofDays(14),
                        TokenType.REFRESH
                );

        // when & then
        mockMvc.perform(
                        get("/api/users/me")
                                .cookie(
                                        new Cookie(
                                                "access_token",
                                                refreshToken
                                        )
                                )
                )
                .andExpect(status().isUnauthorized());
    }
}