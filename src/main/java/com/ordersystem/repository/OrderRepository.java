package com.ordersystem.repository;

import com.ordersystem.entity.Order;
import com.ordersystem.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    boolean existsByOrderCode(String orderCode);

    @Query("SELECT o FROM Order o JOIN FETCH o.user")
    List<Order> findAllWithUsers();

    @Query("SELECT o FROM Order o JOIN FETCH o.user WHERE o.status = :status")
    List<Order> findAllWithUsersByStatus(@Param("status") OrderStatus status);

    @Query("SELECT o FROM Order o JOIN FETCH o.user WHERE o.status IN :statuses")
    List<Order> findAllWithUsersByStatusIn(@Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT o FROM Order o JOIN FETCH o.user LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") UUID id);
}
