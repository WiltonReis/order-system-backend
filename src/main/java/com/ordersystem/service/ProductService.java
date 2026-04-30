package com.ordersystem.service;

import com.ordersystem.dto.request.ProductRequest;
import com.ordersystem.dto.request.ProductUpdateRequest;
import com.ordersystem.dto.response.MessageResponse;
import com.ordersystem.dto.response.ProductResponse;
import com.ordersystem.entity.Product;
import com.ordersystem.exception.ResourceNotFoundException;
import com.ordersystem.repository.CustomerSaasRepository;
import com.ordersystem.repository.ProductRepository;
import com.ordersystem.security.TenantContext;
import com.ordersystem.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    private final ProductRepository productRepository;
    private final CustomerSaasRepository customerSaasRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "products-paged", allEntries = true)
    })
    public ProductResponse create(ProductRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCreatedByName(principal.getName());
        product.setCustomerSaas(customerSaasRepository.getReferenceById(TenantContext.getOrThrow()));

        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    @Cacheable(value = "products", key = "T(com.ordersystem.security.TenantContext).get().toString()")
    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "products-paged", key = "T(com.ordersystem.security.TenantContext).get().toString() + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<ProductResponse> findAllPaged(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "products-paged", allEntries = true)
    })
    public ProductResponse update(UUID id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());

        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "products-paged", allEntries = true)
    })
    public ProductResponse updatePrice(UUID id, BigDecimal price) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setPrice(price);
        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "products-paged", allEntries = true)
    })
    public MessageResponse delete(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product", id);
        }
        productRepository.deleteById(id);
        return new MessageResponse("Product deleted successfully");
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "products-paged", allEntries = true)
    })
    public ProductResponse uploadImage(UUID id, MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("Arquivo vazio");
        if (!ALLOWED_TYPES.contains(file.getContentType()))
            throw new IllegalArgumentException("Tipo não permitido. Use JPG, PNG ou WEBP");
        if (file.getSize() > MAX_SIZE)
            throw new IllegalArgumentException("Arquivo excede 5MB");

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        deleteOldImage(product.getImageUrl());

        String ext = getExtension(file.getContentType());
        String filename = UUID.randomUUID() + ext;

        Path dest = Path.of(uploadDir, "products", filename);
        try {
            Files.createDirectories(dest.getParent());
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao salvar imagem", e);
        }

        product.setImageUrl("/uploads/products/" + filename);
        return toResponse(productRepository.save(product));
    }

    private void deleteOldImage(String imageUrl) {
        if (imageUrl == null) return;
        String prefix = "/uploads/products/";
        if (!imageUrl.startsWith(prefix)) return;
        String filename = imageUrl.substring(prefix.length());
        Path old = Path.of(uploadDir, "products", filename);
        try { Files.deleteIfExists(old); } catch (IOException ignored) {}
    }

    private String getExtension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getImageUrl()
        );
    }
}
