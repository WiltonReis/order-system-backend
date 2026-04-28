package com.ordersystem.service;

import com.ordersystem.entity.RevokedToken;
import com.ordersystem.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RevokedTokenRepository revokedTokenRepository;

    @Transactional
    public void revoke(String jti, LocalDateTime expiresAt) {
        if (revokedTokenRepository.existsByJti(jti)) {
            return;
        }
        RevokedToken revoked = new RevokedToken();
        revoked.setJti(jti);
        revoked.setRevokedAt(LocalDateTime.now());
        revoked.setExpiresAt(expiresAt);
        revokedTokenRepository.save(revoked);
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        return revokedTokenRepository.existsByJti(jti);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpired() {
        revokedTokenRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
        log.debug("Limpeza de tokens revogados expirados concluída");
    }
}
