package com.ordersystem.service;

import com.ordersystem.dto.request.ProductRequest;
import com.ordersystem.dto.request.ProductUpdateRequest;
import com.ordersystem.dto.response.MessageResponse;
import com.ordersystem.dto.response.ProductResponse;
import com.ordersystem.entity.Product;
import com.ordersystem.exception.ResourceNotFoundException;
import com.ordersystem.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUpSecurityContext() {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("admin");
        SecurityContext context = mock(SecurityContext.class);
        lenient().when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Product buildProduct(UUID id, String name, BigDecimal price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setDescription("desc");
        product.setPrice(price);
        product.setCreatedByName("admin");
        return product;
    }

    // --- create ---

    @Test
    void shouldCreateProductSuccessfully() {
        // Given
        UUID id = UUID.randomUUID();
        ProductRequest request = new ProductRequest();
        request.setName("Widget");
        request.setDescription("A widget");
        request.setPrice(new BigDecimal("10.00"));
        when(productRepository.save(any(Product.class))).thenReturn(buildProduct(id, "Widget", new BigDecimal("10.00")));

        // When
        ProductResponse response = productService.create(request);

        // Then
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getName()).isEqualTo("Widget");
        assertThat(response.getPrice()).isEqualByComparingTo("10.00");
        assertThat(response.getDescription()).isEqualTo("desc");
    }

    @Test
    void shouldSetCreatedByUsernameFromSecurityContext() {
        // Given
        ProductRequest request = new ProductRequest();
        request.setName("Gadget");
        request.setPrice(new BigDecimal("5.00"));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        productService.create(request);

        // Then
        verify(productRepository).save(argThat(p -> "admin".equals(p.getCreatedByName())));
    }

    @Test
    void shouldMapAllFieldsOnCreate() {
        // Given
        ProductRequest request = new ProductRequest();
        request.setName("Tool");
        request.setDescription("A tool");
        request.setPrice(new BigDecimal("99.99"));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        productService.create(request);

        // Then
        verify(productRepository).save(argThat(p ->
                "Tool".equals(p.getName())
                        && "A tool".equals(p.getDescription())
                        && new BigDecimal("99.99").compareTo(p.getPrice()) == 0
        ));
    }

    // --- findAll ---

    @Test
    void shouldReturnAllProducts() {
        // Given
        UUID id = UUID.randomUUID();
        when(productRepository.findAll()).thenReturn(List.of(buildProduct(id, "Widget", new BigDecimal("5.00"))));

        // When
        List<ProductResponse> result = productService.findAll();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Widget");
        assertThat(result.get(0).getId()).isEqualTo(id);
    }

    @Test
    void shouldReturnEmptyListWhenNoProductsExist() {
        // Given
        when(productRepository.findAll()).thenReturn(List.of());

        // When
        List<ProductResponse> result = productService.findAll();

        // Then
        assertThat(result).isEmpty();
    }

    // --- findAllPaged ---

    @Test
    void shouldReturnPagedProducts() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        UUID id = UUID.randomUUID();
        Page<Product> page = new PageImpl<>(List.of(buildProduct(id, "Gadget", new BigDecimal("20.00"))), pageable, 1);
        when(productRepository.findAll(pageable)).thenReturn(page);

        // When
        Page<ProductResponse> result = productService.findAllPaged(pageable);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Gadget");
    }

    @Test
    void shouldReturnEmptyPageWhenNoProductsExistPaged() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // When
        Page<ProductResponse> result = productService.findAllPaged(pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
    }

    // --- update ---

    @Test
    void shouldUpdateProductSuccessfully() {
        // Given
        UUID id = UUID.randomUUID();
        Product existing = buildProduct(id, "Old Name", new BigDecimal("10.00"));
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName("New Name");
        request.setDescription("New desc");
        request.setPrice(new BigDecimal("15.00"));

        when(productRepository.findById(id)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        // When
        ProductResponse response = productService.update(id, request);

        // Then
        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getPrice()).isEqualByComparingTo("15.00");
    }

    @Test
    void shouldUpdateAllFieldsOnProduct() {
        // Given
        UUID id = UUID.randomUUID();
        Product existing = buildProduct(id, "Old", new BigDecimal("1.00"));
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName("New");
        request.setDescription("New desc");
        request.setPrice(new BigDecimal("2.00"));

        when(productRepository.findById(id)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        // When
        productService.update(id, request);

        // Then
        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getDescription()).isEqualTo("New desc");
        assertThat(existing.getPrice()).isEqualByComparingTo("2.00");
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentProduct() {
        // Given
        UUID id = UUID.randomUUID();
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName("X");
        request.setPrice(new BigDecimal("1.00"));
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> productService.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    // --- updatePrice ---

    @Test
    void shouldUpdatePriceSuccessfully() {
        // Given
        UUID id = UUID.randomUUID();
        Product existing = buildProduct(id, "Widget", new BigDecimal("10.00"));
        when(productRepository.findById(id)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        // When
        ProductResponse response = productService.updatePrice(id, new BigDecimal("25.00"));

        // Then
        assertThat(response.getPrice()).isEqualByComparingTo("25.00");
        assertThat(existing.getPrice()).isEqualByComparingTo("25.00");
    }

    @Test
    void shouldThrowWhenUpdatingPriceOfNonExistentProduct() {
        // Given
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> productService.updatePrice(id, new BigDecimal("5.00")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- delete ---

    @Test
    void shouldDeleteProductSuccessfully() {
        // Given
        UUID id = UUID.randomUUID();
        when(productRepository.existsById(id)).thenReturn(true);

        // When
        MessageResponse response = productService.delete(id);

        // Then
        assertThat(response.getMessage()).isEqualTo("Product deleted successfully");
        verify(productRepository).deleteById(id);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentProduct() {
        // Given
        UUID id = UUID.randomUUID();
        when(productRepository.existsById(id)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> productService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).deleteById(any());
    }
}
