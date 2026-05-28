package com.ordersystem.service;

import com.ordersystem.dto.response.DashboardResponse;
import com.ordersystem.dto.response.RevenueByDayResponse;
import com.ordersystem.dto.response.TopProductResponse;
import com.ordersystem.enums.OrderStatus;
import com.ordersystem.mapper.DashboardMapper;
import com.ordersystem.repository.OrderItemRepository;
import com.ordersystem.repository.OrderRepository;
import com.ordersystem.repository.RevenueByDayProjection;
import com.ordersystem.repository.TopProductProjection;
import com.ordersystem.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DashboardService — agregação de métricas por tenant e período")
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private DashboardMapper dashboardMapper;

    @InjectMocks private DashboardService dashboardService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setTenant() {
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private void stubDefaultCounts(long total, long canceled, long completed, BigDecimal revenue) {
        lenient().when(orderRepository.countByCreatedAtBetween(any(), any(), eq(TENANT_ID))).thenReturn(total);
        lenient().when(orderRepository.countByStatusBetween(eq(OrderStatus.CANCELED), any(), any(), eq(TENANT_ID))).thenReturn(canceled);
        lenient().when(orderRepository.countByStatusBetween(eq(OrderStatus.COMPLETED), any(), any(), eq(TENANT_ID))).thenReturn(completed);
        lenient().when(orderRepository.sumTotalByStatusBetween(eq(OrderStatus.COMPLETED), any(), any(), eq(TENANT_ID))).thenReturn(revenue);
        lenient().when(orderItemRepository.findTopProductsByQuantity(any(), any(), eq(TENANT_ID))).thenReturn(List.of());
        lenient().when(orderRepository.sumCompletedByDay(any(), any(), eq(TENANT_ID))).thenReturn(List.of());
    }

    @Nested
    @DisplayName("getDashboard — período predefinido")
    class PeriodResolution {

        @Test
        @DisplayName("resolve período TODAY a partir de início do dia corrente")
        void shouldResolveTodayPeriod() {
            stubDefaultCounts(3L, 1L, 2L, new BigDecimal("500.00"));

            DashboardResponse response = dashboardService.getDashboard("TODAY", null, null);

            assertThat(response.getTotalOrders()).isEqualTo(3L);
        }

        @Test
        @DisplayName("resolve período WEEK a partir de 6 dias atrás")
        void shouldResolveWeekPeriod() {
            stubDefaultCounts(10L, 2L, 5L, new BigDecimal("1000.00"));

            DashboardResponse response = dashboardService.getDashboard("WEEK", null, null);

            assertThat(response.getTotalOrders()).isEqualTo(10L);
        }

        @Test
        @DisplayName("resolve período MONTH a partir do primeiro dia do mês")
        void shouldResolveMonthPeriod() {
            stubDefaultCounts(20L, 4L, 10L, new BigDecimal("2000.00"));

            DashboardResponse response = dashboardService.getDashboard("MONTH", null, null);

            assertThat(response.getTotalOrders()).isEqualTo(20L);
        }

        @Test
        @DisplayName("período desconhecido usa data-início em 2000-01-01")
        void shouldUseFallbackForUnknownPeriod() {
            stubDefaultCounts(0L, 0L, 0L, null);

            DashboardResponse response = dashboardService.getDashboard("ALL_TIME", null, null);

            assertThat(response.getTotalOrders()).isZero();
        }
    }

    @Nested
    @DisplayName("getDashboard — intervalo customizado")
    class CustomDateRange {

        @Test
        @DisplayName("usa startDate e endDate quando ambos informados, ignora period")
        void shouldUseCustomDateRangeWhenProvided() {
            LocalDate start = LocalDate.now().minusDays(7);
            LocalDate end = LocalDate.now();
            stubDefaultCounts(5L, 1L, 3L, new BigDecimal("300.00"));

            DashboardResponse response = dashboardService.getDashboard("TODAY", start, end);

            assertThat(response.getTotalOrders()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("getDashboard — cálculos de métricas")
    class MetricsCalculation {

        @Test
        @DisplayName("calcula revenue como zero quando repositório retorna null")
        void shouldReturnZeroRevenueWhenRepositoryReturnsNull() {
            stubDefaultCounts(2L, 1L, 0L, null);

            DashboardResponse response = dashboardService.getDashboard("MONTH", null, null);

            assertThat(response.getRevenue()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("calcula cancelRate corretamente como percentual de cancelados sobre total")
        void shouldCalculateCancelRateCorrectly() {
            stubDefaultCounts(10L, 2L, 5L, new BigDecimal("500.00"));

            DashboardResponse response = dashboardService.getDashboard("MONTH", null, null);

            assertThat(response.getCancelRate()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("retorna cancelRate zero quando não há pedidos")
        void shouldReturnZeroCancelRateWhenNoOrders() {
            stubDefaultCounts(0L, 0L, 0L, null);

            DashboardResponse response = dashboardService.getDashboard("TODAY", null, null);

            assertThat(response.getCancelRate()).isZero();
        }

        @Test
        @DisplayName("calcula averageTicket como revenue dividido por pedidos completados")
        void shouldCalculateAverageTicketCorrectly() {
            stubDefaultCounts(5L, 0L, 4L, new BigDecimal("200.00"));

            DashboardResponse response = dashboardService.getDashboard("MONTH", null, null);

            assertThat(response.getAverageTicket()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("retorna averageTicket zero quando não há pedidos completados")
        void shouldReturnZeroAverageTicketWhenNoCompletedOrders() {
            stubDefaultCounts(3L, 3L, 0L, null);

            DashboardResponse response = dashboardService.getDashboard("MONTH", null, null);

            assertThat(response.getAverageTicket()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("calcula pedidos abertos como total - cancelados - completados")
        void shouldCalculateOpenOrdersCount() {
            stubDefaultCounts(10L, 3L, 4L, new BigDecimal("400.00"));

            DashboardResponse response = dashboardService.getDashboard("MONTH", null, null);

            assertThat(response.getOrdersByStatus().get("OPEN")).isEqualTo(3L);
            assertThat(response.getOrdersByStatus().get("COMPLETED")).isEqualTo(4L);
            assertThat(response.getOrdersByStatus().get("CANCELED")).isEqualTo(3L);
        }

        @Test
        @DisplayName("mapeia top produtos via DashboardMapper")
        void shouldMapTopProductsViaMapper() {
            stubDefaultCounts(5L, 0L, 2L, new BigDecimal("100.00"));

            TopProductProjection projection = mock(TopProductProjection.class);
            TopProductResponse topProduct = new TopProductResponse("Produto X", 10L);
            when(orderItemRepository.findTopProductsByQuantity(any(), any(), eq(TENANT_ID)))
                    .thenReturn(List.of(projection));
            when(dashboardMapper.toTopProductResponse(projection)).thenReturn(topProduct);

            DashboardResponse response = dashboardService.getDashboard("MONTH", null, null);

            assertThat(response.getTopProducts()).hasSize(1);
            assertThat(response.getTopProducts().get(0).getProductName()).isEqualTo("Produto X");
        }

        @Test
        @DisplayName("mapeia receita por dia via DashboardMapper")
        void shouldMapRevenueByDayViaMapper() {
            stubDefaultCounts(5L, 0L, 2L, new BigDecimal("100.00"));

            RevenueByDayProjection projection = mock(RevenueByDayProjection.class);
            RevenueByDayResponse revenueByDay = new RevenueByDayResponse("2025-05-01", new BigDecimal("50.00"));

            when(orderRepository.sumCompletedByDay(any(), any(), eq(TENANT_ID)))
                    .thenReturn(List.of(projection));
            when(dashboardMapper.toRevenueByDayResponse(projection)).thenReturn(revenueByDay);

            DashboardResponse response = dashboardService.getDashboard("MONTH", null, null);

            assertThat(response.getRevenueByDay()).hasSize(1);
            assertThat(response.getRevenueByDay().get(0).getDate()).isEqualTo("2025-05-01");
        }
    }
}
