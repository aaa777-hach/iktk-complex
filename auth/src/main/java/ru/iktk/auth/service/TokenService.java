package ru.iktk.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.iktk.auth.model.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public TokenService(
            @Value("${iktk.security.jwt.secret}") String secret,
            @Value("${iktk.security.jwt.issuer:iktk}") String issuer,
            @Value("${iktk.security.jwt.access-ttl:PT15M}") Duration accessTtl,
            @Value("${iktk.security.jwt.refresh-ttl:P30D}") Duration refreshTtl
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT secret должен быть не короче 32 символов (iktk.security.jwt.secret)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public String generateAccessToken(User user) {
        return buildToken(user, accessTtl, "access");
    }

    public String generateRefreshToken(User user) {
        return buildToken(user, refreshTtl, "refresh");
    }

    public long getAccessTtlSeconds() {
        return accessTtl.getSeconds();
    }

    public String extractUsername(String token) {
        try {
            return parse(token).getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("extractUsername: invalid token — {}", e.getMessage());
            return null;
        }
    }

    public boolean isValid(String token) {
        try {
            Claims c = parse(token);
            return c.getExpiration() != null && c.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Map<String, Object> extractClaims(String token) {
        try {
            return new HashMap<>(parse(token));
        } catch (JwtException | IllegalArgumentException e) {
            return new HashMap<>();
        }
    }

    private String buildToken(User user, Duration ttl, String type) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);

        List<String> roles = user.getRoles() == null
                ? List.of()
                : user.getRoles().stream().map(r -> r.getName().name()).toList();

        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .claim("uid", user.getId())
                .claim("roles", roles)
                .claim("type", type)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(issuer)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}