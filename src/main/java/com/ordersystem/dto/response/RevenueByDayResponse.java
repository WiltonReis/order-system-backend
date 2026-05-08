package com.ordersystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class RevenueByDayResponse {
    private String date;
    private BigDecimal revenue;
}
