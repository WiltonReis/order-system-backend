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
    private OrderStatus status;
    private BigDecimal total;
    private LocalDateTime createdAt;
    private UserSummaryResponse user;
}
