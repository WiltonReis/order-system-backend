package com.ordersystem.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String upload(MultipartFile file, String filename);
    void delete(String imageUrl);
}
