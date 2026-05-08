package com.ordersystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class DashboardResponse {
    private long totalOrders;
    private BigDecimal revenue;
    private double cancelRate;
    private BigDecimal averageTicket;
    private List<TopProductResponse> topProducts;
    private Map<String, Long> ordersByStatus;
    private List<RevenueByDayResponse> revenueByDay;
}
