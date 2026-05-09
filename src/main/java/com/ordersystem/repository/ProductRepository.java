package com.ordersystem.repository;

import com.ordersystem.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    // MT: findById bypasses Hibernate @Filter; JPQL query respects the tenant session filter
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdFiltered(@Param("id") UUID id);

    // Soft-delete: bypass do @SQLRestriction para fluxos de restore/auditoria.
    @Query(value = "SELECT * FROM products WHERE id = :id", nativeQuery = true)
    Optional<Product> findByIdIncludingDeleted(@Param("id") UUID id);
}
