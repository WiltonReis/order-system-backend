package com.ordersystem.dto.response;

import com.ordersystem.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class OrderListResponse {

    private UUID id;
    private String orderCode;
    private OrderStatus status;
    private BigDecimal total;
    private LocalDateTime createdAt;
    private String customerName;
    private LocalDateTime completedAt;
    private LocalDateTime canceledAt;
    private String completedByName;
    private String canceledByName;
    private UserSummaryResponse user;
}
