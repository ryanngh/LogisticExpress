package com.ryan.logisticexpress.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

@Service
public class JwtService {

    private final String secret;
    @Getter
    private final long expirationMs;
    @Getter
    private final long refreshExpirationMs;
    @Getter
    private final String cookieName;
    @Getter
    private final String refreshCookieName;
    private final SecretKey signingKey;

    @Autowired
    public JwtService(
            @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs,
            @Value("${jwt.refresh-token.expiration-ms:${jwt.refresh-expiration-ms:604800000}}") long refreshExpirationMs,
            @Value("${jwt.cookie-name:ACCESS_TOKEN}") String cookieName,
            @Value("${jwt.refresh-token.cookie-name:${jwt.refresh-cookie-name:REFRESH_TOKEN}}") String refreshCookieName
    ) {
        this.secret = secret;
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.cookieName = cookieName;
        this.refreshCookieName = refreshCookieName;
        this.signingKey = initSigningKey(secret);
    }

    public JwtService(String secret, long expirationMs) {
        this(secret, expirationMs, 604800000L, "ACCESS_TOKEN", "REFRESH_TOKEN");
    }

    public JwtService(String secret, long expirationMs, long refreshExpirationMs) {
        this(secret, expirationMs, refreshExpirationMs, "ACCESS_TOKEN", "REFRESH_TOKEN");
    }

    private SecretKey initSigningKey(String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            try {
                keyBytes = MessageDigest.getInstance("SHA-256").digest(keyBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 algorithm not available", e);
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(Map.of(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        if (userDetails instanceof CustomUserDetails customUserDetails) {
            if (customUserDetails.getId() != null) {
                claims.put("uid", customUserDetails.getId().toString());
            }
            claims.put("token_version", customUserDetails.getTokenVersion());
        }
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        claims.put("roles", roles);
        claims.put("token_type", "ACCESS");

        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return generateRefreshToken(Map.of(), userDetails);
    }

    public String generateRefreshToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        if (userDetails instanceof CustomUserDetails customUserDetails) {
            if (customUserDetails.getId() != null) {
                claims.put("uid", customUserDetails.getId().toString());
            }
            claims.put("token_version", customUserDetails.getTokenVersion());
        }
        claims.put("token_type", "REFRESH");

        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshExpirationMs))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public UUID extractUserId(String token) {
        String uid = extractClaim(token, claims -> claims.get("uid", String.class));
        return uid != null ? UUID.fromString(uid) : null;
    }

    public Integer extractTokenVersion(String token) {
        return extractClaim(token, claims -> claims.get("token_version", Integer.class));
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("token_type", String.class));
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractClaim(token, claims -> claims.get("roles", List.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            if (!username.equals(userDetails.getUsername()) || isTokenExpired(token)) {
                return false;
            }
            if (userDetails instanceof CustomUserDetails customUserDetails) {
                Integer tokenVersion = extractTokenVersion(token);
                if (tokenVersion != null && !tokenVersion.equals(customUserDetails.getTokenVersion())) {
                    return false;
                }
            }
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
