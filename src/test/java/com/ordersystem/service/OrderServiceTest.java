package com.ordersystem.service;

import com.ordersystem.dto.request.OrderItemRequest;
import com.ordersystem.dto.request.OrderItemUpdateRequest;
import com.ordersystem.dto.request.OrderRequest;
import com.ordersystem.dto.request.OrderUpdateRequest;
import com.ordersystem.dto.response.*;
import com.ordersystem.entity.Order;
import com.ordersystem.entity.OrderItem;
import com.ordersystem.entity.Product;
import com.ordersystem.entity.User;
import com.ordersystem.enums.OrderStatus;
import com.ordersystem.enums.Role;
import com.ordersystem.exception.BusinessException;
import com.ordersystem.exception.ResourceNotFoundException;
import com.ordersystem.repository.CustomerSaasRepository;
import com.ordersystem.repository.OrderItemRepository;
import com.ordersystem.repository.OrderRepository;
import com.ordersystem.repository.OrderStatusHistoryRepository;
import com.ordersystem.repository.ProductRepository;
import com.ordersystem.repository.UserRepository;
import com.ordersystem.mapper.OrderMapper;
import com.ordersystem.security.AuthenticatedUserProvider;
import com.ordersystem.security.TenantContext;
import com.ordersystem.security.UserPrincipal;
import com.ordersystem.validation.OrderValidator;
import org.mapstruct.factory.Mappers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.micrometer.core.instrument.MeterRegistry;
import org.mockito.Answers;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("OrderService — criação, consulta, status e itens de pedido")
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerSaasRepository customerSaasRepository;

    @Mock
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Spy
    private OrderValidator orderValidator = new OrderValidator();

    @Spy
    private AuthenticatedUserProvider authenticatedUserProvider = new AuthenticatedUserProvider();

    @Spy
    private OrderMapper orderMapper = Mappers.getMapper(OrderMapper.class);

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry meterRegistry;

    @InjectMocks
    private OrderService orderService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        TenantContext.set(TENANT_ID);
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(), "operator@test.com", "operator", null, "pass", null, List.of()
        );
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("operator@test.com");
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

    private User buildUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setName(email);
        user.setRole(Role.USER);
        return user;
    }

    private Product buildProduct(String name, BigDecimal price) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName(name);
        product.setPrice(price);
        return product;
    }

    private Order buildOpenOrder(User user) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderCode("00001");
        order.setStatus(OrderStatus.OPEN);
        order.setCreatedAt(LocalDateTime.now());
        order.setUser(user);
        order.setTotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setItems(new ArrayList<>());
        return order;
    }

    private OrderItem buildItem(Order order, Product product, int quantity, BigDecimal price) {
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setPrice(price);
        return item;
    }

    // --- create ---

    @Test
    @DisplayName("cria pedido com nome do cliente com sucesso")
    void shouldCreateOrderWithCustomerNameSuccessfully() {
        User user = buildUser("operator@test.com");
        OrderRequest request = new OrderRequest();
        request.setCustomerName("  John Doe  ");
        when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCodeForTenant(any())).thenReturn(1L);
        Order saved = buildOpenOrder(user);
        saved.setCustomerName("John Doe");
        when(orderRepository.save(any(Order.class))).thenReturn(saved);

        OrderResponse response = orderService.create(request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(response.getCustomerName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("remove espaços do nome do cliente antes de persistir")
    void shouldTrimCustomerNameBeforePersisting() {
        User user = buildUser("operator@test.com");
        OrderRequest request = new OrderRequest();
        request.setCustomerName("  Alice  ");
        when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCodeForTenant(any())).thenReturn(2L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(request);

        verify(orderRepository).save(argThat(o -> "Alice".equals(o.getCustomerName())));
    }

    @Test
    @DisplayName("cria pedido sem nome do cliente quando não informado")
    void shouldCreateOrderWithoutCustomerNameWhenNotProvided() {
        User user = buildUser("operator@test.com");
        when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCodeForTenant(any())).thenReturn(3L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(new OrderRequest());

        verify(orderRepository).save(argThat(o -> o.getCustomerName() == null));
    }

    @Test
    @DisplayName("ignora nome do cliente quando contém apenas espaços")
    void shouldIgnoreBlankCustomerName() {
        User user = buildUser("operator@test.com");
        OrderRequest request = new OrderRequest();
        request.setCustomerName("   ");
        when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCodeForTenant(any())).thenReturn(4L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(request);

        verify(orderRepository).save(argThat(o -> o.getCustomerName() == null));
    }

    @Test
    @DisplayName("cria pedido com request nulo sem lançar exceção")
    void shouldCreateOrderWithNullRequest() {
        User user = buildUser("operator@test.com");
        when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCodeForTenant(any())).thenReturn(5L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(null);
        verify(orderRepository).save(argThat(o -> o.getCustomerName() == null));
    }

    @Test
    @DisplayName("formata código do pedido com padding de 8 dígitos")
    void shouldFormatOrderCodeWithEightDigitPadding() {
        User user = buildUser("operator@test.com");
        when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCodeForTenant(any())).thenReturn(7L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(new OrderRequest());

        verify(orderRepository).save(argThat(o -> "00000007".equals(o.getOrderCode())));
    }

    @Test
    @DisplayName("define status OPEN ao criar pedido")
    void shouldSetOrderStatusAsOpenOnCreate() {
        User user = buildUser("operator@test.com");
        when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCodeForTenant(any())).thenReturn(1L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(new OrderRequest());

        verify(orderRepository).save(argThat(o -> OrderStatus.OPEN.equals(o.getStatus())));
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao criar pedido com usuário desconhecido")
    void shouldThrowWhenCreatingOrderWithUnknownUser() {
        when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(new OrderRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(orderRepository, never()).save(any());
    }

    // --- findById ---

    @Test
    @DisplayName("retorna detalhes do pedido pelo ID")
    void shouldFindOrderByIdSuccessfully() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        OrderDetailResponse response = orderService.findById(order.getId());

        assertThat(response.getId()).isEqualTo(order.getId());
        assertThat(response.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao buscar pedido inexistente")
    void shouldThrowWhenOrderNotFoundById() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- findAll / findActive / findHistory ---

    @Test
    @DisplayName("retorna página de pedidos")
    void shouldReturnPagedOrders() {
        Pageable pageable = PageRequest.of(0, 10);
        User user = buildUser("operator@test.com");
        Page<Order> page = new PageImpl<>(List.of(buildOpenOrder(user)), pageable, 1);
        when(orderRepository.findAllWithUsersPaged(pageable)).thenReturn(page);

        Page<OrderListResponse> result = orderService.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(OrderStatus.OPEN);
    }

    @Test
    @DisplayName("retorna pedidos ativos com status OPEN")
    void shouldReturnActiveOrders() {
        Pageable pageable = PageRequest.of(0, 10);
        User user = buildUser("operator@test.com");
        Page<Order> page = new PageImpl<>(List.of(buildOpenOrder(user)), pageable, 1);
        when(orderRepository.findAllWithUsersByStatusPaged(OrderStatus.OPEN, pageable)).thenReturn(page);

        Page<OrderListResponse> result = orderService.findActive(pageable);

        assertThat(result.getContent().get(0).getStatus()).isEqualTo(OrderStatus.OPEN);
    }

    @Test
    @DisplayName("retorna histórico com pedidos COMPLETED e CANCELED")
    void shouldReturnOrderHistoryWithCompletedAndCanceled() {
        Pageable pageable = PageRequest.of(0, 10);
        User user = buildUser("operator@test.com");
        Order completed = buildOpenOrder(user);
        completed.setStatus(OrderStatus.COMPLETED);
        Page<Order> page = new PageImpl<>(List.of(completed), pageable, 1);
        when(orderRepository.findAllWithUsersByStatusInPaged(
                List.of(OrderStatus.COMPLETED, OrderStatus.CANCELED), pageable)).thenReturn(page);

        Page<OrderListResponse> result = orderService.findHistory(pageable);

        assertThat(result.getContent().get(0).getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    // --- findAllDetails ---

    @Test
    @DisplayName("retorna todos os detalhes de pedidos paginados")
    void shouldReturnAllOrderDetails() {
        Pageable pageable = PageRequest.of(0, 10);
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        UUID orderId = order.getId();
        Page<UUID> idsPage = new PageImpl<>(List.of(orderId), pageable, 1);
        when(orderRepository.findAllIdsPaged(pageable)).thenReturn(idsPage);
        when(orderRepository.findAllWithDetailsByIds(List.of(orderId))).thenReturn(List.of(order));

        Page<OrderDetailResponse> result = orderService.findAllDetails(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("retorna página vazia sem chamar findAllWithDetailsByIds quando não há IDs")
    void shouldReturnEmptyPageWhenNoOrderIds() {
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findAllIdsPaged(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<OrderDetailResponse> result = orderService.findAllDetails(pageable);

        assertThat(result.getContent()).isEmpty();
        verify(orderRepository, never()).findAllWithDetailsByIds(any());
    }

    // --- applyDiscount ---

    @Test
    @DisplayName("aplica desconto em pedido aberto e recalcula total")
    void shouldApplyDiscountToOpenOrder() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("100.00"));
        order.getItems().add(buildItem(order, product, 2, new BigDecimal("100.00")));
        order.recalculateTotal();

        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setDiscount(new BigDecimal("20.00"));
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderUpdateResponse response = orderService.applyDiscount(order.getId(), request);

        assertThat(response.getDiscount()).isEqualByComparingTo("20.00");
        assertThat(response.getTotal()).isEqualByComparingTo("180.00");
    }

    @Test
    @DisplayName("lança BusinessException quando desconto é maior ou igual ao subtotal")
    void shouldThrowWhenDiscountExceedsSubtotal() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("10.00"));
        order.getItems().add(buildItem(order, product, 1, new BigDecimal("10.00")));

        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setDiscount(new BigDecimal("50.00"));
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.applyDiscount(order.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("subtotal");
    }

    @Test
    @DisplayName("lança BusinessException ao aplicar desconto em pedido COMPLETED")
    void shouldThrowWhenApplyingDiscountToCompletedOrder() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.COMPLETED);
        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setDiscount(new BigDecimal("10.00"));
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.applyDiscount(order.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("abertos");
    }

    @Test
    @DisplayName("lança BusinessException ao aplicar desconto em pedido CANCELED")
    void shouldThrowWhenApplyingDiscountToCanceledOrder() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.CANCELED);
        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setDiscount(new BigDecimal("5.00"));
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.applyDiscount(order.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao aplicar desconto em pedido inexistente")
    void shouldThrowWhenOrderNotFoundForDiscount() {
        UUID id = UUID.randomUUID();
        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setDiscount(BigDecimal.TEN);
        when(orderRepository.findByIdWithDetails(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.applyDiscount(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- completeOrder ---

    @Test
    @DisplayName("finaliza pedido aberto com sucesso")
    void shouldCompleteOpenOrderSuccessfully() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.completeOrder(order.getId());

        assertThat(response.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getCompletedAt()).isNotNull();
        assertThat(order.getCompletedByName()).isEqualTo("operator");
    }

    @Test
    @DisplayName("define completedByName a partir do SecurityContext")
    void shouldSetCompletedByNameFromSecurityContext() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.completeOrder(order.getId());

        assertThat(order.getCompletedByName()).isEqualTo("operator");
    }

    @Test
    @DisplayName("lança BusinessException ao finalizar pedido já COMPLETED")
    void shouldThrowWhenCompletingAlreadyCompletedOrder() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.completeOrder(order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("finalizado");
    }

    @Test
    @DisplayName("lança BusinessException ao finalizar pedido CANCELED")
    void shouldThrowWhenCompletingCanceledOrder() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.CANCELED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.completeOrder(order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("abertos");
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao finalizar pedido inexistente")
    void shouldThrowWhenOrderNotFoundForCompletion() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.completeOrder(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- cancelOrder ---

    @Test
    @DisplayName("cancela pedido aberto com sucesso")
    void shouldCancelOpenOrderSuccessfully() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.cancelOrder(order.getId());

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCanceledAt()).isNotNull();
        assertThat(order.getCanceledByName()).isEqualTo("operator");
    }

    @Test
    @DisplayName("define canceledByName a partir do SecurityContext")
    void shouldSetCanceledByNameFromSecurityContext() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.cancelOrder(order.getId());

        assertThat(order.getCanceledByName()).isEqualTo("operator");
    }

    @Test
    @DisplayName("lança BusinessException ao cancelar pedido já CANCELED")
    void shouldThrowWhenCancelingAlreadyCanceledOrder() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.CANCELED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cancelado");
    }

    @Test
    @DisplayName("lança BusinessException ao cancelar pedido COMPLETED")
    void shouldThrowWhenCancelingCompletedOrder() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("abertos");
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao cancelar pedido inexistente")
    void shouldThrowWhenOrderNotFoundForCancellation() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- delete ---

    @Test
    @DisplayName("exclui pedido com sucesso e retorna mensagem de confirmação")
    void shouldDeleteOrderSuccessfully() {
        UUID id = UUID.randomUUID();
        when(orderRepository.existsById(id)).thenReturn(true);

        MessageResponse response = orderService.delete(id);

        assertThat(response.getMessage()).isEqualTo("Pedido excluído com sucesso");
        verify(orderRepository).deleteById(id);
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao excluir pedido inexistente")
    void shouldThrowWhenDeletingNonExistentOrder() {
        UUID id = UUID.randomUUID();
        when(orderRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> orderService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(orderRepository, never()).deleteById(any());
    }

    // --- addItem ---

    @Test
    @DisplayName("adiciona item em pedido aberto com sucesso")
    void shouldAddItemToOpenOrderSuccessfully() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Product A", new BigDecimal("50.00"));
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(product.getId());
        request.setQuantity(3);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(order)).thenReturn(order);

        OrderItemResponse response = orderService.addItem(order.getId(), request);

        assertThat(response.getQuantity()).isEqualTo(3);
        assertThat(response.getPrice()).isEqualByComparingTo("50.00");
        assertThat(response.getProductName()).isEqualTo("Product A");
        assertThat(order.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("recalcula total do pedido após adicionar item")
    void shouldRecalculateTotalAfterAddingItem() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("30.00"));
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(product.getId());
        request.setQuantity(2);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.addItem(order.getId(), request);

        assertThat(order.getTotal()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("registra snapshot do preço do produto no momento da adição")
    void shouldSnapshotProductPriceAtTimeOfAdding() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("45.00"));
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(product.getId());
        request.setQuantity(1);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(order)).thenReturn(order);

        OrderItemResponse response = orderService.addItem(order.getId(), request);

        assertThat(response.getPrice()).isEqualByComparingTo("45.00");
    }

    @Test
    @DisplayName("lança BusinessException ao adicionar item em pedido COMPLETED")
    void shouldThrowWhenAddingItemToCompletedOrder() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(UUID.randomUUID());
        request.setQuantity(1);

        assertThatThrownBy(() -> orderService.addItem(order.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("abertos");
    }

    @Test
    @DisplayName("lança BusinessException ao adicionar item em pedido CANCELED")
    void shouldThrowWhenAddingItemToCanceledOrder() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.CANCELED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(UUID.randomUUID());
        request.setQuantity(1);

        assertThatThrownBy(() -> orderService.addItem(order.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao adicionar item em pedido inexistente")
    void shouldThrowWhenAddingItemToNonExistentOrder() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.empty());

        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(UUID.randomUUID());
        request.setQuantity(1);

        assertThatThrownBy(() -> orderService.addItem(orderId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao adicionar produto inexistente ao pedido")
    void shouldThrowWhenAddingNonExistentProduct() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        UUID productId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(productId);
        request.setQuantity(1);

        assertThatThrownBy(() -> orderService.addItem(order.getId(), request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- updateItem ---

    @Test
    @DisplayName("atualiza quantidade do item com sucesso")
    void shouldUpdateItemQuantitySuccessfully() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("20.00"));
        OrderItem item = buildItem(order, product, 1, new BigDecimal("20.00"));
        order.getItems().add(item);

        OrderItemUpdateRequest request = new OrderItemUpdateRequest();
        request.setQuantity(5);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(item.getId(), order.getId())).thenReturn(Optional.of(item));
        when(orderItemRepository.save(item)).thenReturn(item);
        when(orderRepository.save(order)).thenReturn(order);

        OrderItemUpdateResponse response = orderService.updateItem(order.getId(), item.getId(), request);

        assertThat(response.getQuantity()).isEqualTo(5);
        assertThat(item.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("recalcula total do pedido após atualizar quantidade do item")
    void shouldRecalculateTotalAfterUpdatingItem() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("10.00"));
        OrderItem item = buildItem(order, product, 1, new BigDecimal("10.00"));
        order.getItems().add(item);

        OrderItemUpdateRequest request = new OrderItemUpdateRequest();
        request.setQuantity(4);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(item.getId(), order.getId())).thenReturn(Optional.of(item));
        when(orderItemRepository.save(item)).thenReturn(item);
        when(orderRepository.save(order)).thenReturn(order);

        orderService.updateItem(order.getId(), item.getId(), request);

        assertThat(order.getTotal()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("lança BusinessException ao atualizar item de pedido não aberto")
    void shouldThrowWhenUpdatingItemOnNonOpenOrder() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.COMPLETED);
        UUID itemId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        OrderItemUpdateRequest request = new OrderItemUpdateRequest();
        request.setQuantity(2);

        assertThatThrownBy(() -> orderService.updateItem(order.getId(), itemId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("abertos");
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao atualizar item inexistente")
    void shouldThrowWhenUpdatingNonExistentItem() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        UUID itemId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(itemId, order.getId())).thenReturn(Optional.empty());

        OrderItemUpdateRequest request = new OrderItemUpdateRequest();
        request.setQuantity(2);

        assertThatThrownBy(() -> orderService.updateItem(order.getId(), itemId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- removeItem ---

    @Test
    @DisplayName("remove item de pedido aberto com sucesso")
    void shouldRemoveItemFromOpenOrderSuccessfully() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("10.00"));
        OrderItem item = buildItem(order, product, 1, new BigDecimal("10.00"));
        order.getItems().add(item);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(item.getId(), order.getId())).thenReturn(Optional.of(item));
        when(orderRepository.save(order)).thenReturn(order);

        MessageResponse response = orderService.removeItem(order.getId(), item.getId());

        assertThat(response.getMessage()).isEqualTo("Item removido");
        assertThat(order.getItems()).isEmpty();
    }

    @Test
    @DisplayName("recalcula total para zero após remover último item")
    void shouldRecalculateTotalToZeroAfterRemovingLastItem() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("50.00"));
        OrderItem item = buildItem(order, product, 2, new BigDecimal("50.00"));
        order.getItems().add(item);
        order.recalculateTotal();

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(item.getId(), order.getId())).thenReturn(Optional.of(item));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.removeItem(order.getId(), item.getId());

        assertThat(order.getTotal()).isEqualByComparingTo("0.00");
        assertThat(order.getItems()).isEmpty();
    }

    @Test
    @DisplayName("lança BusinessException ao remover item de pedido não aberto")
    void shouldThrowWhenRemovingItemFromNonOpenOrder() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.COMPLETED);
        UUID itemId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.removeItem(order.getId(), itemId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("abertos");
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao remover item inexistente")
    void shouldThrowWhenRemovingNonExistentItem() {
        User user = buildUser("operator@test.com");
        Order order = buildOpenOrder(user);
        UUID itemId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(itemId, order.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.removeItem(order.getId(), itemId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao remover item de pedido inexistente")
    void shouldThrowWhenRemovingItemFromNonExistentOrder() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.removeItem(orderId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
