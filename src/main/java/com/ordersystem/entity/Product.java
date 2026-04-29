package com.ordersystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_customer_saas_id", columnList = "customer_saas_id")
})
@Filter(name = "tenantFilter", condition = "customer_saas_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column
    private String imageUrl;

    @Column
    private String createdByUsername;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_saas_id", nullable = false)
    private CustomerSaas customerSaas;
}
