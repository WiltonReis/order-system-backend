package com.ordersystem.dto.request;

import com.ordersystem.enums.OrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class OrderFilterParams {
    private List<OrderStatus> statuses;
    private UUID userId;
    private String customerName;
    private String orderCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private String sort;
}
