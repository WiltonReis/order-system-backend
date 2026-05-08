package com.ordersystem.repository;

import java.math.BigDecimal;

public interface RevenueByDayProjection {
    String getDate();
    BigDecimal getRevenue();
}
