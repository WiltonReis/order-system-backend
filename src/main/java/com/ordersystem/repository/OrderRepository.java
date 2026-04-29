package com.ordersystem.repository;

import com.ordersystem.entity.Order;
import com.ordersystem.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    // PERF-01: busca paginada de IDs (leve) + busca de detalhes por IDs (evita N+1 e HHH90003004)
    @Query(value = "SELECT o.id FROM Order o", countQuery = "SELECT COUNT(o) FROM Order o")
    Page<UUID> findAllIdsPaged(Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.user LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id IN :ids")
    List<Order> findAllWithDetailsByIds(@Param("ids") List<UUID> ids);

    // PERF-02: versões paginadas das listagens (JOIN FETCH em @ManyToOne é seguro com Pageable)
    @Query(value = "SELECT o FROM Order o JOIN FETCH o.user",
           countQuery = "SELECT COUNT(o) FROM Order o")
    Page<Order> findAllWithUsersPaged(Pageable pageable);

    @Query(value = "SELECT o FROM Order o JOIN FETCH o.user WHERE o.status = :status",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    Page<Order> findAllWithUsersByStatusPaged(@Param("status") OrderStatus status, Pageable pageable);

    @Query(value = "SELECT o FROM Order o JOIN FETCH o.user WHERE o.status IN :statuses",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.status IN :statuses")
    Page<Order> findAllWithUsersByStatusInPaged(@Param("statuses") List<OrderStatus> statuses, Pageable pageable);

    @Query("SELECT o FROM Order o JOIN FETCH o.user LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") UUID id);

    // PERF-03: próximo código de pedido via sequence PostgreSQL (atômico, sem loop de unicidade)
    @Query(value = "SELECT nextval('order_code_seq')", nativeQuery = true)
    Long getNextOrderCode();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :from")
    long countByCreatedAtFrom(@Param("from") LocalDateTime from);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.createdAt >= :from")
    long countByStatusFrom(@Param("status") OrderStatus status, @Param("from") LocalDateTime from);

    @Query("SELECT SUM(o.total) FROM Order o WHERE o.status = :status AND o.createdAt >= :from")
    BigDecimal sumTotalByStatusFrom(@Param("status") OrderStatus status, @Param("from") LocalDateTime from);
}
