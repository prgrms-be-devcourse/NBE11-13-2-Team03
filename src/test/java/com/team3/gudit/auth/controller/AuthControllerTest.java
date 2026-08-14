package com.team3.gudit.auth.controller;

import com.team3.gudit.auth.filter.TokenAuthenticationFilter;
import com.team3.gudit.auth.jwt.JwtProperties;
import com.team3.gudit.auth.security.CustomUserDetails;
import com.team3.gudit.auth.service.AuthService;
import com.team3.gudit.auth.service.TokenService;
import com.team3.gudit.user.domain.entity.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = TokenAuthenticationFilter.class
                )
        }
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtProperties jwtProperties;


    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }


    // =========================================================
    // reissue
    // =========================================================

    @Test
    @DisplayName("Refresh Token 재발급에 성공하면 새로운 Access Token과 Refresh Token을 쿠키로 반환한다")
    void reissue_success() throws Exception {

        // given
        String oldRefreshToken = "old-refresh-token";

        TokenService.TokenPair newTokenPair =
                new TokenService.TokenPair(
                        "new-access-token",
                        "new-refresh-token"
                );

        when(tokenService.reissueToken(oldRefreshToken))
                .thenReturn(newTokenPair);

        when(jwtProperties.getAccessTokenValidity())
                .thenReturn(Duration.ofMinutes(30));

        when(jwtProperties.getRefreshTokenValidity())
                .thenReturn(Duration.ofDays(14));


        // when
        MvcResult result =
                mockMvc.perform(
                                post("/api/auth/reissue")
                                        .cookie(
                                                new Cookie(
                                                        "refresh_token",
                                                        oldRefreshToken
                                                )
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();


        // then
        verify(tokenService)
                .reissueToken(oldRefreshToken);

        List<String> setCookieHeaders =
                result.getResponse()
                        .getHeaders(HttpHeaders.SET_COOKIE);

        assertThat(setCookieHeaders)
                .hasSize(2);

        assertThat(setCookieHeaders)
                .anySatisfy(cookie -> {
                    assertThat(cookie)
                            .contains("access_token=new-access-token");

                    assertThat(cookie)
                            .contains("HttpOnly");

                    assertThat(cookie)
                            .contains("Secure");

                    assertThat(cookie)
                            .contains("SameSite=Lax");

                    assertThat(cookie)
                            .contains("Path=/");

                    assertThat(cookie)
                            .contains("Max-Age=1800");
                });

        assertThat(setCookieHeaders)
                .anySatisfy(cookie -> {
                    assertThat(cookie)
                            .contains("refresh_token=new-refresh-token");

                    assertThat(cookie)
                            .contains("HttpOnly");

                    assertThat(cookie)
                            .contains("Secure");

                    assertThat(cookie)
                            .contains("SameSite=Lax");

                    assertThat(cookie)
                            .contains("Path=/");

                    assertThat(cookie)
                            .contains("Max-Age=1209600");
                });
    }


    @Test
    @DisplayName("Refresh Token 쿠키가 없으면 TokenService에 null을 전달한다")
    void reissue_withoutRefreshToken() throws Exception {

        // given
        when(tokenService.reissueToken(null))
                .thenThrow(
                        new IllegalArgumentException(
                                "Refresh Token이 없습니다."
                        )
                );


        // when & then
        mockMvc.perform(
                        post("/api/auth/reissue")
                )
                .andExpect(status().is5xxServerError());

        verify(tokenService)
                .reissueToken(null);
    }


    // =========================================================
    // logout
    // =========================================================

    @Test
    @DisplayName("로그아웃에 성공하면 Refresh Token을 삭제하고 Access Token과 Refresh Token 쿠키를 제거한다")
    void logout_success() throws Exception {

        // given
        Long userId = 1L;

        CustomUserDetails principal =
                new CustomUserDetails(
                        userId,
                        Role.USER
                );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);


        // when
        MvcResult result =
                mockMvc.perform(
                                post("/api/auth/logout")
                        )
                        .andExpect(status().isNoContent())
                        .andReturn();


        // then
        verify(authService)
                .logout(userId);

        Cookie[] cookies =
                result.getResponse()
                        .getCookies();

        assertThat(cookies)
                .hasSize(2);


        Cookie accessTokenCookie =
                Arrays.stream(cookies)
                        .filter(cookie ->
                                "access_token"
                                        .equals(cookie.getName())
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(accessTokenCookie.getValue())
                .isEmpty();

        assertThat(accessTokenCookie.getMaxAge())
                .isZero();

        assertThat(accessTokenCookie.getPath())
                .isEqualTo("/");

        assertThat(accessTokenCookie.isHttpOnly())
                .isTrue();


        Cookie refreshTokenCookie =
                Arrays.stream(cookies)
                        .filter(cookie ->
                                "refresh_token"
                                        .equals(cookie.getName())
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(refreshTokenCookie.getValue())
                .isEmpty();

        assertThat(refreshTokenCookie.getMaxAge())
                .isZero();

        assertThat(refreshTokenCookie.getPath())
                .isEqualTo("/");

        assertThat(refreshTokenCookie.isHttpOnly())
                .isTrue();
    }
}