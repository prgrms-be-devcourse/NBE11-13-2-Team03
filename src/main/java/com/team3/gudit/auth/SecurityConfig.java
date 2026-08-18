package com.team3.gudit.auth;

import com.team3.gudit.auth.filter.TokenAuthenticationFilter;
import com.team3.gudit.auth.oauth2.OAuth2FailureHandler;
import com.team3.gudit.auth.oauth2.OAuth2SuccessHandler;
import com.team3.gudit.auth.security.CustomAuthenticationEntryPoint;
import com.team3.gudit.auth.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CustomOAuth2UserService customOAuth2UserService
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",

                                // 정적 리소스
                                "/css/**",
                                "/js/**",
                                "/images/**",

                                // OAuth2
                                "/login-success.html",
                                "/login-failure.html",
                                "/oauth2/**",
                                "/login/**",

                                // 사용자 화면
                                "/sales",
                                "/sales/**",
                                "/payments",
                                "/payments/**",
                                "/mypage/**",

                                // 관리자 화면
                                "/admin/**",

                                // 기존 결제 테스트 화면
                                "/payments/test",
                                "/payments/test/**",

                                // 인증
                                "/api/auth/reissue"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/sales",
                                "/api/sales/**"
                        ).permitAll()

                        .anyRequest().authenticated()
                )

                // 소셜 로그인
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(
                                userInfo ->
                                        userInfo.userService(customOAuth2UserService)
                        )
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                )

                .exceptionHandling(exception -> exception
                        .defaultAuthenticationEntryPointFor(
                                authenticationEntryPoint,
                                PathPatternRequestMatcher.withDefaults()
                                        .matcher("/api/**")
                        )
                )

                // access 토큰 검사
                .addFilterBefore(
                        tokenAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}