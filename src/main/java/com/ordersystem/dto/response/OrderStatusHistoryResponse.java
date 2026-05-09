package com.ordersystem.dto.response;

import com.ordersystem.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderStatusHistoryResponse(
        UUID id,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        LocalDateTime changedAt,
        String changedBy
) {
}
