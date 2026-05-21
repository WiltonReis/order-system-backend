package com.ordersystem.infra;

import com.ordersystem.security.TenantContext;
import com.ordersystem.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String requestId = UUID.randomUUID().toString();
            MDC.put("requestId", requestId);
            MDC.put("requestIdShort", requestId.substring(0, 8));

            String xRequestId = request.getHeader("X-Request-ID");
            if (xRequestId != null && !xRequestId.isBlank()) {
                MDC.put("clientRequestId", xRequestId);
            }

            MDC.put("http.method", request.getMethod());
            MDC.put("http.path", request.getRequestURI());
            MDC.put("clientIp", resolveClientIp(request));

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
                MDC.put("userId", String.valueOf(principal.getId()));
            }

            UUID tenantId = TenantContext.get();
            if (tenantId != null) {
                MDC.put("tenantId", tenantId.toString());
            }

            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
