package com.ordersystem.repository;

import com.ordersystem.entity.CustomerSaas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerSaasRepository extends JpaRepository<CustomerSaas, UUID> {

    boolean existsByCpfCnpj(String cpfCnpj);

    Optional<CustomerSaas> findByCpfCnpj(String cpfCnpj);
}
