package com.ordersystem.mapper;

import com.ordersystem.dto.response.*;
import com.ordersystem.entity.Order;
import com.ordersystem.entity.OrderItem;
import com.ordersystem.entity.OrderStatusHistory;
import com.ordersystem.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderResponse toOrderResponse(Order order);

    OrderListResponse toOrderListResponse(Order order);

    OrderDetailResponse toOrderDetailResponse(Order order);

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    OrderItemResponse toOrderItemResponse(OrderItem item);

    OrderItemUpdateResponse toOrderItemUpdateResponse(OrderItem item);

    OrderUpdateResponse toOrderUpdateResponse(Order order);

    OrderStatusHistoryResponse toOrderStatusHistoryResponse(OrderStatusHistory history);

    UserSummaryResponse toUserSummaryResponse(User user);
}
