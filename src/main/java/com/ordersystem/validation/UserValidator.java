package com.ordersystem.validation;

import com.ordersystem.enums.Role;
import com.ordersystem.exception.ConflictException;
import com.ordersystem.exception.ForbiddenOperationException;
import com.ordersystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserValidator {

    private final UserRepository userRepository;

    public void validateEmailNotTaken(String email) {
        if (userRepository.existsByEmailGlobal(email)) {
            throw new ConflictException("E-mail já cadastrado");
        }
    }

    public void validateEmailNotTakenByAnother(String currentEmail, String newEmail) {
        if (!currentEmail.equals(newEmail) && userRepository.existsByEmailGlobal(newEmail)) {
            throw new ConflictException("E-mail já cadastrado");
        }
    }

    public void validateNotAdminMaster(Role role) {
        if (role == Role.ADMIN_MASTER) {
            throw new ForbiddenOperationException("Administrador master não pode ser modificado");
        }
    }
}
