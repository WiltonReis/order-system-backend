package com.ordersystem.service.impl;

import com.ordersystem.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@Profile("!prod")
public class LocalStorageService implements StorageService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    public String upload(MultipartFile file, String filename) {
        Path dest = Path.of(uploadDir, "products", filename);
        try {
            Files.createDirectories(dest.getParent());
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao salvar imagem", e);
        }
        return "/uploads/products/" + filename;
    }

    @Override
    public void delete(String imageUrl) {
        if (imageUrl == null) return;
        String prefix = "/uploads/products/";
        if (!imageUrl.startsWith(prefix)) return;
        String filename = imageUrl.substring(prefix.length());
        Path old = Path.of(uploadDir, "products", filename);
        try {
            Files.deleteIfExists(old);
        } catch (IOException ignored) {}
    }
}
