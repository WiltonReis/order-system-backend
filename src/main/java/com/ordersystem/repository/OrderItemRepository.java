package com.ordersystem.repository;

import com.ordersystem.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    Optional<OrderItem> findByIdAndOrderId(UUID id, UUID orderId);

    @Query(value = """
            SELECT p.name AS productName, SUM(oi.quantity) AS totalQuantity
            FROM order_items oi
            JOIN products p ON oi.product_id = p.id
            JOIN orders o ON oi.order_id = o.id
            WHERE o.status = 'COMPLETED' AND o.created_at >= :from
            GROUP BY p.id, p.name
            ORDER BY SUM(oi.quantity) DESC
            LIMIT 5
            """, nativeQuery = true)
    List<TopProductProjection> findTopProductsByQuantity(@Param("from") LocalDateTime from);
}
