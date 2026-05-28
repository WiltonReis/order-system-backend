package com.ordersystem.service;

import com.ordersystem.config.JwtProperties;
import com.ordersystem.entity.RefreshToken;
import com.ordersystem.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("RefreshTokenService — emissão, validação, rotação e expiração")
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    // 30 dias em ms
    private static final long REFRESH_EXPIRATION_MS = 2_592_000_000L;
    private static final JwtProperties JWT_PROPS = new JwtProperties("secret", 60_000L, REFRESH_EXPIRATION_MS);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(JWT_PROPS, refreshTokenRepository);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("remove token anterior do usuário antes de criar novo")
        void shouldDeletePreviousTokenBeforeCreating() {
            UUID userId = UUID.randomUUID();
            RefreshToken saved = new RefreshToken();
            saved.setToken(UUID.randomUUID().toString());
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(saved);

            refreshTokenService.create(userId);

            verify(refreshTokenRepository).deleteByUserId(userId);
        }

        @Test
        @DisplayName("persiste novo RefreshToken e retorna o valor do token")
        void shouldSaveAndReturnToken() {
            UUID userId = UUID.randomUUID();
            String expectedToken = UUID.randomUUID().toString();
            RefreshToken saved = new RefreshToken();
            saved.setToken(expectedToken);
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(saved);

            String result = refreshTokenService.create(userId);

            assertThat(result).isEqualTo(expectedToken);
        }

        @Test
        @DisplayName("define expiresAt em ~30 dias a partir da criação")
        void shouldSetExpiresAtThirtyDaysFromNow() {
            UUID userId = UUID.randomUUID();
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

            refreshTokenService.create(userId);

            verify(refreshTokenRepository).save(argThat(t -> {
                LocalDateTime expectedExpiry = LocalDateTime.now().plusSeconds(REFRESH_EXPIRATION_MS / 1000);
                return t.getExpiresAt().isAfter(expectedExpiry.minusSeconds(5))
                        && t.getExpiresAt().isBefore(expectedExpiry.plusSeconds(5));
            }));
        }
    }

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("retorna userId para token válido e não expirado")
        void shouldReturnUserIdForValidToken() {
            UUID userId = UUID.randomUUID();
            RefreshToken token = new RefreshToken();
            token.setToken("valid-token");
            token.setUserId(userId);
            token.setExpiresAt(LocalDateTime.now().plusDays(29));

            when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

            UUID result = refreshTokenService.validate("valid-token");

            assertThat(result).isEqualTo(userId);
        }

        @Test
        @DisplayName("lança BadCredentialsException para token não encontrado")
        void shouldThrowForTokenNotFound() {
            when(refreshTokenRepository.findByToken("ghost-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.validate("ghost-token"))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("inválido");
        }

        @Test
        @DisplayName("lança BadCredentialsException e exclui token quando expirado")
        void shouldThrowAndDeleteExpiredToken() {
            RefreshToken token = new RefreshToken();
            token.setToken("expired-token");
            token.setUserId(UUID.randomUUID());
            token.setExpiresAt(LocalDateTime.now().minusDays(1));

            when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> refreshTokenService.validate("expired-token"))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("expirado");

            verify(refreshTokenRepository).delete(token);
        }
    }

    @Nested
    @DisplayName("delete / deleteByUserId")
    class Delete {

        @Test
        @DisplayName("deleta token pelo valor")
        void shouldDeleteTokenByValue() {
            refreshTokenService.delete("some-token");

            verify(refreshTokenRepository).deleteByToken("some-token");
        }

        @Test
        @DisplayName("deleta token pelo userId")
        void shouldDeleteTokenByUserId() {
            UUID userId = UUID.randomUUID();
            refreshTokenService.deleteByUserId(userId);

            verify(refreshTokenRepository).deleteByUserId(userId);
        }
    }

    @Nested
    @DisplayName("cleanupExpired")
    class CleanupExpired {

        @Test
        @DisplayName("delega limpeza de tokens expirados ao repositório")
        void shouldDelegateCleanupToRepository() {
            refreshTokenService.cleanupExpired();

            verify(refreshTokenRepository).deleteAllByExpiresAtBefore(any(LocalDateTime.class));
        }
    }
}
