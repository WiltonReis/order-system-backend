package com.ordersystem.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida o formato de CPF (11 dígitos) ou CNPJ (14 dígitos). Aceita entrada com
 * ou sem máscara (pontos, traços, barras). Não checa o dígito verificador, pra
 * facilitar o cadastro de quem está testando o projeto.
 *
 * Não exige presença — combinar com {@link jakarta.validation.constraints.NotBlank}
 * quando o campo for obrigatório. Valor null ou em branco é considerado válido aqui.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CpfCnpjValidator.class)
public @interface CpfCnpj {

    String message() default "CPF ou CNPJ inválido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
