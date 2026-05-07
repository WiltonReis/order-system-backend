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
                throw new BusinessException("Order cannot have duplicate products");
            }
        }
    }

    public void validateDiscountNotExceedsSubtotal(BigDecimal discount, BigDecimal subtotal) {
        if (discount.compareTo(subtotal) >= 0) {
            throw new BusinessException("Discount cannot be equal to or greater than order subtotal");
        }
    }

    public void validateStatusTransition(OrderStatus current, OrderStatus target) {
        if (current == target) {
            throw new BusinessException("Order is already " + target.name().toLowerCase());
        }
        if (current != OrderStatus.OPEN) {
            throw new BusinessException("Only OPEN orders can be transitioned");
        }
    }

    public void validateOrderIsOpen(OrderStatus status) {
        if (status != OrderStatus.OPEN) {
            throw new BusinessException("Operation can only be applied to OPEN orders");
        }
    }
}
