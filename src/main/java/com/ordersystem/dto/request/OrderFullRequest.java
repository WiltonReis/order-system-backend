package com.ordersystem.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class OrderFullRequest {

    @Size(max = 150)
    private String customerName;

    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;

    @PositiveOrZero
    private BigDecimal discount;
}
