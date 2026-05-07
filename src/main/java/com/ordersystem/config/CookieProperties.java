package com.ordersystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.cookie")
public record CookieProperties(boolean secure) {}
