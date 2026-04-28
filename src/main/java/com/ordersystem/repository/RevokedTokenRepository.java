package com.ordersystem.repository;

import com.ordersystem.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {

    boolean existsByJti(String jti);

    void deleteAllByExpiresAtBefore(LocalDateTime now);
}
