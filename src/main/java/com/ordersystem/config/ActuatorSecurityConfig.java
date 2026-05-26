package com.ordersystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Order(1)
public class ActuatorSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(
            HttpSecurity http,
            InMemoryUserDetailsManager metricsUserDetailsManager) throws Exception {

        http
            .securityMatcher("/actuator/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/health").permitAll();
                auth.requestMatchers("/actuator/prometheus").hasRole("METRICS");
                auth.anyRequest().hasRole("METRICS");
            })
            .httpBasic(org.springframework.security.config.Customizer.withDefaults())
            .userDetailsService(metricsUserDetailsManager);

        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager metricsUserDetailsManager(
            @Value("${app.metrics.username}") String username,
            @Value("${app.metrics.password}") String password) {

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserDetails user = User.builder()
            .username(username)
            .password(encoder.encode(password))
            .roles("METRICS")
            .build();
        return new InMemoryUserDetailsManager(user);
    }
}
