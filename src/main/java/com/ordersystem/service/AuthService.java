package com.ordersystem.service;

import com.ordersystem.dto.request.LoginRequest;
import com.ordersystem.dto.request.RegisterRequest;
import com.ordersystem.dto.response.AuthResponse;
import com.ordersystem.dto.response.RegisterResponse;
import com.ordersystem.entity.CustomerSaas;
import com.ordersystem.entity.User;
import com.ordersystem.enums.Role;
import com.ordersystem.mapper.AuthMapper;
import com.ordersystem.validation.AuthValidator;
import com.ordersystem.repository.CustomerSaasRepository;
import com.ordersystem.repository.UserRepository;
import com.ordersystem.security.JwtTokenProvider;
import com.ordersystem.security.UserPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final CustomerSaasRepository customerSaasRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthMapper authMapper;
    private final AuthValidator authValidator;
    private final MeterRegistry meterRegistry;

    public record LoginResult(AuthResponse authResponse, String refreshToken) {}

    public LoginResult login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            meterRegistry.counter("auth.login.attempts", "outcome", "failure").increment();
            throw e;
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String accessToken = jwtTokenProvider.generateToken(principal);
        String refreshToken = refreshTokenService.create(principal.getId());

        String role = principal.getAuthorities().stream()
                .findFirst()
                .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                .orElse("USER");

        AuthResponse authResponse = new AuthResponse(
                principal.getId(),
                accessToken,
                "Bearer",
                principal.getEmail(),
                principal.getName(),
                role,
                principal.getCustomerSaasId()
        );
        meterRegistry.counter("auth.login.attempts", "outcome", "success").increment();
        return new LoginResult(authResponse, refreshToken);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedCpfCnpj = request.getCpfCnpj() == null
                ? null
                : request.getCpfCnpj().replaceAll("\\D", "");

        authValidator.validateCpfCnpjNotBlank(normalizedCpfCnpj);
        authValidator.validateEmailNotRegistered(request.getEmail());
        authValidator.validateCpfCnpjNotRegistered(normalizedCpfCnpj);

        CustomerSaas tenant = new CustomerSaas();
        tenant.setCompanyName(request.getCompanyName());
        tenant.setCpfCnpj(normalizedCpfCnpj);
        tenant.setContactEmail(request.getEmail());
        tenant = customerSaasRepository.save(tenant);

        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.ADMIN_MASTER);
        user.setCustomerSaas(tenant);
        user = userRepository.save(user);

        return authMapper.toRegisterResponse(user, tenant);
    }
}
