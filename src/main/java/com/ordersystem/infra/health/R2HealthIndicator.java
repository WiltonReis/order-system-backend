package com.ordersystem.infra.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@Profile("prod")
public class R2HealthIndicator implements HealthIndicator {

    private final String endpoint;
    private final HttpClient client;

    public R2HealthIndicator(@Value("${app.r2.endpoint}") String endpoint) {
        this.endpoint = endpoint;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public Health health() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(3))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            // <500 means R2 is reachable (auth errors like 403 are expected and fine)
            if (status < 500) {
                return Health.up().withDetail("endpoint", endpoint).withDetail("status", status).build();
            }
            return Health.down().withDetail("endpoint", endpoint).withDetail("status", status).build();
        } catch (Exception e) {
            return Health.down().withDetail("endpoint", endpoint).withException(e).build();
        }
    }
}
