package com.ordersystem.controller;

import com.ordersystem.dto.request.ProductPriceRequest;
import com.ordersystem.dto.request.ProductRequest;
import com.ordersystem.dto.request.ProductUpdateRequest;
import com.ordersystem.dto.response.MessageResponse;
import com.ordersystem.dto.response.ProductResponse;
import com.ordersystem.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    // Retorna lista completa — usada pelo OrderFormDialog para popular o dropdown de produtos
    @GetMapping("/all")
    public ResponseEntity<List<ProductResponse>> findAll() {
        return ResponseEntity.ok(productService.findAll());
    }

    // Versão paginada — usada pela tela de gerenciamento de produtos
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> findAllPaged(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(productService.findAllPaged(pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProductResponse> updatePrice(@PathVariable UUID id,
                                                       @Valid @RequestBody ProductPriceRequest request) {
        return ResponseEntity.ok(productService.updatePrice(id, request.getPrice()));
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<ProductResponse> uploadImage(@PathVariable UUID id,
                                                       @RequestParam("image") MultipartFile file) {
        return ResponseEntity.ok(productService.uploadImage(id, file));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.delete(id));
    }
}
