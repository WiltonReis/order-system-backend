package com.ordersystem.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class CookieSecurityValidator {

    private final CookieProperties cookieProperties;

    @PostConstruct
    void validate() {
        if (!cookieProperties.secure()) {
            throw new IllegalStateException(
                "Configuração inválida: COOKIE_SECURE deve ser 'true' em ambiente de produção. " +
                "Defina a variável de ambiente COOKIE_SECURE=true.");
        }
    }
}
