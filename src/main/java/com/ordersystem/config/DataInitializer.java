package com.ordersystem.config;

import com.ordersystem.entity.User;
import com.ordersystem.enums.Role;
import com.ordersystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${ADMIN_INITIAL_PASSWORD:}")
    private String adminInitialPassword;

    @Override
    public void run(String... args) {
        // Cria a sequence de orderCode antes de qualquer pedido ser persistido
        jdbcTemplate.execute(
                "CREATE SEQUENCE IF NOT EXISTS order_code_seq " +
                "START 1 MINVALUE 1 MAXVALUE 99999 INCREMENT 1 NO CYCLE"
        );
        log.info("Sequence order_code_seq verificada/criada");

        if (userRepository.findByUsername("admin").isPresent()) {
            return;
        }

        String password;
        if (!adminInitialPassword.isBlank()) {
            password = adminInitialPassword;
        } else {
            // Gera senha aleatória e exibe no console — deve ser trocada imediatamente
            password = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            log.warn("=================================================================");
            log.warn("AVISO DE SEGURANÇA: ADMIN_INITIAL_PASSWORD não foi definida.");
            log.warn("Senha gerada automaticamente para o admin: {}", password);
            log.warn("Troque essa senha imediatamente e defina ADMIN_INITIAL_PASSWORD");
            log.warn("=================================================================");
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        log.info("Usuário admin criado (username: admin)");
    }
}
