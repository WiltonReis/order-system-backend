package com.ordersystem.controller;

import com.ordersystem.dto.request.OrderItemRequest;
import com.ordersystem.dto.request.OrderItemUpdateRequest;
import com.ordersystem.dto.request.OrderRequest;
import com.ordersystem.dto.request.OrderUpdateRequest;
import com.ordersystem.dto.response.*;
import com.ordersystem.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<OrderListResponse>> findAll() {
        return ResponseEntity.ok(orderService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    // ADMIN only — enforced via @PreAuthorize inside OrderService
    @PutMapping("/{id}")
    public ResponseEntity<OrderUpdateResponse> applyDiscount(@PathVariable UUID id,
                                                             @Valid @RequestBody OrderUpdateRequest request) {
        return ResponseEntity.ok(orderService.applyDiscount(id, request));
    }

    @DeleteMapping("/{id}")
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
