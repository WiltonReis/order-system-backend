package com.ordersystem.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfCnpjValidator implements ConstraintValidator<CpfCnpj, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Presença é responsabilidade de @NotBlank — null/blank passa aqui.
        if (value == null || value.isBlank()) {
            return true;
        }

        // Normaliza: remove qualquer caractere que não seja dígito.
        String normalized = value.replaceAll("\\D", "");

        // Valida só o formato (11 dígitos = CPF, 14 = CNPJ), sem checar dígito
        // verificador. Mantém o cadastro fácil pra quem está testando o projeto.
        return normalized.length() == 11 || normalized.length() == 14;
    }
}
