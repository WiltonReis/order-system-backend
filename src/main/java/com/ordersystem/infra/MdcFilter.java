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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            MDC.put("requestId", UUID.randomUUID().toString());

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
}
