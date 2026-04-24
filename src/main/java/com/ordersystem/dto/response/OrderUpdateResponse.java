package com.ordersystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class OrderUpdateResponse {

    private UUID id;
    private BigDecimal total;
    private BigDecimal discount;
}
