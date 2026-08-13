package com.team3.gudit.auth;

import com.team3.gudit.auth.filter.TokenAuthenticationFilter;
import com.team3.gudit.auth.oauth2.OAuth2FailureHandler;
import com.team3.gudit.auth.oauth2.OAuth2SuccessHandler;
import com.team3.gudit.auth.security.CustomAuthenticationEntryPoint;
import com.team3.gudit.auth.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CustomOAuth2UserService customOAuth2UserService) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login-success.html",
                                "/login-failure.html",
                                "/oauth2/**",
                                "/login/**",
                                "/api/auth/reissue"
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

                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(
                                authenticationEntryPoint
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
