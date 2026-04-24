package com.ordersystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class OrderItemUpdateResponse {

    private UUID id;
    private Integer quantity;
    private BigDecimal price;
}
