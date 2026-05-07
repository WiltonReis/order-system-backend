package com.ordersystem.security;

import com.ordersystem.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails, jwtProperties.expiration());
    }

    public String generateToken(UserDetails userDetails, long expirationMs) {
        var builder = Jwts.builder()
                .subject(userDetails.getUsername())
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs));

        if (userDetails instanceof UserPrincipal principal && principal.getCustomerSaasId() != null) {
            builder.claim("tenantId", principal.getCustomerSaasId().toString());
        }

        return builder.signWith(getSigningKey()).compact();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractJti(String token) {
        return extractClaims(token).getId();
    }

    public UUID extractTenantId(String token) {
        Object claim = extractClaims(token).get("tenantId");
        return claim == null ? null : UUID.fromString(claim.toString());
    }

    public LocalDateTime extractIssuedAt(String token) {
        return extractClaims(token).getIssuedAt().toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public LocalDateTime extractExpiration(String token) {
        return extractClaims(token).getExpiration().toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.secret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
