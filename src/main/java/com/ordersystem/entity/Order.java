package com.ordersystem.entity;

import com.ordersystem.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders",
        uniqueConstraints = {
                // MT-21: order_code único por tenant (não globalmente)
                @UniqueConstraint(name = "uk_orders_customer_saas_order_code", columnNames = {"customer_saas_id", "order_code"})
        },
        indexes = {
                @Index(name = "idx_orders_status", columnList = "status"),
                @Index(name = "idx_orders_user_id", columnList = "user_id"),
                @Index(name = "idx_orders_created_at", columnList = "createdAt"),
                @Index(name = "idx_orders_customer_saas_id", columnList = "customer_saas_id"),
                @Index(name = "idx_orders_customer_saas_id_status", columnList = "customer_saas_id, status")
        })
@Filter(name = "tenantFilter", condition = "customer_saas_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime completedAt;

    @Column
    private LocalDateTime canceledAt;

    @Column(length = 150)
    private String customerName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(length = 8)
    private String orderCode;

    @Column
    private String completedByName;

    @Column
    private String canceledByName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_saas_id", nullable = false)
    private CustomerSaas customerSaas;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public void recalculateTotal() {
        BigDecimal subtotal = items.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.discount = discount.min(subtotal);
        this.total = subtotal.subtract(discount).max(BigDecimal.ZERO);
    }
}
