package com.ordersystem.controller;

import com.ordersystem.config.CookieProperties;
import com.ordersystem.config.JwtProperties;
import com.ordersystem.dto.request.LoginRequest;
import com.ordersystem.dto.request.RegisterRequest;
import com.ordersystem.dto.response.AuthResponse;
import com.ordersystem.dto.response.RegisterResponse;
import com.ordersystem.exception.TooManyRequestsException;
import com.ordersystem.security.JwtTokenProvider;
import com.ordersystem.security.UserDetailsServiceImpl;
import com.ordersystem.service.AuthService;
import com.ordersystem.service.LoginRateLimiterService;
import com.ordersystem.service.RefreshTokenService;
import com.ordersystem.service.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Registro, login, refresh e logout de sessão")
public class AuthController {

    private static final String ACCESS_COOKIE = "oms.token";
    private static final String REFRESH_COOKIE = "oms.refresh";

    private final AuthService authService;
    private final LoginRateLimiterService loginRateLimiterService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtProperties jwtProperties;
    private final CookieProperties cookieProperties;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Autentica usuário e define cookies httpOnly com access e refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Credenciais incorretas", content = @Content),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas — aguarde 15 min", content = @Content)
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse response) {
        String clientIp = getClientIp(httpRequest);

        if (!loginRateLimiterService.isAllowed(clientIp)) {
            throw new TooManyRequestsException("Muitas tentativas de login. Tente novamente em 15 minutos.");
        }

        try {
            AuthService.LoginResult result = authService.login(request);

            setAccessCookie(response, result.authResponse().getToken());
            setRefreshCookie(response, result.refreshToken());

            return ResponseEntity.ok(result.authResponse());
        } catch (BadCredentialsException e) {
            log.warn("Tentativa de login falhou para o e-mail '{}' a partir do IP: {}",
                    request.getEmail(), clientIp);
            throw e;
        }
    }

    @PostMapping("/register")
    @Operation(summary = "Registro de tenant", description = "Cria novo tenant SaaS e usuário ADMIN_MASTER associado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tenant criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "409", description = "E-mail ou CPF/CNPJ já cadastrado", content = @Content),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas — aguarde 15 min", content = @Content)
    })
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (!loginRateLimiterService.isAllowed(clientIp)) {
            throw new TooManyRequestsException("Muitas tentativas de registro. Tente novamente em 15 minutos.");
        }
        RegisterResponse body = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar access token", description = "Gera novo access token a partir do refresh token (cookie oms.refresh).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token renovado"),
            @ApiResponse(responseCode = "401", description = "Refresh token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas", content = @Content)
    })
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String clientIp = getClientIp(request);
        if (!loginRateLimiterService.isAllowed(clientIp)) {
            throw new TooManyRequestsException("Muitas tentativas de refresh. Tente novamente em 15 minutos.");
        }

        String refreshTokenValue = extractCookieValue(request, REFRESH_COOKIE);

        if (refreshTokenValue == null) {
            throw new BadCredentialsException("Refresh token ausente");
        }

        java.util.UUID userId = refreshTokenService.validate(refreshTokenValue);
        UserDetails userDetails = userDetailsService.loadById(userId);
        String newAccessToken = jwtTokenProvider.generateToken(userDetails);

        setAccessCookie(response, newAccessToken);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoga tokens e limpa cookies de sessão.")
    @ApiResponse(responseCode = "200", description = "Logout realizado")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = extractCookieValue(request, ACCESS_COOKIE);
        if (accessToken == null) {
            accessToken = extractBearerToken(request);
        }

        if (accessToken != null) {
            try {
                String jti = jwtTokenProvider.extractJti(accessToken);
                tokenBlacklistService.revoke(jti, jwtTokenProvider.extractExpiration(accessToken));
            } catch (Exception e) {
                log.debug("Falha ao revogar access token no logout: {}", e.getMessage());
            }
        }

        String refreshToken = extractCookieValue(request, REFRESH_COOKIE);
        if (refreshToken != null) {
            try {
                refreshTokenService.delete(refreshToken);
            } catch (Exception e) {
                log.debug("Falha ao revogar refresh token no logout: {}", e.getMessage());
            }
        }

        clearCookie(response, ACCESS_COOKIE, "/");
        clearCookie(response, REFRESH_COOKIE, "/auth");
        return ResponseEntity.ok().build();
    }

    private void setAccessCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_COOKIE, token)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("None")
                .path("/")
                .maxAge(Duration.ofMillis(jwtProperties.expiration()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("None")
                .path("/auth")
                .maxAge(Duration.ofMillis(jwtProperties.refreshExpiration()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name, String path) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("None")
                .path(path)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
