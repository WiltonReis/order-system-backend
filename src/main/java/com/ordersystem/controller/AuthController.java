package com.ordersystem.controller;

import com.ordersystem.dto.request.LoginRequest;
import com.ordersystem.dto.response.AuthResponse;
import com.ordersystem.exception.TooManyRequestsException;
import com.ordersystem.security.JwtTokenProvider;
import com.ordersystem.service.AuthService;
import com.ordersystem.service.LoginRateLimiterService;
import com.ordersystem.service.TokenBlacklistService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiterService loginRateLimiterService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${COOKIE_SECURE:false}")
    private boolean cookieSecure;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse response) {
        String clientIp = getClientIp(httpRequest);

        if (!loginRateLimiterService.isAllowed(clientIp)) {
            throw new TooManyRequestsException("Muitas tentativas de login. Tente novamente em 15 minutos.");
        }

        try {
            AuthResponse authResponse = authService.login(request);

            ResponseCookie cookie = ResponseCookie.from("oms.token", authResponse.getToken())
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofMillis(jwtExpiration))
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            return ResponseEntity.ok(authResponse);
        } catch (BadCredentialsException e) {
            log.warn("Tentativa de login falhou para o usuário '{}' a partir do IP: {}",
                    request.getUsername(), clientIp);
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = extractTokenFromRequest(request);
        if (token != null) {
            try {
                String jti = jwtTokenProvider.extractJti(token);
                tokenBlacklistService.revoke(jti, jwtTokenProvider.extractExpiration(token));
            } catch (Exception e) {
                log.debug("Falha ao revogar token no logout: {}", e.getMessage());
            }
        }

        ResponseCookie cookie = ResponseCookie.from("oms.token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok().build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("oms.token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
