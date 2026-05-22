package com.ordersystem.config;

import io.micrometer.core.instrument.Clock;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class OtlpProdConfig {

    @Bean
    OtlpHttpSpanExporter otlpHttpSpanExporter() {
        String base = requiredEnv("OTEL_EXPORTER_OTLP_ENDPOINT");
        var builder = OtlpHttpSpanExporter.builder()
                .setEndpoint(base + "/v1/traces")
                .setCompression("gzip")
                .setTimeout(Duration.ofSeconds(10));
        parseHeaders(System.getenv("OTEL_EXPORTER_OTLP_HEADERS")).forEach(builder::addHeader);
        return builder.build();
    }

    @Bean
    OtlpMeterRegistry otlpMeterRegistry(Clock clock) {
        String base = requiredEnv("OTEL_EXPORTER_OTLP_ENDPOINT");
        Map<String, String> headers = parseHeaders(System.getenv("OTEL_EXPORTER_OTLP_HEADERS"));
        Map<String, String> resourceAttrs = buildResourceAttributes();

        OtlpConfig cfg = new OtlpConfig() {
            @Override public String url() { return base + "/v1/metrics"; }
            @Override public Map<String, String> headers() { return headers; }
            @Override public Map<String, String> resourceAttributes() { return resourceAttrs; }
            @Override public String get(String k) { return null; }
        };
        return new OtlpMeterRegistry(cfg, clock);
    }

    private static Map<String, String> parseHeaders(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return result;
        for (String pair : raw.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) result.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        return result;
    }

    private static Map<String, String> buildResourceAttributes() {
        Map<String, String> attrs = new LinkedHashMap<>();
        String serviceName = System.getenv("OTEL_SERVICE_NAME");
        if (serviceName != null && !serviceName.isBlank()) attrs.put("service.name", serviceName);
        String resourceRaw = System.getenv("OTEL_RESOURCE_ATTRIBUTES");
        if (resourceRaw != null && !resourceRaw.isBlank()) {
            for (String pair : resourceRaw.split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) attrs.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return attrs;
    }

    private static String requiredEnv(String name) {
        String val = System.getenv(name);
        if (val == null || val.isBlank()) {
            throw new IllegalStateException("Missing required env var for PROD OTLP: " + name);
        }
        return val;
    }
}
