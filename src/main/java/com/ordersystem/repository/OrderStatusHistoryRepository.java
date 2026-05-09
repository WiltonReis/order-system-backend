package com.ordersystem.repository;

import com.ordersystem.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {

    @Query("SELECT h FROM OrderStatusHistory h WHERE h.order.id = :orderId ORDER BY h.changedAt DESC")
    List<OrderStatusHistory> findByOrderIdOrderByChangedAtDesc(@Param("orderId") UUID orderId);
}
