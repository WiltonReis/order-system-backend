package com.ordersystem.validation;

import br.com.caelum.stella.validation.CNPJValidator;
import br.com.caelum.stella.validation.CPFValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfCnpjValidator implements ConstraintValidator<CpfCnpj, String> {

    private static final CPFValidator CPF_VALIDATOR = new CPFValidator();
    private static final CNPJValidator CNPJ_VALIDATOR = new CNPJValidator();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Presença é responsabilidade de @NotBlank — null/blank passa aqui.
        if (value == null || value.isBlank()) {
            return true;
        }

        // Normaliza: remove qualquer caractere que não seja dígito.
        String normalized = value.replaceAll("\\D", "");

        return switch (normalized.length()) {
            case 11 -> CPF_VALIDATOR.invalidMessagesFor(normalized).isEmpty();
            case 14 -> CNPJ_VALIDATOR.invalidMessagesFor(normalized).isEmpty();
            default -> false;
        };
    }
}
