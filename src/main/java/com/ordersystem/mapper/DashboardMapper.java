package com.ordersystem.mapper;

import com.ordersystem.dto.response.RevenueByDayResponse;
import com.ordersystem.dto.response.TopProductResponse;
import com.ordersystem.repository.RevenueByDayProjection;
import com.ordersystem.repository.TopProductProjection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DashboardMapper {

    TopProductResponse toTopProductResponse(TopProductProjection projection);

    @Mapping(target = "revenue", expression = "java(projection.getRevenue() != null ? projection.getRevenue() : java.math.BigDecimal.ZERO)")
    RevenueByDayResponse toRevenueByDayResponse(RevenueByDayProjection projection);
}
