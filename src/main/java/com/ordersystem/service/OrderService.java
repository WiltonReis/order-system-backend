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
import com.ordersystem.exception.BusinessException;
import com.ordersystem.exception.ResourceNotFoundException;
import com.ordersystem.repository.OrderItemRepository;
import com.ordersystem.repository.OrderRepository;
import com.ordersystem.repository.ProductRepository;
import com.ordersystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    private static final Random RANDOM = new Random();

    private String generateUniqueOrderCode() {
        String code;
        do {
            code = String.format("%05d", RANDOM.nextInt(100000));
        } while (orderRepository.existsByOrderCode(code));
        return code;
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        Order order = new Order();
        order.setStatus(OrderStatus.OPEN);
        order.setCreatedAt(LocalDateTime.now());
        order.setUser(user);
        order.setOrderCode(generateUniqueOrderCode());
        if (request != null && request.getCustomerName() != null && !request.getCustomerName().isBlank()) {
            order.setCustomerName(request.getCustomerName().trim());
        }

        Order saved = orderRepository.save(order);
        return toOrderResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderListResponse> findAll() {
        return orderRepository.findAllWithUsers().stream()
                .map(this::toOrderListResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderListResponse> findActive() {
        return orderRepository.findAllWithUsersByStatus(OrderStatus.OPEN).stream()
                .map(this::toOrderListResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderListResponse> findHistory() {
        return orderRepository.findAllWithUsersByStatusIn(List.of(OrderStatus.COMPLETED, OrderStatus.CANCELED)).stream()
                .map(this::toOrderListResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse findById(UUID id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return toOrderDetailResponse(order);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public OrderUpdateResponse applyDiscount(UUID id, OrderUpdateRequest request) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        if (order.getStatus() != OrderStatus.OPEN) {
            throw new BusinessException("Discount can only be applied to OPEN orders");
        }

        order.setDiscount(request.getDiscount());
        order.recalculateTotal();

        Order saved = orderRepository.save(order);
        return new OrderUpdateResponse(saved.getId(), saved.getTotal(), saved.getDiscount());
    }

    @Transactional
    public OrderResponse completeOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new BusinessException("Order is already completed");
        }
        if (order.getStatus() == OrderStatus.CANCELED) {
            throw new BusinessException("Cannot complete a canceled order");
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        order.setCompletedByUsername(username);

        Order saved = orderRepository.save(order);
        return toOrderResponse(saved);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        if (order.getStatus() == OrderStatus.CANCELED) {
            throw new BusinessException("Order is already canceled");
        }
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new BusinessException("Cannot cancel a completed order");
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        order.setStatus(OrderStatus.CANCELED);
        order.setCanceledAt(LocalDateTime.now());
        order.setCanceledByUsername(username);

        Order saved = orderRepository.save(order);
        return toOrderResponse(saved);
    }

    @Transactional
    public MessageResponse delete(UUID id) {
        if (!orderRepository.existsById(id)) {
            throw new ResourceNotFoundException("Order", id);
        }
        orderRepository.deleteById(id);
        return new MessageResponse("Order deleted successfully");
    }

    @Transactional
    public OrderItemResponse addItem(UUID orderId, OrderItemRequest request) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != OrderStatus.OPEN) {
            throw new BusinessException("Items can only be added to OPEN orders");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.getProductId()));

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(request.getQuantity());
        item.setPrice(product.getPrice());

        order.getItems().add(item);
        order.recalculateTotal();
        orderRepository.save(order);

        return new OrderItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                item.getQuantity(),
                item.getPrice()
        );
    }

    @Transactional
    public OrderItemUpdateResponse updateItem(UUID orderId, UUID itemId, OrderItemUpdateRequest request) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != OrderStatus.OPEN) {
            throw new BusinessException("Items can only be updated on OPEN orders");
        }

        OrderItem item = orderItemRepository.findByIdAndOrderId(itemId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", itemId));

        item.setQuantity(request.getQuantity());
        orderItemRepository.save(item);

        order.recalculateTotal();
        orderRepository.save(order);

        return new OrderItemUpdateResponse(item.getId(), item.getQuantity(), item.getPrice());
    }

    @Transactional
    public MessageResponse removeItem(UUID orderId, UUID itemId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != OrderStatus.OPEN) {
            throw new BusinessException("Items can only be removed from OPEN orders");
        }

        OrderItem item = orderItemRepository.findByIdAndOrderId(itemId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", itemId));

        order.getItems().remove(item);
        order.recalculateTotal();
        orderRepository.save(order);

        return new MessageResponse("Item removed");
    }

    private OrderResponse toOrderResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getTotal(),
                order.getDiscount(),
                order.getCustomerName(),
                order.getCompletedAt(),
                order.getCanceledAt(),
                order.getCompletedByUsername(),
                order.getCanceledByUsername()
        );
    }

    private OrderListResponse toOrderListResponse(Order order) {
        UserSummaryResponse userSummary = new UserSummaryResponse(
                order.getUser().getId(),
                order.getUser().getUsername()
        );
        return new OrderListResponse(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                order.getTotal(),
                order.getCreatedAt(),
                order.getCustomerName(),
                order.getCompletedAt(),
                order.getCanceledAt(),
                order.getCompletedByUsername(),
                order.getCanceledByUsername(),
                userSummary
        );
    }

    private OrderDetailResponse toOrderDetailResponse(Order order) {
        UserSummaryResponse userSummary = new UserSummaryResponse(
                order.getUser().getId(),
                order.getUser().getUsername()
        );

        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getPrice()
                ))
                .collect(Collectors.toList());

        return new OrderDetailResponse(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getTotal(),
                order.getDiscount(),
                order.getCustomerName(),
                order.getCompletedAt(),
                order.getCanceledAt(),
                order.getCompletedByUsername(),
                order.getCanceledByUsername(),
                userSummary,
                items
        );
    }
}
