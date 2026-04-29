package com.ordersystem.service;

import com.ordersystem.dto.request.LoginRequest;
import com.ordersystem.dto.response.AuthResponse;
import com.ordersystem.security.JwtTokenProvider;
import com.ordersystem.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public record LoginResult(AuthResponse authResponse, String refreshToken) {}

    public LoginResult login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String accessToken = jwtTokenProvider.generateToken(principal);
        String refreshToken = refreshTokenService.create(principal.getUsername());

        String role = principal.getAuthorities().stream()
                .findFirst()
                .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                .orElse("USER");

        AuthResponse authResponse = new AuthResponse(principal.getId(), accessToken, "Bearer", principal.getUsername(), role);
        return new LoginResult(authResponse, refreshToken);
    }
}
