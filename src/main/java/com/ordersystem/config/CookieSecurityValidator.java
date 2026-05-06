package com.ordersystem.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class CookieSecurityValidator {

    @Value("${COOKIE_SECURE:false}")
    private boolean cookieSecure;

    @PostConstruct
    void validate() {
        if (!cookieSecure) {
            throw new IllegalStateException(
                "Configuração inválida: COOKIE_SECURE deve ser 'true' em ambiente de produção. " +
                "Defina a variável de ambiente COOKIE_SECURE=true.");
        }
    }
}
