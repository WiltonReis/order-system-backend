package com.ordersystem.validation;

import com.ordersystem.exception.BusinessException;
import com.ordersystem.exception.ConflictException;
import com.ordersystem.repository.CustomerSaasRepository;
import com.ordersystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthValidator {

    private final UserRepository userRepository;
    private final CustomerSaasRepository customerSaasRepository;

    public void validateCpfCnpjNotBlank(String normalizedCpfCnpj) {
        if (normalizedCpfCnpj == null || normalizedCpfCnpj.isBlank()) {
            throw new BusinessException("CPF/CNPJ é obrigatório");
        }
    }

    public void validateEmailNotRegistered(String email) {
        if (userRepository.existsByEmailGlobal(email)) {
            throw new ConflictException("E-mail já cadastrado");
        }
    }

    public void validateCpfCnpjNotRegistered(String normalizedCpfCnpj) {
        if (customerSaasRepository.existsByCpfCnpj(normalizedCpfCnpj)) {
            throw new ConflictException("CPF/CNPJ já cadastrado");
        }
    }
}
