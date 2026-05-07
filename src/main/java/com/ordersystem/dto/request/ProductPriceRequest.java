package com.ordersystem.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ProductPriceRequest {

    @NotNull
    @Positive
    @Digits(integer = 8, fraction = 2)
    private BigDecimal price;
}
