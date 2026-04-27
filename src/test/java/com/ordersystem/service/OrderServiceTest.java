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
import com.ordersystem.repository.OrderItemRepository;
import com.ordersystem.repository.OrderRepository;
import com.ordersystem.repository.ProductRepository;
import com.ordersystem.repository.UserRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUpSecurityContext() {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("operator");
        SecurityContext context = mock(SecurityContext.class);
        lenient().when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User buildUser(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
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
    void shouldCreateOrderWithCustomerNameSuccessfully() {
        // Given
        User user = buildUser("operator");
        OrderRequest request = new OrderRequest();
        request.setCustomerName("  John Doe  ");
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCode()).thenReturn(1L);
        Order saved = buildOpenOrder(user);
        saved.setCustomerName("John Doe");
        when(orderRepository.save(any(Order.class))).thenReturn(saved);

        // When
        OrderResponse response = orderService.create(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(response.getCustomerName()).isEqualTo("John Doe");
    }

    @Test
    void shouldTrimCustomerNameBeforePersisting() {
        // Given
        User user = buildUser("operator");
        OrderRequest request = new OrderRequest();
        request.setCustomerName("  Alice  ");
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCode()).thenReturn(2L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        orderService.create(request);

        // Then
        verify(orderRepository).save(argThat(o -> "Alice".equals(o.getCustomerName())));
    }

    @Test
    void shouldCreateOrderWithoutCustomerNameWhenNotProvided() {
        // Given
        User user = buildUser("operator");
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCode()).thenReturn(3L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        orderService.create(new OrderRequest());

        // Then
        verify(orderRepository).save(argThat(o -> o.getCustomerName() == null));
    }

    @Test
    void shouldIgnoreBlankCustomerName() {
        // Given
        User user = buildUser("operator");
        OrderRequest request = new OrderRequest();
        request.setCustomerName("   ");
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCode()).thenReturn(4L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        orderService.create(request);

        // Then
        verify(orderRepository).save(argThat(o -> o.getCustomerName() == null));
    }

    @Test
    void shouldCreateOrderWithNullRequest() {
        // Given
        User user = buildUser("operator");
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCode()).thenReturn(5L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // When / Then — não deve lançar exceção
        orderService.create(null);
        verify(orderRepository).save(argThat(o -> o.getCustomerName() == null));
    }

    @Test
    void shouldFormatOrderCodeWithFiveDigitPadding() {
        // Given — PERF-03: código gerado via sequence com padding de 5 dígitos
        User user = buildUser("operator");
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCode()).thenReturn(7L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        orderService.create(new OrderRequest());

        // Then
        verify(orderRepository).save(argThat(o -> "00007".equals(o.getOrderCode())));
    }

    @Test
    void shouldSetOrderStatusAsOpenOnCreate() {
        // Given
        User user = buildUser("operator");
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));
        when(orderRepository.getNextOrderCode()).thenReturn(1L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        orderService.create(new OrderRequest());

        // Then
        verify(orderRepository).save(argThat(o -> OrderStatus.OPEN.equals(o.getStatus())));
    }

    @Test
    void shouldThrowWhenCreatingOrderWithUnknownUser() {
        // Given
        when(userRepository.findByUsername("operator")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> orderService.create(new OrderRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(orderRepository, never()).save(any());
    }

    // --- findById ---

    @Test
    void shouldFindOrderByIdSuccessfully() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        // When
        OrderDetailResponse response = orderService.findById(order.getId());

        // Then
        assertThat(response.getId()).isEqualTo(order.getId());
        assertThat(response.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void shouldThrowWhenOrderNotFoundById() {
        // Given
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(id)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> orderService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- findAll / findActive / findHistory ---

    @Test
    void shouldReturnPagedOrders() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        User user = buildUser("operator");
        Page<Order> page = new PageImpl<>(List.of(buildOpenOrder(user)), pageable, 1);
        when(orderRepository.findAllWithUsersPaged(pageable)).thenReturn(page);

        // When
        Page<OrderListResponse> result = orderService.findAll(pageable);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(OrderStatus.OPEN);
    }

    @Test
    void shouldReturnActiveOrders() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        User user = buildUser("operator");
        Page<Order> page = new PageImpl<>(List.of(buildOpenOrder(user)), pageable, 1);
        when(orderRepository.findAllWithUsersByStatusPaged(OrderStatus.OPEN, pageable)).thenReturn(page);

        // When
        Page<OrderListResponse> result = orderService.findActive(pageable);

        // Then
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(OrderStatus.OPEN);
    }

    @Test
    void shouldReturnOrderHistoryWithCompletedAndCanceled() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        User user = buildUser("operator");
        Order completed = buildOpenOrder(user);
        completed.setStatus(OrderStatus.COMPLETED);
        Page<Order> page = new PageImpl<>(List.of(completed), pageable, 1);
        when(orderRepository.findAllWithUsersByStatusInPaged(
                List.of(OrderStatus.COMPLETED, OrderStatus.CANCELED), pageable)).thenReturn(page);

        // When
        Page<OrderListResponse> result = orderService.findHistory(pageable);

        // Then
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    // --- findAllDetails ---

    @Test
    void shouldReturnAllOrderDetails() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        UUID orderId = order.getId();
        Page<UUID> idsPage = new PageImpl<>(List.of(orderId), pageable, 1);
        when(orderRepository.findAllIdsPaged(pageable)).thenReturn(idsPage);
        when(orderRepository.findAllWithDetailsByIds(List.of(orderId))).thenReturn(List.of(order));

        // When
        Page<OrderDetailResponse> result = orderService.findAllDetails(pageable);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(orderId);
    }

    @Test
    void shouldReturnEmptyPageWhenNoOrderIds() {
        // Given — PERF-01: evita N+1 retornando vazio sem chamar findAllWithDetailsByIds
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findAllIdsPaged(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // When
        Page<OrderDetailResponse> result = orderService.findAllDetails(pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        verify(orderRepository, never()).findAllWithDetailsByIds(any());
    }

    // --- applyDiscount ---

    @Test
    void shouldApplyDiscountToOpenOrder() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("100.00"));
        order.getItems().add(buildItem(order, product, 2, new BigDecimal("100.00")));
        order.recalculateTotal();

        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setDiscount(new BigDecimal("20.00"));
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        // When
        OrderUpdateResponse response = orderService.applyDiscount(order.getId(), request);

        // Then
        assertThat(response.getDiscount()).isEqualByComparingTo("20.00");
        assertThat(response.getTotal()).isEqualByComparingTo("180.00");
    }

    @Test
    void shouldKeepTotalAtZeroWhenDiscountExceedsSubtotal() {
        // Given — BACK-01: desconto maior que subtotal não gera total negativo
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("10.00"));
        order.getItems().add(buildItem(order, product, 1, new BigDecimal("10.00")));

        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setDiscount(new BigDecimal("50.00"));
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        // When
        OrderUpdateResponse response = orderService.applyDiscount(order.getId(), request);

        // Then
        assertThat(response.getTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldThrowWhenApplyingDiscountToCompletedOrder() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.COMPLETED);
        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setDiscount(new BigDecimal("10.00"));
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        // When / Then
        assertThatThrownBy(() -> orderService.applyDiscount(order.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OPEN");
    }

    @Test
    void shouldThrowWhenApplyingDiscountToCanceledOrder() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.CANCELED);
        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setDiscount(new BigDecimal("5.00"));
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        // When / Then
        assertThatThrownBy(() -> orderService.applyDiscount(order.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldThrowWhenOrderNotFoundForDiscount() {
        // Given
        UUID id = UUID.randomUUID();
        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setDiscount(BigDecimal.TEN);
        when(orderRepository.findByIdWithDetails(id)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> orderService.applyDiscount(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- completeOrder ---

    @Test
    void shouldCompleteOpenOrderSuccessfully() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        // When
        OrderResponse response = orderService.completeOrder(order.getId());

        // Then
        assertThat(response.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getCompletedAt()).isNotNull();
        assertThat(order.getCompletedByUsername()).isEqualTo("operator");
    }

    @Test
    void shouldSetCompletedByUsernameFromSecurityContext() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        // When
        orderService.completeOrder(order.getId());

        // Then
        assertThat(order.getCompletedByUsername()).isEqualTo("operator");
    }

    @Test
    void shouldThrowWhenCompletingAlreadyCompletedOrder() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        // When / Then
        assertThatThrownBy(() -> orderService.completeOrder(order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    void shouldThrowWhenCompletingCanceledOrder() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.CANCELED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        // When / Then
        assertThatThrownBy(() -> orderService.completeOrder(order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("canceled");
    }

    @Test
    void shouldThrowWhenOrderNotFoundForCompletion() {
        // Given
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> orderService.completeOrder(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- cancelOrder ---

    @Test
    void shouldCancelOpenOrderSuccessfully() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        // When
        OrderResponse response = orderService.cancelOrder(order.getId());

        // Then
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCanceledAt()).isNotNull();
        assertThat(order.getCanceledByUsername()).isEqualTo("operator");
    }

    @Test
    void shouldSetCanceledByUsernameFromSecurityContext() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        // When
        orderService.cancelOrder(order.getId());

        // Then
        assertThat(order.getCanceledByUsername()).isEqualTo("operator");
    }

    @Test
    void shouldThrowWhenCancelingAlreadyCanceledOrder() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.CANCELED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        // When / Then
        assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already canceled");
    }

    @Test
    void shouldThrowWhenCancelingCompletedOrder() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        // When / Then
        assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("completed");
    }

    @Test
    void shouldThrowWhenOrderNotFoundForCancellation() {
        // Given
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> orderService.cancelOrder(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- delete ---

    @Test
    void shouldDeleteOrderSuccessfully() {
        // Given
        UUID id = UUID.randomUUID();
        when(orderRepository.existsById(id)).thenReturn(true);

        // When
        MessageResponse response = orderService.delete(id);

        // Then
        assertThat(response.getMessage()).isEqualTo("Order deleted successfully");
        verify(orderRepository).deleteById(id);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentOrder() {
        // Given
        UUID id = UUID.randomUUID();
        when(orderRepository.existsById(id)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> orderService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(orderRepository, never()).deleteById(any());
    }

    // --- addItem ---

    @Test
    void shouldAddItemToOpenOrderSuccessfully() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Product A", new BigDecimal("50.00"));
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(product.getId());
        request.setQuantity(3);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(order)).thenReturn(order);

        // When
        OrderItemResponse response = orderService.addItem(order.getId(), request);

        // Then
        assertThat(response.getQuantity()).isEqualTo(3);
        assertThat(response.getPrice()).isEqualByComparingTo("50.00");
        assertThat(response.getProductName()).isEqualTo("Product A");
        assertThat(order.getItems()).hasSize(1);
    }

    @Test
    void shouldRecalculateTotalAfterAddingItem() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("30.00"));
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(product.getId());
        request.setQuantity(2);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(order)).thenReturn(order);

        // When
        orderService.addItem(order.getId(), request);

        // Then
        assertThat(order.getTotal()).isEqualByComparingTo("60.00");
    }

    @Test
    void shouldSnapshotProductPriceAtTimeOfAdding() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("45.00"));
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(product.getId());
        request.setQuantity(1);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(order)).thenReturn(order);

        // When
        OrderItemResponse response = orderService.addItem(order.getId(), request);

        // Then — preço é snapshot do produto no momento da adição
        assertThat(response.getPrice()).isEqualByComparingTo("45.00");
    }

    @Test
    void shouldThrowWhenAddingItemToCompletedOrder() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(UUID.randomUUID());
        request.setQuantity(1);

        // When / Then
        assertThatThrownBy(() -> orderService.addItem(order.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OPEN");
    }

    @Test
    void shouldThrowWhenAddingItemToCanceledOrder() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.CANCELED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(UUID.randomUUID());
        request.setQuantity(1);

        // When / Then
        assertThatThrownBy(() -> orderService.addItem(order.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldThrowWhenAddingItemToNonExistentOrder() {
        // Given
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.empty());

        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(UUID.randomUUID());
        request.setQuantity(1);

        // When / Then
        assertThatThrownBy(() -> orderService.addItem(orderId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldThrowWhenAddingNonExistentProduct() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        UUID productId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(productId);
        request.setQuantity(1);

        // When / Then
        assertThatThrownBy(() -> orderService.addItem(order.getId(), request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- updateItem ---

    @Test
    void shouldUpdateItemQuantitySuccessfully() {
        // Given
        User user = buildUser("operator");
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

        // When
        OrderItemUpdateResponse response = orderService.updateItem(order.getId(), item.getId(), request);

        // Then
        assertThat(response.getQuantity()).isEqualTo(5);
        assertThat(item.getQuantity()).isEqualTo(5);
    }

    @Test
    void shouldRecalculateTotalAfterUpdatingItem() {
        // Given
        User user = buildUser("operator");
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

        // When
        orderService.updateItem(order.getId(), item.getId(), request);

        // Then
        assertThat(order.getTotal()).isEqualByComparingTo("40.00");
    }

    @Test
    void shouldThrowWhenUpdatingItemOnNonOpenOrder() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.COMPLETED);
        UUID itemId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        OrderItemUpdateRequest request = new OrderItemUpdateRequest();
        request.setQuantity(2);

        // When / Then
        assertThatThrownBy(() -> orderService.updateItem(order.getId(), itemId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OPEN");
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentItem() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        UUID itemId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(itemId, order.getId())).thenReturn(Optional.empty());

        OrderItemUpdateRequest request = new OrderItemUpdateRequest();
        request.setQuantity(2);

        // When / Then
        assertThatThrownBy(() -> orderService.updateItem(order.getId(), itemId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- removeItem ---

    @Test
    void shouldRemoveItemFromOpenOrderSuccessfully() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("10.00"));
        OrderItem item = buildItem(order, product, 1, new BigDecimal("10.00"));
        order.getItems().add(item);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(item.getId(), order.getId())).thenReturn(Optional.of(item));
        when(orderRepository.save(order)).thenReturn(order);

        // When
        MessageResponse response = orderService.removeItem(order.getId(), item.getId());

        // Then
        assertThat(response.getMessage()).isEqualTo("Item removed");
        assertThat(order.getItems()).isEmpty();
    }

    @Test
    void shouldRecalculateTotalToZeroAfterRemovingLastItem() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        Product product = buildProduct("Item", new BigDecimal("50.00"));
        OrderItem item = buildItem(order, product, 2, new BigDecimal("50.00"));
        order.getItems().add(item);
        order.recalculateTotal();

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(item.getId(), order.getId())).thenReturn(Optional.of(item));
        when(orderRepository.save(order)).thenReturn(order);

        // When
        orderService.removeItem(order.getId(), item.getId());

        // Then
        assertThat(order.getTotal()).isEqualByComparingTo("0.00");
        assertThat(order.getItems()).isEmpty();
    }

    @Test
    void shouldThrowWhenRemovingItemFromNonOpenOrder() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        order.setStatus(OrderStatus.COMPLETED);
        UUID itemId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        // When / Then
        assertThatThrownBy(() -> orderService.removeItem(order.getId(), itemId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OPEN");
    }

    @Test
    void shouldThrowWhenRemovingNonExistentItem() {
        // Given
        User user = buildUser("operator");
        Order order = buildOpenOrder(user);
        UUID itemId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(itemId, order.getId())).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> orderService.removeItem(order.getId(), itemId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldThrowWhenRemovingItemFromNonExistentOrder() {
        // Given
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> orderService.removeItem(orderId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
