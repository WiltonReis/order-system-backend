package com.ordersystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_saas", indexes = {
        @Index(name = "idx_customer_saas_cpf_cnpj", columnList = "cpf_cnpj", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class CustomerSaas {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "cpf_cnpj", nullable = false, unique = true, length = 20)
    private String cpfCnpj;

    @Column(name = "contact_email", nullable = false, length = 200)
    private String contactEmail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
