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
import org.junit.jupiter.api.AfterEach;
import org.mapstruct.factory.Mappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

@DisplayName("ProductService — criação, consulta, atualização e exclusão de produtos")
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerSaasRepository customerSaasRepository;

    @Spy
    private AuthenticatedUserProvider authenticatedUserProvider = new AuthenticatedUserProvider();

    @Spy
    private ProductMapper productMapper = Mappers.getMapper(ProductMapper.class);

    @InjectMocks
    private ProductService productService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        TenantContext.set(TENANT_ID);
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(), "admin@test.com", "admin", null, "pass", null, List.of()
        );
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("admin@test.com");
        lenient().when(auth.getPrincipal()).thenReturn(principal);
        SecurityContext context = mock(SecurityContext.class);
        lenient().when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
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
    @DisplayName("cria produto com sucesso e retorna response")
    void shouldCreateProductSuccessfully() {
        UUID id = UUID.randomUUID();
        ProductRequest request = new ProductRequest();
        request.setName("Widget");
        request.setDescription("A widget");
        request.setPrice(new BigDecimal("10.00"));
        when(productRepository.save(any(Product.class))).thenReturn(buildProduct(id, "Widget", new BigDecimal("10.00")));

        ProductResponse response = productService.create(request);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getName()).isEqualTo("Widget");
        assertThat(response.getPrice()).isEqualByComparingTo("10.00");
        assertThat(response.getDescription()).isEqualTo("desc");
    }

    @Test
    @DisplayName("define createdByName a partir do SecurityContext")
    void shouldSetCreatedByNameFromSecurityContext() {
        ProductRequest request = new ProductRequest();
        request.setName("Gadget");
        request.setPrice(new BigDecimal("5.00"));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.create(request);

        verify(productRepository).save(argThat(p -> "admin".equals(p.getCreatedByName())));
    }

    @Test
    @DisplayName("mapeia todos os campos na criação do produto")
    void shouldMapAllFieldsOnCreate() {
        ProductRequest request = new ProductRequest();
        request.setName("Tool");
        request.setDescription("A tool");
        request.setPrice(new BigDecimal("99.99"));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.create(request);

        verify(productRepository).save(argThat(p ->
                "Tool".equals(p.getName())
                        && "A tool".equals(p.getDescription())
                        && new BigDecimal("99.99").compareTo(p.getPrice()) == 0
        ));
    }

    // --- findAll ---

    @Test
    @DisplayName("retorna todos os produtos")
    void shouldReturnAllProducts() {
        UUID id = UUID.randomUUID();
        when(productRepository.findAll()).thenReturn(List.of(buildProduct(id, "Widget", new BigDecimal("5.00"))));

        List<ProductResponse> result = productService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Widget");
        assertThat(result.get(0).getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("retorna lista vazia quando não há produtos")
    void shouldReturnEmptyListWhenNoProductsExist() {
        when(productRepository.findAll()).thenReturn(List.of());

        List<ProductResponse> result = productService.findAll();

        assertThat(result).isEmpty();
    }

    // --- findAllPaged ---

    @Test
    @DisplayName("retorna página de produtos")
    void shouldReturnPagedProducts() {
        Pageable pageable = PageRequest.of(0, 10);
        UUID id = UUID.randomUUID();
        Page<Product> page = new PageImpl<>(List.of(buildProduct(id, "Gadget", new BigDecimal("20.00"))), pageable, 1);
        when(productRepository.findAll(pageable)).thenReturn(page);

        Page<ProductResponse> result = productService.findAllPaged(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Gadget");
    }

    @Test
    @DisplayName("retorna página vazia quando não há produtos paginados")
    void shouldReturnEmptyPageWhenNoProductsExistPaged() {
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<ProductResponse> result = productService.findAllPaged(pageable);

        assertThat(result.getContent()).isEmpty();
    }

    // --- update ---

    @Test
    @DisplayName("atualiza produto com sucesso")
    void shouldUpdateProductSuccessfully() {
        UUID id = UUID.randomUUID();
        Product existing = buildProduct(id, "Old Name", new BigDecimal("10.00"));
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName("New Name");
        request.setDescription("New desc");
        request.setPrice(new BigDecimal("15.00"));

        when(productRepository.findByIdFiltered(id)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        ProductResponse response = productService.update(id, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getPrice()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("atualiza todos os campos do produto")
    void shouldUpdateAllFieldsOnProduct() {
        UUID id = UUID.randomUUID();
        Product existing = buildProduct(id, "Old", new BigDecimal("1.00"));
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName("New");
        request.setDescription("New desc");
        request.setPrice(new BigDecimal("2.00"));

        when(productRepository.findByIdFiltered(id)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        productService.update(id, request);

        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getDescription()).isEqualTo("New desc");
        assertThat(existing.getPrice()).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao atualizar produto inexistente")
    void shouldThrowWhenUpdatingNonExistentProduct() {
        UUID id = UUID.randomUUID();
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName("X");
        request.setPrice(new BigDecimal("1.00"));
        when(productRepository.findByIdFiltered(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    // --- updatePrice ---

    @Test
    @DisplayName("atualiza preço do produto com sucesso")
    void shouldUpdatePriceSuccessfully() {
        UUID id = UUID.randomUUID();
        Product existing = buildProduct(id, "Widget", new BigDecimal("10.00"));
        when(productRepository.findByIdFiltered(id)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        ProductResponse response = productService.updatePrice(id, new BigDecimal("25.00"));

        assertThat(response.getPrice()).isEqualByComparingTo("25.00");
        assertThat(existing.getPrice()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao atualizar preço de produto inexistente")
    void shouldThrowWhenUpdatingPriceOfNonExistentProduct() {
        UUID id = UUID.randomUUID();
        when(productRepository.findByIdFiltered(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updatePrice(id, new BigDecimal("5.00")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- delete ---

    @Test
    @DisplayName("exclui produto com sucesso e retorna mensagem de confirmação")
    void shouldDeleteProductSuccessfully() {
        UUID id = UUID.randomUUID();
        Product existing = buildProduct(id, "Widget", new BigDecimal("10.00"));
        when(productRepository.findByIdFiltered(id)).thenReturn(Optional.of(existing));

        MessageResponse response = productService.delete(id);

        assertThat(response.getMessage()).isEqualTo("Produto excluído com sucesso");
        verify(productRepository).deleteById(id);
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao excluir produto inexistente")
    void shouldThrowWhenDeletingNonExistentProduct() {
        UUID id = UUID.randomUUID();
        when(productRepository.findByIdFiltered(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).deleteById(any());
    }
}
