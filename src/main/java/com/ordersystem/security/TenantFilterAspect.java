package com.ordersystem.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* org.springframework.data.repository.Repository+.*(..))")
    public void enableTenantFilter() {
        UUID tenantId = TenantContext.get();
        if (tenantId == null) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.getEnabledFilter("tenantFilter");
        if (filter == null) {
            filter = session.enableFilter("tenantFilter");
        }
        filter.setParameter("tenantId", tenantId);
    }
}
