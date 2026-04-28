package com.ordersystem.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LoginRateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> blockedUntil = new ConcurrentHashMap<>();

    public boolean isAllowed(String ip) {
        Instant blockExpiry = blockedUntil.get(ip);
        if (blockExpiry != null) {
            if (Instant.now().isBefore(blockExpiry)) {
                log.warn("IP bloqueado por excesso de tentativas de login: {}", ip);
                return false;
            }
            blockedUntil.remove(ip);
            buckets.remove(ip);
        }

        Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            blockedUntil.put(ip, Instant.now().plus(BLOCK_DURATION));
            log.warn("IP {} excedeu o limite de tentativas de login. Bloqueado por {} minutos.",
                    ip, BLOCK_DURATION.toMinutes());
        }

        return allowed;
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(MAX_ATTEMPTS)
                .refillGreedy(MAX_ATTEMPTS, WINDOW)
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Scheduled(fixedDelay = 1800000)
    void cleanupExpiredBlocks() {
        Instant now = Instant.now();
        blockedUntil.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
        log.debug("Limpeza de bloqueios de IP expirados concluída");
    }
}
