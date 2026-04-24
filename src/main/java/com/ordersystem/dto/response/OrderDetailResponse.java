package com.ordersystem.dto.response;

import com.ordersystem.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class OrderDetailResponse {

    private UUID id;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private BigDecimal total;
    private BigDecimal discount;
    private UserSummaryResponse user;
    private List<OrderItemResponse> items;
}
