package com.ordersystem.service;

import com.ordersystem.dto.response.DashboardResponse;
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

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String period) {
        LocalDateTime from = resolveFrom(period);

        long totalOrders = orderRepository.countByCreatedAtFrom(from);
        long canceledOrders = orderRepository.countByStatusFrom(OrderStatus.CANCELED, from);
        long completedOrders = orderRepository.countByStatusFrom(OrderStatus.COMPLETED, from);

        BigDecimal revenue = orderRepository.sumTotalByStatusFrom(OrderStatus.COMPLETED, from);
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

        List<TopProductResponse> topProducts = orderItemRepository.findTopProductsByQuantity(from, TenantContext.getOrThrow())
                .stream()
                .map(p -> new TopProductResponse(p.getProductName(), p.getTotalQuantity()))
                .toList();

        return new DashboardResponse(totalOrders, revenue, cancelRate, averageTicket, topProducts);
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
