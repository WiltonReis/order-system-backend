package com.ordersystem.mapper;

import com.ordersystem.dto.response.ProductResponse;
import com.ordersystem.entity.Product;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductResponse toProductResponse(Product product);
}
