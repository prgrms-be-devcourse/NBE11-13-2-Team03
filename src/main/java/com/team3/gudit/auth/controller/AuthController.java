package com.team3.gudit.auth.controller;

import com.team3.gudit.auth.jwt.JwtProperties;
import com.team3.gudit.auth.security.CustomUserDetails;
import com.team3.gudit.auth.service.AuthService;
import com.team3.gudit.auth.service.TokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final TokenService tokenService;
    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal CustomUserDetails user,
            HttpServletResponse response
    ) {
        authService.logout(user.getUserId());

        deleteCookie(response, "access_token");
        deleteCookie(response, "refresh_token");

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reissue")
    public ResponseEntity<Void> reissue(
            @CookieValue(name = "refresh_token", required = false)
            String refreshToken,
            HttpServletResponse response
    ) {

        TokenService.TokenPair tokenPair =
                tokenService.refreshToken(refreshToken);

        addTokenCookie(
                response,
                "access_token",
                tokenPair.accessToken(),
                (int) jwtProperties.getAccessTokenValidity().toSeconds()
        );

        addTokenCookie(
                response,
                "refresh_token",
                tokenPair.refreshToken(),
                (int) jwtProperties.getRefreshTokenValidity().toSeconds()
        );

        return ResponseEntity.ok().build();
    }

    private void addTokenCookie(
            HttpServletResponse response,
            String name,
            String value,
            int maxAge
    ) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true) // 로컬 HTTP 환경에서는 false
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
    private void deleteCookie(
            HttpServletResponse response,
            String name
    ) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);

        response.addCookie(cookie);
    }
}