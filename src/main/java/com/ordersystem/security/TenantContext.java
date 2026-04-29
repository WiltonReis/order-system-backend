package com.ordersystem.security;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID get() {
        return CURRENT_TENANT.get();
    }

    public static UUID getOrThrow() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context not set for the current thread");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
