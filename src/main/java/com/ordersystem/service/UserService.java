package com.ordersystem.service;

import com.ordersystem.dto.request.UserRequest;
import com.ordersystem.dto.request.UserUpdateRequest;
import com.ordersystem.dto.response.MessageResponse;
import com.ordersystem.dto.response.UserResponse;
import com.ordersystem.entity.CustomerSaas;
import com.ordersystem.entity.User;
import com.ordersystem.enums.Role;
import com.ordersystem.exception.ResourceNotFoundException;
import com.ordersystem.mapper.UserMapper;
import com.ordersystem.repository.CustomerSaasRepository;
import com.ordersystem.repository.UserRepository;
import com.ordersystem.security.TenantContext;
import com.ordersystem.validation.UserValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CustomerSaasRepository customerSaasRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserValidator userValidator;

    @Transactional
    public UserResponse create(UserRequest request) {
        userValidator.validateEmailNotTaken(request.getEmail());

        UUID tenantId = TenantContext.getOrThrow();
        CustomerSaas tenant = customerSaasRepository.getReferenceById(tenantId);

        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setCustomerSaas(tenant);

        User saved = userRepository.save(user);
        return userMapper.toUserResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toUserResponse);
    }

    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        userValidator.validateNotAdminMaster(user.getRole());
        userValidator.validateEmailNotTakenByAnother(user.getEmail(), request.getEmail());

        user.setEmail(request.getEmail());
        user.setName(request.getName());
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setRole(request.getRole());

        User saved = userRepository.save(user);
        return userMapper.toUserResponse(saved);
    }

    @Transactional
    public UserResponse updateRole(UUID id, Role role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        userValidator.validateNotAdminMaster(user.getRole());

        user.setRole(role);
        user.setTokenRevokedBefore(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1));
        User saved = userRepository.save(user);
        return userMapper.toUserResponse(saved);
    }

    @Transactional
    public MessageResponse delete(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        userValidator.validateNotAdminMaster(user.getRole());

        userRepository.delete(user);
        return new MessageResponse("Usuário excluído com sucesso");
    }
}
