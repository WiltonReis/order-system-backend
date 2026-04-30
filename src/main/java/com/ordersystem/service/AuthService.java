package com.ordersystem.service;

import com.ordersystem.dto.request.LoginRequest;
import com.ordersystem.dto.request.RegisterRequest;
import com.ordersystem.dto.response.AuthResponse;
import com.ordersystem.dto.response.RegisterResponse;
import com.ordersystem.entity.CustomerSaas;
import com.ordersystem.entity.User;
import com.ordersystem.enums.Role;
import com.ordersystem.exception.BusinessException;
import com.ordersystem.repository.CustomerSaasRepository;
import com.ordersystem.repository.UserRepository;
import com.ordersystem.security.JwtTokenProvider;
import com.ordersystem.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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

    public record LoginResult(AuthResponse authResponse, String refreshToken) {}

    public LoginResult login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

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
        return new LoginResult(authResponse, refreshToken);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedCpfCnpj = request.getCpfCnpj() == null
                ? null
                : request.getCpfCnpj().replaceAll("\\D", "");

        if (normalizedCpfCnpj == null || normalizedCpfCnpj.isBlank()) {
            throw new BusinessException("CPF/CNPJ é obrigatório.");
        }

        if (userRepository.existsByEmailGlobal(request.getEmail())) {
            throw new BusinessException("E-mail já cadastrado.");
        }

        if (customerSaasRepository.existsByCpfCnpj(normalizedCpfCnpj)) {
            throw new BusinessException("CPF/CNPJ já cadastrado.");
        }

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

        return new RegisterResponse(user.getId(), user.getEmail(), user.getName(), tenant.getId());
    }
}
