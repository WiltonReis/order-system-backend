package com.ordersystem.service;

import com.ordersystem.dto.response.OrderDetailResponse;
import com.ordersystem.dto.response.OrderItemResponse;
import com.ordersystem.dto.response.UserSummaryResponse;
import com.ordersystem.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("OrderPdfService — geração de PDF por status, desconto e nome de cliente")
class OrderPdfServiceTest {

    private final OrderPdfService pdfService = new OrderPdfService();

    private OrderDetailResponse buildOrder(OrderStatus status,
                                           String customerName,
                                           BigDecimal discount,
                                           List<OrderItemResponse> items) {
        return new OrderDetailResponse(
                UUID.randomUUID(),
                "00000001",
                status,
                LocalDateTime.now(),
                items.stream()
                        .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .subtract(discount != null ? discount : BigDecimal.ZERO)
                        .max(BigDecimal.ZERO),
                discount != null ? discount : BigDecimal.ZERO,
                customerName,
                status == OrderStatus.COMPLETED ? LocalDateTime.now() : null,
                status == OrderStatus.CANCELED ? LocalDateTime.now() : null,
                status == OrderStatus.COMPLETED ? "operator" : null,
                status == OrderStatus.CANCELED ? "operator" : null,
                new UserSummaryResponse(UUID.randomUUID(), "Operador"),
                items
        );
    }

    private OrderItemResponse buildItem(String name, int quantity, BigDecimal price) {
        return new OrderItemResponse(UUID.randomUUID(), UUID.randomUUID(), name, quantity, price);
    }

    @Nested
    @DisplayName("generate — contrato de saída")
    class GenerateOutput {

        @Test
        @DisplayName("gera byte array não-vazio para pedido aberto")
        void shouldGenerateNonEmptyBytesForOpenOrder() {
            OrderDetailResponse order = buildOrder(
                    OrderStatus.OPEN, "Cliente Teste", null,
                    List.of(buildItem("Produto A", 2, new BigDecimal("50.00"))));

            byte[] pdf = pdfService.generate(order);

            assertThat(pdf).isNotEmpty();
        }

        @Test
        @DisplayName("output inicia com magic bytes %PDF")
        void shouldStartWithPdfMagicBytes() {
            OrderDetailResponse order = buildOrder(
                    OrderStatus.OPEN, "Cliente Teste", null,
                    List.of(buildItem("Produto A", 1, new BigDecimal("10.00"))));

            byte[] pdf = pdfService.generate(order);

            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }
    }

    @Nested
    @DisplayName("generate — variações de status")
    class StatusVariations {

        @Test
        @DisplayName("gera PDF para pedido COMPLETED sem exceção")
        void shouldGeneratePdfForCompletedOrder() {
            OrderDetailResponse order = buildOrder(
                    OrderStatus.COMPLETED, "Cliente VIP", null,
                    List.of(buildItem("Item A", 3, new BigDecimal("100.00"))));

            assertThatNoException().isThrownBy(() -> pdfService.generate(order));
        }

        @Test
        @DisplayName("gera PDF para pedido CANCELED sem exceção")
        void shouldGeneratePdfForCanceledOrder() {
            OrderDetailResponse order = buildOrder(
                    OrderStatus.CANCELED, "Cliente X", null,
                    List.of(buildItem("Item B", 1, new BigDecimal("30.00"))));

            assertThatNoException().isThrownBy(() -> pdfService.generate(order));
        }
    }

    @Nested
    @DisplayName("generate — variações de conteúdo")
    class ContentVariations {

        @Test
        @DisplayName("gera PDF sem exceção quando nome do cliente é nulo")
        void shouldGeneratePdfWithNullCustomerName() {
            OrderDetailResponse order = buildOrder(
                    OrderStatus.OPEN, null, null,
                    List.of(buildItem("Produto", 1, new BigDecimal("20.00"))));

            assertThatNoException().isThrownBy(() -> pdfService.generate(order));
        }

        @Test
        @DisplayName("gera PDF sem exceção quando há desconto aplicado")
        void shouldGeneratePdfWithDiscount() {
            OrderDetailResponse order = buildOrder(
                    OrderStatus.OPEN, "Cliente com Desconto", new BigDecimal("15.00"),
                    List.of(buildItem("Produto Premium", 2, new BigDecimal("50.00"))));

            assertThatNoException().isThrownBy(() -> pdfService.generate(order));
        }

        @Test
        @DisplayName("gera PDF sem exceção para pedido com múltiplos itens")
        void shouldGeneratePdfWithMultipleItems() {
            List<OrderItemResponse> items = List.of(
                    buildItem("Item 1", 1, new BigDecimal("10.00")),
                    buildItem("Item 2", 3, new BigDecimal("25.00")),
                    buildItem("Item 3", 2, new BigDecimal("40.00"))
            );
            OrderDetailResponse order = buildOrder(OrderStatus.OPEN, "Cliente Multi", null, items);

            byte[] pdf = pdfService.generate(order);

            assertThat(pdf).isNotEmpty();
        }

        @Test
        @DisplayName("gera PDF sem exceção para pedido sem itens")
        void shouldGeneratePdfForOrderWithNoItems() {
            OrderDetailResponse order = buildOrder(OrderStatus.OPEN, "Cliente", null, List.of());

            assertThatNoException().isThrownBy(() -> pdfService.generate(order));
        }
    }
}
