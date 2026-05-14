package com.ordersystem.controller;

import com.ordersystem.dto.request.ProductPriceRequest;
import com.ordersystem.dto.request.ProductRequest;
import com.ordersystem.dto.request.ProductUpdateRequest;
import com.ordersystem.dto.response.MessageResponse;
import com.ordersystem.dto.response.ProductResponse;
import com.ordersystem.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag(name = "Produtos", description = "Catálogo de produtos do tenant. Escrita restrita a ADMIN.")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_MASTER')")
    @Operation(summary = "Criar produto", description = "Cria novo produto no catálogo do tenant. Restrito a ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Produto criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    // Retorna lista completa — usada pelo OrderFormDialog para popular o dropdown de produtos
    @GetMapping("/all")
    @Operation(summary = "Listar todos os produtos", description = "Lista completa sem paginação. Usada para popular dropdowns.")
    public ResponseEntity<List<ProductResponse>> findAll() {
        return ResponseEntity.ok(productService.findAll());
    }

    // Versão paginada — usada pela tela de gerenciamento de produtos
    @GetMapping
    @Operation(summary = "Listar produtos (paginado)")
    public ResponseEntity<Page<ProductResponse>> findAllPaged(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(productService.findAllPaged(pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_MASTER')")
    @Operation(summary = "Atualizar produto", description = "Atualiza nome, descrição e preço. Restrito a ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content)
    })
    public ResponseEntity<ProductResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_MASTER')")
    @Operation(summary = "Atualizar preço do produto", description = "Atualiza somente o preço. Restrito a ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preço atualizado"),
            @ApiResponse(responseCode = "400", description = "Preço inválido", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content)
    })
    public ResponseEntity<ProductResponse> updatePrice(@PathVariable UUID id,
                                                       @Valid @RequestBody ProductPriceRequest request) {
        return ResponseEntity.ok(productService.updatePrice(id, request.getPrice()));
    }

    @PostMapping("/{id}/image")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_MASTER')")
    @Operation(summary = "Upload de imagem do produto", description = "Aceita JPG, PNG ou WEBP. Tamanho máximo: 5 MB. Restrito a ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagem enviada"),
            @ApiResponse(responseCode = "400", description = "Arquivo inválido ou muito grande", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content)
    })
    public ResponseEntity<ProductResponse> uploadImage(@PathVariable UUID id,
                                                       @RequestParam("image") MultipartFile file) {
        return ResponseEntity.ok(productService.uploadImage(id, file));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_MASTER')")
    @Operation(summary = "Excluir produto", description = "Soft-delete. Restrito a ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto excluído"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content)
    })
    public ResponseEntity<MessageResponse> delete(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.delete(id));
    }
}
