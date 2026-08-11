package com.team3.gudit.auth.jwt;

import com.team3.gudit.auth.security.CustomUserDetails;
import com.team3.gudit.user.domain.entity.Role;
import com.team3.gudit.user.domain.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenProvider {

    private static final String CLAIM_ROLE = "role";

    private final JwtProperties jwtProperties;

    private SecretKey secretKey;
    private JwtParser jwtParser;

    @PostConstruct
    private void init() {
        this.secretKey = Keys.hmacShaKeyFor(
                Base64.getDecoder()
                        .decode(jwtProperties.getSecretKey())
        );

        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
    }

    public Authentication getAuthentication(String token) {

        Claims claims = getClaims(token);

        Long userId =
                Long.valueOf(claims.getSubject());

        Role role =
                Role.valueOf(
                        claims.get(CLAIM_ROLE, String.class)
                );

        CustomUserDetails principal =
                new CustomUserDetails(
                        userId,
                        role
                );

        return new UsernamePasswordAuthenticationToken(
                principal,
                token,
                principal.getAuthorities()
        );
    }

    public String generateToken(User user, Duration validity) {
        Date now = new Date();
        Date expiration =
                new Date(now.getTime() + validity.toMillis());

        return Jwts.builder()
                .header()
                .type("JWT")
                .and()
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiration(expiration)

                // 우리 서비스의 User PK
                .subject(String.valueOf(user.getId()))

                .claim(
                        CLAIM_ROLE,
                        user.getRole().name()
                )

                .signWith(secretKey, Jwts.SIG.HS512)
                .compact();
    }

    public TokenStatus validateToken(String token) {
        try {
            jwtParser.parseSignedClaims(token);

            return TokenStatus.VALID;

        } catch (ExpiredJwtException e) {

            return TokenStatus.EXPIRED;

        } catch (Exception e) {

            return TokenStatus.INVALID;
        }
    }

    public Long getUserId(String token) {
        Claims claims = getClaims(token);

        return Long.valueOf(claims.getSubject());
    }

    public Role getRole(String token) {
        Claims claims = getClaims(token);

        return Role.valueOf(
                claims.get(CLAIM_ROLE, String.class)
        );
    }

    private Claims getClaims(String token) {
        return jwtParser
                .parseSignedClaims(token)
                .getPayload();
    }
}
