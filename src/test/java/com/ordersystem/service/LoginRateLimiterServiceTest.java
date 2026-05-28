package com.ordersystem.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoginRateLimiterService — bucket, bloqueio e reset por IP")
@ExtendWith(MockitoExtension.class)
class LoginRateLimiterServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry meterRegistry;

    @InjectMocks
    private LoginRateLimiterService loginRateLimiterService;

    @AfterEach
    void resetState() {
        loginRateLimiterService.reset();
    }

    @Nested
    @DisplayName("isAllowed")
    class IsAllowed {

        @Test
        @DisplayName("permite primeira tentativa de IP desconhecido")
        void shouldAllowFirstRequestFromNewIp() {
            assertThat(loginRateLimiterService.isAllowed("192.168.0.1")).isTrue();
        }

        @Test
        @DisplayName("permite até 5 tentativas consecutivas antes de bloquear")
        void shouldAllowUpToFiveAttempts() {
            String ip = "10.0.0.1";

            for (int i = 0; i < 5; i++) {
                assertThat(loginRateLimiterService.isAllowed(ip))
                        .as("tentativa %d deve ser permitida", i + 1)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("bloqueia IP após esgotar o bucket de 5 tentativas")
        void shouldBlockIpAfterBucketExhausted() {
            String ip = "10.0.0.2";

            for (int i = 0; i < 5; i++) {
                loginRateLimiterService.isAllowed(ip);
            }

            assertThat(loginRateLimiterService.isAllowed(ip)).isFalse();
        }

        @Test
        @DisplayName("mantém bloqueio em tentativas subsequentes do IP bloqueado")
        void shouldContinueBlockingAfterInitialBlock() {
            String ip = "10.0.0.3";

            for (int i = 0; i < 6; i++) {
                loginRateLimiterService.isAllowed(ip);
            }

            assertThat(loginRateLimiterService.isAllowed(ip)).isFalse();
            assertThat(loginRateLimiterService.isAllowed(ip)).isFalse();
        }

        @Test
        @DisplayName("IPs diferentes têm buckets independentes")
        void shouldHaveIndependentBucketsPerIp() {
            String ip1 = "10.0.0.10";
            String ip2 = "10.0.0.11";

            for (int i = 0; i < 5; i++) {
                loginRateLimiterService.isAllowed(ip1);
            }

            assertThat(loginRateLimiterService.isAllowed(ip1)).isFalse();
            assertThat(loginRateLimiterService.isAllowed(ip2)).isTrue();
        }
    }

    @Nested
    @DisplayName("reset")
    class Reset {

        @Test
        @DisplayName("limpa todos os buckets e bloqueios — IP anteriormente bloqueado volta a ser permitido")
        void shouldClearAllStateAfterReset() {
            String ip = "10.0.0.99";

            for (int i = 0; i < 6; i++) {
                loginRateLimiterService.isAllowed(ip);
            }
            assertThat(loginRateLimiterService.isAllowed(ip)).isFalse();

            loginRateLimiterService.reset();

            assertThat(loginRateLimiterService.isAllowed(ip)).isTrue();
        }
    }
}
