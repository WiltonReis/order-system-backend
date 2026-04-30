package com.ordersystem.config;

import com.ordersystem.entity.CustomerSaas;
import com.ordersystem.entity.User;
import com.ordersystem.enums.Role;
import com.ordersystem.repository.CustomerSaasRepository;
import com.ordersystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CustomerSaasRepository customerSaasRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_INITIAL_PASSWORD:}")
    private String adminInitialPassword;

    private static final String DEMO_EMAIL = "admin@demo.local";
    private static final String DEMO_CPFCNPJ = "00000000000000";

    @Override
    public void run(String... args) {
        if (userRepository.findByEmail(DEMO_EMAIL).isPresent()) {
            return;
        }

        String password;
        if (!adminInitialPassword.isBlank()) {
            password = adminInitialPassword;
        } else {
            password = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            log.warn("=================================================================");
            log.warn("AVISO DE SEGURANÇA: ADMIN_INITIAL_PASSWORD não foi definida.");
            log.warn("Senha gerada automaticamente para o admin: {}", password);
            log.warn("Troque essa senha imediatamente e defina ADMIN_INITIAL_PASSWORD");
            log.warn("=================================================================");
        }

        CustomerSaas tenant = customerSaasRepository.findByCpfCnpj(DEMO_CPFCNPJ).orElseGet(() -> {
            CustomerSaas demo = new CustomerSaas();
            demo.setCompanyName("Demo");
            demo.setCpfCnpj(DEMO_CPFCNPJ);
            demo.setContactEmail(DEMO_EMAIL);
            return customerSaasRepository.save(demo);
        });

        User admin = new User();
        admin.setEmail(DEMO_EMAIL);
        admin.setName("Admin");
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN_MASTER);
        admin.setCustomerSaas(tenant);
        userRepository.save(admin);

        log.info("Tenant Demo e usuário ADMIN_MASTER criados (email: {})", DEMO_EMAIL);
    }
}
