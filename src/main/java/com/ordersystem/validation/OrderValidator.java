package com.ordersystem.validation;

import com.ordersystem.dto.request.OrderItemRequest;
import com.ordersystem.enums.OrderStatus;
import com.ordersystem.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class OrderValidator {

    public void validateNoDuplicateItems(List<OrderItemRequest> items) {
        Set<UUID> seen = new HashSet<>();
        for (OrderItemRequest item : items) {
            if (!seen.add(item.getProductId())) {
                throw new BusinessException("O pedido não pode ter produtos duplicados");
            }
        }
    }

    public void validateDiscountNotExceedsSubtotal(BigDecimal discount, BigDecimal subtotal) {
        if (discount.compareTo(subtotal) >= 0) {
            throw new BusinessException("O desconto não pode ser maior ou igual ao subtotal do pedido");
        }
    }

    public void validateStatusTransition(OrderStatus current, OrderStatus target) {
        if (current == target) {
            String label = target == OrderStatus.COMPLETED ? "finalizado" : "cancelado";
            throw new BusinessException("O pedido já foi " + label);
        }
        if (current != OrderStatus.OPEN) {
            throw new BusinessException("Somente pedidos abertos podem ter o status alterado");
        }
    }

    public void validateOrderIsOpen(OrderStatus status) {
        if (status != OrderStatus.OPEN) {
            throw new BusinessException("Esta operação só pode ser aplicada a pedidos abertos");
        }
    }
}
