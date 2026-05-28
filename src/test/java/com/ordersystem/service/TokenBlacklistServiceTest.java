package com.ordersystem.service;

import com.ordersystem.entity.RevokedToken;
import com.ordersystem.repository.RevokedTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("TokenBlacklistService — revogação e verificação de tokens por jti")
@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    @Nested
    @DisplayName("revoke")
    class Revoke {

        @Test
        @DisplayName("persiste token revogado quando jti ainda não está na blacklist")
        void shouldSaveRevokedTokenWhenJtiIsNew() {
            String jti = "jti-abc";
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
            when(revokedTokenRepository.existsByJti(jti)).thenReturn(false);

            tokenBlacklistService.revoke(jti, expiresAt);

            verify(revokedTokenRepository).save(argThat(t ->
                    jti.equals(t.getJti()) && expiresAt.equals(t.getExpiresAt())
            ));
        }

        @Test
        @DisplayName("idempotente — não persiste de novo quando jti já está revogado")
        void shouldSkipSaveWhenJtiAlreadyRevoked() {
            String jti = "jti-duplicate";
            when(revokedTokenRepository.existsByJti(jti)).thenReturn(true);

            tokenBlacklistService.revoke(jti, LocalDateTime.now().plusHours(1));

            verify(revokedTokenRepository, never()).save(any(RevokedToken.class));
        }
    }

    @Nested
    @DisplayName("isRevoked")
    class IsRevoked {

        @Test
        @DisplayName("retorna true quando jti está na blacklist")
        void shouldReturnTrueForRevokedJti() {
            when(revokedTokenRepository.existsByJti("revoked-jti")).thenReturn(true);

            assertThat(tokenBlacklistService.isRevoked("revoked-jti")).isTrue();
        }

        @Test
        @DisplayName("retorna false quando jti não está na blacklist")
        void shouldReturnFalseForActiveJti() {
            when(revokedTokenRepository.existsByJti("active-jti")).thenReturn(false);

            assertThat(tokenBlacklistService.isRevoked("active-jti")).isFalse();
        }
    }

    @Nested
    @DisplayName("cleanupExpired")
    class CleanupExpired {

        @Test
        @DisplayName("delega limpeza de tokens expirados ao repositório")
        void shouldDelegateCleanupToRepository() {
            tokenBlacklistService.cleanupExpired();

            verify(revokedTokenRepository).deleteAllByExpiresAtBefore(any(LocalDateTime.class));
        }
    }
}
