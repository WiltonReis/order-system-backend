package com.ordersystem.security;

import com.ordersystem.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME = "oms.token";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = extractToken(request);
        boolean tenantSet = false;

        if (token != null) {
            try {
                String username = jwtTokenProvider.extractUsername(token);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    if (jwtTokenProvider.isTokenValid(token, userDetails)) {
                        String jti = jwtTokenProvider.extractJti(token);
                        if (tokenBlacklistService.isRevoked(jti)) {
                            chain.doFilter(request, response);
                            return;
                        }

                        if (userDetails instanceof UserPrincipal principal
                                && principal.getTokenRevokedBefore() != null) {
                            LocalDateTime issuedAt = jwtTokenProvider.extractIssuedAt(token);
                            if (issuedAt.isBefore(principal.getTokenRevokedBefore())) {
                                chain.doFilter(request, response);
                                return;
                            }
                        }

                        UUID claimTenantId = jwtTokenProvider.extractTenantId(token);
                        if (!(userDetails instanceof UserPrincipal principal)
                                || principal.getCustomerSaasId() == null
                                || claimTenantId == null
                                || !principal.getCustomerSaasId().equals(claimTenantId)) {
                            log.warn("Token JWT recusado: tenantId ausente ou divergente do usuário");
                            chain.doFilter(request, response);
                            return;
                        }

                        TenantContext.set(claimTenantId);
                        tenantSet = true;

                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (Exception e) {
                log.warn("Token JWT inválido: {}", e.getMessage());
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            if (tenantSet) {
                TenantContext.clear();
            }
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}
