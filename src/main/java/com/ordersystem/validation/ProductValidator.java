package com.ordersystem.validation;

import com.ordersystem.exception.InvalidFileException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Component
public class ProductValidator {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    public void validateImageFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidFileException("O arquivo está vazio");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new InvalidFileException("Tipo de arquivo não permitido. Use JPG, PNG ou WEBP");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new InvalidFileException("O arquivo excede o tamanho máximo de 5MB");
        }
    }
}
