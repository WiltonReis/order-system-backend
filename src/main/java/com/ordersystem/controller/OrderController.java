package com.ordersystem.controller;

import com.ordersystem.dto.request.OrderFilterParams;
import com.ordersystem.dto.request.OrderFullRequest;
import com.ordersystem.dto.request.OrderItemRequest;
import com.ordersystem.dto.request.OrderItemUpdateRequest;
import com.ordersystem.dto.request.OrderRequest;
import com.ordersystem.dto.request.OrderUpdateRequest;
import com.ordersystem.enums.OrderStatus;
import com.ordersystem.dto.response.*;
import com.ordersystem.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody(required = false) OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request));
    }

    // ESC-03: criação atômica — pedido completo (itens + desconto) em uma única transação
    @PostMapping("/full")
    public ResponseEntity<OrderDetailResponse> createFull(@Valid @RequestBody OrderFullRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createFull(request));
    }

    // PERF-01 + PERF-02 + FUT-02: retorna pedidos completos paginados com filtros opcionais
    @GetMapping("/details")
    public ResponseEntity<Page<OrderDetailResponse>> findAllDetails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<OrderStatus> statuses,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String orderCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "newest") String sort) {

        OrderFilterParams params = new OrderFilterParams();
        params.setStatuses(statuses);
        params.setUserId(userId);
        params.setCustomerName(customerName);
        params.setOrderCode(orderCode);
        params.setSort(sort);
        if (startDate != null) params.setStartDate(LocalDate.parse(startDate));
        if (endDate != null) params.setEndDate(LocalDate.parse(endDate));

        return ResponseEntity.ok(orderService.findAllDetailsFiltered(params, page, size));
    }

    @GetMapping
    public ResponseEntity<Page<OrderListResponse>> findAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.findAll(pageable));
    }

    @GetMapping("/active")
    public ResponseEntity<Page<OrderListResponse>> findActive(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.findActive(pageable));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<OrderListResponse>> findHistory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.findHistory(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    // ADMIN only — enforced via @PreAuthorize inside OrderService
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderUpdateResponse> applyDiscount(@PathVariable UUID id,
                                                             @Valid @RequestBody OrderUpdateRequest request) {
        return ResponseEntity.ok(orderService.applyDiscount(id, request));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<OrderResponse> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.completeOrder(id));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> delete(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.delete(id));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<OrderItemResponse> addItem(@PathVariable UUID id,
                                                     @Valid @RequestBody OrderItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.addItem(id, request));
    }

    @PutMapping("/{id}/items/{itemId}")
    public ResponseEntity<OrderItemUpdateResponse> updateItem(@PathVariable UUID id,
                                                              @PathVariable UUID itemId,
                                                              @Valid @RequestBody OrderItemUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateItem(id, itemId, request));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<MessageResponse> removeItem(@PathVariable UUID id,
                                                      @PathVariable UUID itemId) {
        return ResponseEntity.ok(orderService.removeItem(id, itemId));
    }
}
