package com.ordersystem.service;

import com.ordersystem.dto.request.OrderFilterParams;
import com.ordersystem.dto.request.OrderFullRequest;
import com.ordersystem.dto.request.OrderItemRequest;
import com.ordersystem.dto.request.OrderItemUpdateRequest;
import com.ordersystem.dto.request.OrderRequest;
import com.ordersystem.dto.request.OrderUpdateRequest;
import com.ordersystem.dto.response.*;
import com.ordersystem.entity.*;
import com.ordersystem.enums.OrderStatus;
import com.ordersystem.exception.BusinessException;
import com.ordersystem.exception.ResourceNotFoundException;
import com.ordersystem.mapper.OrderMapper;
import com.ordersystem.repository.*;
import com.ordersystem.security.AuthenticatedUserProvider;
import com.ordersystem.security.TenantContext;
import com.ordersystem.security.UserPrincipal;
import com.ordersystem.validation.OrderValidator;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@DisplayName("OrderService")
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CustomerSaasRepository customerSaasRepository;
    @Mock private OrderStatusHistoryRepository statusHistoryRepository;
    @Mock private OrderPdfService orderPdfService;
    @Spy  private OrderValidator orderValidator = new OrderValidator();
    @Spy  private AuthenticatedUserProvider authenticatedUserProvider = new AuthenticatedUserProvider();
    @Spy  private OrderMapper orderMapper = Mappers.getMapper(OrderMapper.class);
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private MeterRegistry meterRegistry;

    @InjectMocks private OrderService orderService;

    static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        TenantContext.set(TENANT_ID);
        setUpContext("operator@test.com", "operator", List.of());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void setUpContext(String email, String name, List<? extends GrantedAuthority> authorities) {
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(), email, name, null, "pass", null, authorities);
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(email);
        lenient().when(auth.getPrincipal()).thenReturn(principal);
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private void setUpAdminContext() {
        setUpContext("admin@test.com", "admin", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private User buildUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setName(email);
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
        order.setOrderCode("00000001");
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

    private CustomerSaas buildCustomerSaas(UUID id) {
        CustomerSaas cs = new CustomerSaas();
        cs.setId(id);
        return cs;
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create")
    class Create {

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
    }

    // ── createFull ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createFull")
    class CreateFull {

        @Test
        @DisplayName("cria pedido completo com itens com sucesso")
        void shouldCreateFullOrderWithItemsSuccessfully() {
            User user = buildUser("operator@test.com");
            Product product = buildProduct("Produto A", new BigDecimal("50.00"));

            OrderItemRequest itemReq = new OrderItemRequest();
            itemReq.setProductId(product.getId());
            itemReq.setQuantity(2);

            OrderFullRequest request = new OrderFullRequest();
            request.setCustomerName("Cliente Teste");
            request.setItems(List.of(itemReq));

            when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.of(user));
            when(orderRepository.getNextOrderCodeForTenant(any())).thenReturn(1L);
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDetailResponse response = orderService.createFull(request);

            assertThat(response.getStatus()).isEqualTo(OrderStatus.OPEN);
            assertThat(response.getTotal()).isEqualByComparingTo("100.00");
            assertThat(response.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("aplica desconto em pedido completo quando usuário tem role ADMIN")
        void shouldApplyDiscountForAdminRole() {
            setUpAdminContext();
            User user = buildUser("admin@test.com");
            Product product = buildProduct("Produto B", new BigDecimal("100.00"));

            OrderItemRequest itemReq = new OrderItemRequest();
            itemReq.setProductId(product.getId());
            itemReq.setQuantity(1);

            OrderFullRequest request = new OrderFullRequest();
            request.setItems(List.of(itemReq));
            request.setDiscount(new BigDecimal("20.00"));

            when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
            when(orderRepository.getNextOrderCodeForTenant(any())).thenReturn(1L);
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDetailResponse response = orderService.createFull(request);

            assertThat(response.getTotal()).isEqualByComparingTo("80.00");
            assertThat(response.getDiscount()).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("não aplica desconto para usuário sem role ADMIN")
        void shouldIgnoreDiscountForNonAdminRole() {
            User user = buildUser("operator@test.com");
            Product product = buildProduct("Produto C", new BigDecimal("50.00"));

            OrderItemRequest itemReq = new OrderItemRequest();
            itemReq.setProductId(product.getId());
            itemReq.setQuantity(2);

            OrderFullRequest request = new OrderFullRequest();
            request.setItems(List.of(itemReq));
            request.setDiscount(new BigDecimal("10.00"));

            when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.of(user));
            when(orderRepository.getNextOrderCodeForTenant(any())).thenReturn(1L);
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDetailResponse response = orderService.createFull(request);

            assertThat(response.getDiscount()).isEqualByComparingTo("0.00");
            assertThat(response.getTotal()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("lança BusinessException ao criar pedido completo com itens duplicados")
        void shouldThrowWhenItemsHaveDuplicateProduct() {
            User user = buildUser("operator@test.com");
            UUID productId = UUID.randomUUID();

            OrderItemRequest item1 = new OrderItemRequest();
            item1.setProductId(productId);
            item1.setQuantity(1);

            OrderItemRequest item2 = new OrderItemRequest();
            item2.setProductId(productId);
            item2.setQuantity(2);

            OrderFullRequest request = new OrderFullRequest();
            request.setItems(List.of(item1, item2));

            when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.of(user));
            when(orderRepository.getNextOrderCodeForTenant(any())).thenReturn(1L);

            assertThatThrownBy(() -> orderService.createFull(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("duplicados");
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança ResourceNotFoundException ao criar pedido completo com produto inexistente")
        void shouldThrowWhenProductNotFoundInFullOrder() {
            User user = buildUser("operator@test.com");
            UUID productId = UUID.randomUUID();

            OrderItemRequest itemReq = new OrderItemRequest();
            itemReq.setProductId(productId);
            itemReq.setQuantity(1);

            OrderFullRequest request = new OrderFullRequest();
            request.setItems(List.of(itemReq));

            when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.of(user));
            when(orderRepository.getNextOrderCodeForTenant(any())).thenReturn(1L);
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createFull(request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança ResourceNotFoundException ao criar pedido completo com usuário desconhecido")
        void shouldThrowWhenCreatingFullOrderWithUnknownUser() {
            when(userRepository.findByEmail("operator@test.com")).thenReturn(Optional.empty());

            OrderFullRequest request = new OrderFullRequest();
            request.setItems(List.of());

            assertThatThrownBy(() -> orderService.createFull(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

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
    }

    // ── findAll / findActive / findHistory ───────────────────────────────────

    @Nested
    @DisplayName("findAll / findActive / findHistory")
    class FindAllList {

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
    }

    // ── findAllDetails / findAllDetailsFiltered ───────────────────────────────

    @Nested
    @DisplayName("findAllDetails / findAllDetailsFiltered")
    class FindAllDetails {

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

        @Test
        @DisplayName("retorna pedidos filtrados por status com sucesso")
        void shouldReturnFilteredOrderDetails() {
            User user = buildUser("operator@test.com");
            Order order = buildOpenOrder(user);
            Page<Order> pagedOrders = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);
            when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pagedOrders);
            when(orderRepository.findAllWithDetailsByIds(anyList())).thenReturn(List.of(order));

            OrderFilterParams params = new OrderFilterParams();
            params.setStatuses(List.of(OrderStatus.OPEN));

            Page<OrderDetailResponse> result = orderService.findAllDetailsFiltered(params, 0, 10);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(OrderStatus.OPEN);
        }

        @Test
        @DisplayName("retorna página vazia ao filtrar sem resultados")
        void shouldReturnEmptyPageWhenNoFilteredResults() {
            when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            OrderFilterParams params = new OrderFilterParams();

            Page<OrderDetailResponse> result = orderService.findAllDetailsFiltered(params, 0, 10);

            assertThat(result.getContent()).isEmpty();
            verify(orderRepository, never()).findAllWithDetailsByIds(any());
        }
    }

    // ── exportPdf ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("exportPdf")
    class ExportPdf {

        @Test
        @DisplayName("delega geração de PDF ao OrderPdfService")
        void shouldDelegatePdfGenerationToOrderPdfService() {
            User user = buildUser("operator@test.com");
            Order order = buildOpenOrder(user);
            byte[] expectedPdf = new byte[]{0x25, 0x50, 0x44, 0x46};

            when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
            when(orderPdfService.generate(any(OrderDetailResponse.class))).thenReturn(expectedPdf);

            byte[] result = orderService.exportPdf(order.getId());

            assertThat(result).isEqualTo(expectedPdf);
            verify(orderPdfService).generate(any(OrderDetailResponse.class));
        }

        @Test
        @DisplayName("lança ResourceNotFoundException ao exportar PDF de pedido inexistente")
        void shouldThrowWhenExportingPdfOfNonExistentOrder() {
            UUID id = UUID.randomUUID();
            when(orderRepository.findByIdWithDetails(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.exportPdf(id))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(orderPdfService, never()).generate(any());
        }
    }

    // ── getStatusHistory ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getStatusHistory")
    class GetStatusHistory {

        @Test
        @DisplayName("retorna histórico de status para pedido existente")
        void shouldReturnStatusHistoryForExistingOrder() {
            UUID orderId = UUID.randomUUID();
            OrderStatusHistory history = new OrderStatusHistory();
            history.setId(UUID.randomUUID());
            history.setFromStatus(OrderStatus.OPEN);
            history.setToStatus(OrderStatus.COMPLETED);
            history.setChangedAt(LocalDateTime.now());
            history.setChangedBy("operator");

            when(orderRepository.existsById(orderId)).thenReturn(true);
            when(statusHistoryRepository.findByOrderIdOrderByChangedAtDesc(orderId))
                    .thenReturn(List.of(history));

            List<OrderStatusHistoryResponse> result = orderService.getStatusHistory(orderId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).fromStatus()).isEqualTo(OrderStatus.OPEN);
            assertThat(result.get(0).toStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(result.get(0).changedBy()).isEqualTo("operator");
        }

        @Test
        @DisplayName("retorna lista vazia quando pedido não tem histórico de status")
        void shouldReturnEmptyListWhenNoStatusHistory() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.existsById(orderId)).thenReturn(true);
            when(statusHistoryRepository.findByOrderIdOrderByChangedAtDesc(orderId)).thenReturn(List.of());

            List<OrderStatusHistoryResponse> result = orderService.getStatusHistory(orderId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("lança ResourceNotFoundException ao buscar histórico de pedido inexistente")
        void shouldThrowWhenGettingHistoryOfNonExistentOrder() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.existsById(orderId)).thenReturn(false);

            assertThatThrownBy(() -> orderService.getStatusHistory(orderId))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(statusHistoryRepository, never()).findByOrderIdOrderByChangedAtDesc(any());
        }
    }

    // ── applyDiscount ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("applyDiscount")
    class ApplyDiscount {

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
    }

    // ── completeOrder ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("completeOrder")
    class CompleteOrder {

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
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

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
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

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
    }

    // ── restore ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("restore")
    class Restore {

        @Test
        @DisplayName("restaura pedido excluído dentro da janela de 60 segundos")
        void shouldRestoreSoftDeletedOrderSuccessfully() {
            User user = buildUser("operator@test.com");
            Order order = buildOpenOrder(user);
            order.setDeletedAt(LocalDateTime.now().minusSeconds(30));
            order.setCustomerSaas(buildCustomerSaas(TENANT_ID));

            when(orderRepository.findByIdIncludingDeleted(order.getId())).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            MessageResponse response = orderService.restore(order.getId());

            assertThat(response.getMessage()).isEqualTo("Pedido restaurado com sucesso");
            assertThat(order.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("lança BusinessException ao restaurar pedido que não está excluído")
        void shouldThrowWhenRestoringOrderNotDeleted() {
            User user = buildUser("operator@test.com");
            Order order = buildOpenOrder(user);
            order.setCustomerSaas(buildCustomerSaas(TENANT_ID));

            when(orderRepository.findByIdIncludingDeleted(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.restore(order.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("não está excluído");
        }

        @Test
        @DisplayName("lança BusinessException quando janela de restauração de 60s expirou")
        void shouldThrowWhenRestoreWindowExpired() {
            User user = buildUser("operator@test.com");
            Order order = buildOpenOrder(user);
            order.setDeletedAt(LocalDateTime.now().minusSeconds(61));
            order.setCustomerSaas(buildCustomerSaas(TENANT_ID));

            when(orderRepository.findByIdIncludingDeleted(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.restore(order.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("expirou");
        }

        @Test
        @DisplayName("lança ResourceNotFoundException ao restaurar pedido de outro tenant")
        void shouldThrowWhenRestoringOrderFromDifferentTenant() {
            User user = buildUser("operator@test.com");
            Order order = buildOpenOrder(user);
            order.setDeletedAt(LocalDateTime.now().minusSeconds(10));
            order.setCustomerSaas(buildCustomerSaas(UUID.randomUUID()));

            when(orderRepository.findByIdIncludingDeleted(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.restore(order.getId()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("lança ResourceNotFoundException ao restaurar pedido inexistente")
        void shouldThrowWhenRestoringNonExistentOrder() {
            UUID id = UUID.randomUUID();
            when(orderRepository.findByIdIncludingDeleted(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.restore(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── addItem / updateItem / removeItem ────────────────────────────────────

    @Nested
    @DisplayName("addItem / updateItem / removeItem")
    class Items {

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
}
