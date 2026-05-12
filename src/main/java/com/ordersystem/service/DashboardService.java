package com.ordersystem.service;

import com.ordersystem.dto.response.DashboardResponse;
import com.ordersystem.dto.response.RevenueByDayResponse;
import com.ordersystem.dto.response.TopProductResponse;
import com.ordersystem.enums.OrderStatus;
import com.ordersystem.repository.OrderItemRepository;
import com.ordersystem.repository.OrderRepository;
import com.ordersystem.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String period, LocalDate startDate, LocalDate endDate) {
        LocalDateTime from;
        LocalDateTime to;

        if (startDate != null && endDate != null) {
            from = startDate.atStartOfDay();
            to = endDate.atTime(23, 59, 59);
        } else {
            from = resolveFrom(period);
            to = LocalDateTime.now();
        }

        UUID tenantId = TenantContext.getOrThrow();

        long totalOrders = orderRepository.countByCreatedAtBetween(from, to, tenantId);
        long canceledOrders = orderRepository.countByStatusBetween(OrderStatus.CANCELED, from, to, tenantId);
        long completedOrders = orderRepository.countByStatusBetween(OrderStatus.COMPLETED, from, to, tenantId);
        long openOrders = totalOrders - canceledOrders - completedOrders;

        BigDecimal revenue = orderRepository.sumTotalByStatusBetween(OrderStatus.COMPLETED, from, to, tenantId);
        if (revenue == null) revenue = BigDecimal.ZERO;

        double cancelRate = totalOrders > 0
                ? BigDecimal.valueOf(canceledOrders)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                        .doubleValue()
                : 0.0;

        BigDecimal averageTicket = completedOrders > 0
                ? revenue.divide(BigDecimal.valueOf(completedOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<TopProductResponse> topProducts = orderItemRepository.findTopProductsByQuantity(from, to, tenantId)
                .stream()
                .map(p -> new TopProductResponse(p.getProductName(), p.getTotalQuantity()))
                .toList();

        Map<String, Long> ordersByStatus = Map.of(
                "OPEN", openOrders,
                "COMPLETED", completedOrders,
                "CANCELED", canceledOrders
        );

        List<RevenueByDayResponse> revenueByDay = orderRepository.sumCompletedByDay(from, to, tenantId)
                .stream()
                .map(r -> new RevenueByDayResponse(
                        r.getDate(),
                        r.getRevenue() != null ? r.getRevenue() : BigDecimal.ZERO
                ))
                .toList();

        return new DashboardResponse(totalOrders, revenue, cancelRate, averageTicket,
                topProducts, ordersByStatus, revenueByDay);
    }

    private LocalDateTime resolveFrom(String period) {
        LocalDate today = LocalDate.now();
        return switch (period.toUpperCase()) {
            case "TODAY" -> today.atStartOfDay();
            case "WEEK" -> today.minusDays(6).atStartOfDay();
            case "MONTH" -> today.withDayOfMonth(1).atStartOfDay();
            default -> LocalDateTime.of(2000, 1, 1, 0, 0);
        };
    }
}
