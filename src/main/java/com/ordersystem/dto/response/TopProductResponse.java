package com.ordersystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TopProductResponse {
    private String productName;
    private long totalQuantity;
}
