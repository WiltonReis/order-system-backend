package com.ordersystem.service;

import com.ordersystem.dto.request.ProductRequest;
import com.ordersystem.dto.request.ProductUpdateRequest;
import com.ordersystem.dto.response.MessageResponse;
import com.ordersystem.dto.response.ProductResponse;
import com.ordersystem.entity.Product;
import com.ordersystem.exception.ResourceNotFoundException;
import com.ordersystem.mapper.ProductMapper;
import com.ordersystem.repository.CustomerSaasRepository;
import com.ordersystem.repository.ProductRepository;
import com.ordersystem.security.AuthenticatedUserProvider;
import com.ordersystem.security.TenantContext;
import com.ordersystem.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
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
    private final StorageService storageService;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final ProductMapper productMapper;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "products-paged", allEntries = true)
    })
    public ProductResponse create(ProductRequest request) {
        UserPrincipal principal = authenticatedUserProvider.getPrincipal();

        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCreatedByName(principal.getName());
        product.setCustomerSaas(customerSaasRepository.getReferenceById(TenantContext.getOrThrow()));

        Product saved = productRepository.save(product);
        return productMapper.toProductResponse(saved);
    }

    @Cacheable(value = "products", key = "T(com.ordersystem.security.TenantContext).get().toString()")
    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream()
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "products-paged", key = "T(com.ordersystem.security.TenantContext).get().toString() + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<ProductResponse> findAllPaged(Pageable pageable) {
        return productRepository.findAll(pageable).map(productMapper::toProductResponse);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "products-paged", allEntries = true)
    })
    public ProductResponse update(UUID id, ProductUpdateRequest request) {
        Product product = productRepository.findByIdFiltered(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());

        Product saved = productRepository.save(product);
        return productMapper.toProductResponse(saved);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "products-paged", allEntries = true)
    })
    public ProductResponse updatePrice(UUID id, BigDecimal price) {
        Product product = productRepository.findByIdFiltered(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setPrice(price);
        Product saved = productRepository.save(product);
        return productMapper.toProductResponse(saved);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "products-paged", allEntries = true)
    })
    public MessageResponse delete(UUID id) {
        productRepository.findByIdFiltered(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
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

        Product product = productRepository.findByIdFiltered(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        storageService.delete(product.getImageUrl());

        String ext = getExtension(file.getContentType());
        String filename = UUID.randomUUID() + ext;
        String imageUrl = storageService.upload(file, filename);

        product.setImageUrl(imageUrl);
        return productMapper.toProductResponse(productRepository.save(product));
    }

    private String getExtension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

}
